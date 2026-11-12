package com.asgardians;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import java.lang.ref.WeakReference;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.Skill;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.OkHttpClient;

/**
 * Asgardians Clan: records drops, pets and collection log slots and sends them to the clan website. Members are
 * recognised by RSN against the clan roster; the server hands back an install key, kept per API address and account.
 */
@Slf4j
@PluginDescriptor(
	name = "Asgardians Clan",
	description = "Records drops, collection log, deaths and PBs for the Asgardians clan",
	tags = {"asgardians", "clan", "drops", "bingo", "events", "pb"}
)
public class AsgardiansPlugin extends Plugin
{
	static final String CLAN_NAME = "Asgardians";

	private static final long FLUSH_SECONDS = 30;
	private static final long SETTINGS_MINUTES = 15;
	private static final long SCREENSHOT_DELAY_MILLIS = 500;
	/** After a failed registration, wait this long before trying again on the flush timer. */
	private static final long REGISTER_RETRY_MILLIS = 5 * 60_000;

	/** Worlds whose progress doesn't count, so nothing is recorded there. */
	private static final Set<WorldType> IGNORED_WORLDS = EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN,
		WorldType.BETA_WORLD, WorldType.TOURNAMENT_WORLD, WorldType.FRESH_START_WORLD, WorldType.QUEST_SPEEDRUNNING,
		WorldType.NOSAVE_MODE, WorldType.LAST_MAN_STANDING);
	/** Raid chests (loot tracker event names) that get the party size. */
	private static final String[] RAID_EVENTS = {"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut", "Fractured Archive"};
	/** RuneLite's Loot Tracker name for the Fractured Archive's chest. */
	static final String FRACTURED_ARCHIVE = "Fractured Archive";
	/** Group bosses whose NPC loot gets the party size. */
	private static final Set<String> GROUP_NPCS = Set.of("Nex", "The Nightmare");

	/** Game tick of the last pickpocket, whose loot the game also reports as NPC loot. */
	private int pickpocketTick = -1;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AsgardiansConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private net.runelite.client.chat.ChatCommandManager chatCommandManager;

	/** Clan profiles looked up for !asg commands, by lowercase RSN, kept for a few minutes. */
	private final Map<String, Object[]> commandProfiles = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long COMMAND_PROFILE_TTL = 5 * 60_000L;

	@Inject
	private ItemManager itemManager;

	@Inject
	private net.runelite.client.game.SpriteManager spriteManager;

	@Inject
	private net.runelite.client.game.SkillIconManager skillIconManager;

	/** RuneLite's hiscore bosses, by name, for the Stats tab's PB icons. */
	private static final Map<String, net.runelite.client.hiscore.HiscoreSkill> HISCORE_BOSSES = hiscoreBosses();

	private static Map<String, net.runelite.client.hiscore.HiscoreSkill> hiscoreBosses()
	{
		Map<String, net.runelite.client.hiscore.HiscoreSkill> byName = new java.util.LinkedHashMap<>();
		for (net.runelite.client.hiscore.HiscoreSkill skill : net.runelite.client.hiscore.HiscoreSkill.values())
		{
			if (skill.getType() == net.runelite.client.hiscore.HiscoreSkillType.BOSS && skill.getName() != null)
			{
				byName.put(skill.getName(), skill);
			}
		}
		return byName;
	}

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ImageCapture imageCapture;

	private AsgardiansPanel panel;
	private NavigationButton navButton;
	private ApiClient api;
	private final RecordQueue queue = new RecordQueue();
	private DropRecorder recorder;
	private volatile ServerSettings settings = new ServerSettings();
	/** Events and News: how often to reload, faster while a board you're on is live. */
	private static final long CLAN_REFRESH_MILLIS = 5 * 60_000;
	private static final long BOARD_REFRESH_MILLIS = 2 * 60_000;
	private static final java.time.Duration REMINDER_WINDOW = java.time.Duration.ofMinutes(15);
	/** How long after sending a watched item to reload the boards, giving the website time to match it. */
	private static final long WATCHED_REFRESH_SECONDS = 20;
	/** How long an instant "Drop submitted" message stands in for the website's own "submitted" message. */
	private static final long ANNOUNCED_MILLIS = TimeUnit.MINUTES.toMillis(10);
	/** Tiles the member was told about when the drop happened, by tile key, with the time. */
	private final Map<String, Long> announcedDrops = new java.util.concurrent.ConcurrentHashMap<>();
	/** Drops the member has been told to pick a tile for, so each is mentioned once. */
	private final Set<String> choicesAnnounced = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private net.runelite.client.party.PartyService partyService;

	// ---- group finder ----
	private static final long GROUP_IN_GROUP_MILLIS = TimeUnit.SECONDS.toMillis(10);
	private static final long GROUP_ALERT_MILLIS = TimeUnit.SECONDS.toMillis(20);
	private static final long GROUP_IDLE_MILLIS = TimeUnit.MINUTES.toMillis(2);
	private static final long GROUP_UNSUPPORTED_MILLIS = TimeUnit.MINUTES.toMillis(5);
	private static final long GROUP_ACTIVITIES_MILLIS = TimeUnit.MINUTES.toMillis(30);
	private final Object groupLock = new Object();
	private volatile ClanData.GroupList groups;
	private volatile List<ClanData.GroupActivity> groupActivities = List.of();
	private volatile boolean groupsUnsupported;
	private volatile boolean groupBusy;
	private volatile java.time.Instant groupsFetchedAt;
	/** A group the member just left or closed, so the chat line says so rather than "you were removed". */
	private volatile String leftGroupOnPurpose;
	/** The group whose RuneLite party the plugin put the member in, and its passphrase. */
	private volatile String partyGroup;
	private volatile String partyPassphrase;
	private volatile long lastGroupsPoll;
	private volatile long lastActivitiesFetch;
	private final AtomicBoolean groupsPolling = new AtomicBoolean();
	private ScheduledFuture<?> groupTask;

	private ClanFetcher clanFetcher;
	private CodewordOverlay codewordOverlay;
	private GroupAlertOverlay groupAlertOverlay;
	private volatile ClanView clanView = ClanView.empty();
	private volatile long lastClanFetch;
	/** The member's clan profile for the Connection card. */
	private volatile ClanData.Me me;
	/** Leaderboards by period (ClanTabs.WEEK, MONTH, YEAR, ALL). */
	private final Map<String, List<ClanData.Leaderboard>> leaderboards = new java.util.concurrent.ConcurrentHashMap<>();
	/** Chat icons reserved for team badges, one per team slot, and what each currently shows. */
	private final int[] badgeIcons = new int[TeamBadges.MAX_TEAMS];
	private final String[] badgePainted = new String[TeamBadges.MAX_TEAMS];
	private boolean badgesReserved;
	/** Players in the live event, by {@link TeamBadges#key}, to their team's badge. */
	private volatile Map<String, TeamBadges.Badge> badgePlayers = Map.of();
	/** Set until the first load after logging in, which lists any unread announcements in chat. */
	private volatile boolean loginNewsPending = true;
	private final AtomicBoolean fetchingClan = new AtomicBoolean();
	/** Event IDs already reminded about this session. */
	private final Set<String> reminded = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private ScheduledFuture<?> clanTask;
	/** The Stats tab's lists; null until loaded. */
	private volatile ClanData.StatsFeed stats;
	/** The website doesn't have clan stats yet. */
	private volatile boolean statsUnsupported;
	private volatile String statsError;
	private volatile java.time.Instant statsFetchedAt;
	private volatile long lastStatsFetch;
	private final AtomicBoolean fetchingStats = new AtomicBoolean();
	/** Handegg throws seen nearby, to spot ones the player is part of. Client thread. */
	private final EggTracker eggs = new EggTracker();
	/** Where the clan's egg feed is up to; null until the first fetch after logging in. */
	private volatile String eggCursor;
	/** The website doesn't have the egg feed yet. */
	private volatile boolean eggsUnsupported;
	private final AtomicBoolean fetchingEggs = new AtomicBoolean();
	/** Sign-up lists loaded so far, events whose list is open, and events with a request in flight. */
	private final Map<String, ClanData.SignupList> signupLists = new java.util.concurrent.ConcurrentHashMap<>();
	private final Set<String> signupsOpen = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final Set<String> signupsBusy = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile SignupForm.Prefill signupPrefill = new SignupForm.Prefill(java.time.ZoneId.systemDefault().getId(), null);

	private final ChatPrivacy chatPrivacy = new ChatPrivacy();
	private final ClogCapture clogCapture = new ClogCapture();
	/** The last collection log read this session; an identical read isn't sent again. */
	private Map<Integer, Integer> lastClogCapture;
	private long lastAutoFetchMillis;
	/** A captured collection log waiting to be sent. Replaced by a newer capture; cleared once the server has it. */
	private volatile PendingClog pendingClog;
	private final AtomicBoolean syncingClog = new AtomicBoolean();
	private volatile String clogStatus;
	private volatile boolean clogStatusIsError;

	@lombok.Value
	private static class PendingClog
	{
		List<ClogCapture.Item> items;
		int obtained;
		int total;
	}

	/** The last thing the player attacked, for working out a killer. Weak so a despawned NPC can be collected. */
	private WeakReference<Actor> lastTarget = new WeakReference<>(null);
	private PbTracker pbs;
	private final AtomicBoolean syncingPbs = new AtomicBoolean();
	/** Account hashes whose PBs have been fully synced since the client started. */
	private final Set<Long> pbSyncedThisSession = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile String pbStatus;
	private volatile boolean pbStatusIsError;
	/** Adventure Log owner and scroll tracking, mirroring how RuneLite's Chat Commands plugin reads the counters page. */
	private boolean advLogLoaded;
	private boolean scrollLoaded;
	private String advLogOwner;
	private ScheduledFuture<?> flushTask;
	private ScheduledFuture<?> settingsTask;
	private final AtomicBoolean uploading = new AtomicBoolean();
	private final AtomicBoolean registering = new AtomicBoolean();
	private volatile long lastRegisterFailure;

	/** The logged-in account, captured on the client thread for the upload thread. */
	private volatile ApiClient.AccountInfo account;
	/** Whether recording is allowed right now (consent, clan, world), captured on the client thread. */
	private volatile boolean sharing;
	private ScheduledFuture<?> pbTask;
	private volatile String status;
	private volatile boolean statusIsError;

	@Inject
	private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager worldMapPointManager;

	private ClanMap clanMap;
	/** Clanmates sharing their location, from the website. */
	private volatile List<ClanMap.Pin> mapPins = List.of();
	/** Where the member is, when they share it and aren't in an instance; set on the client thread. */
	private volatile WorldPoint myLocation;
	private volatile WorldPoint sentLocation;
	/** Send a new position at most this often. */
	private static final long LOCATION_RESEND_MILLIS = 15_000;
	/** Reload clanmates' positions this often while the world map is open. */
	private static final long MAP_REFRESH_MILLIS = 10_000;

	@Provides
	AsgardiansConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AsgardiansConfig.class);
	}

	@Override
	protected void startUp()
	{
		api = new ApiClient(okHttpClient, gson);
		chatCommandManager.registerCommand(AsgCommands.PREFIX, this::asgCommand);
		recorder = new DropRecorder(new DropRecorder.Items()
		{
			@Override
			public String name(int itemId)
			{
				return itemManager.getItemComposition(itemId).getMembersName();
			}

			@Override
			public long price(int itemId)
			{
				return itemManager.getItemPrice(itemId);
			}

			@Override
			public Integer idForName(String name)
			{
				for (ItemPrice price : itemManager.search(name))
				{
					if (price.getName().equalsIgnoreCase(name))
					{
						return price.getId();
					}
				}
				return null;
			}
		});

		pbs = new PbTracker(new PbTracker.Store()
		{
			@Override
			public List<String> keys()
			{
				return configManager.getRSProfileConfigurationKeys(PersonalBest.CONFIG_GROUP, configManager.getRSProfileKey(), "");
			}

			@Override
			public String value(String key)
			{
				return configManager.getRSProfileConfiguration(PersonalBest.CONFIG_GROUP, key);
			}

			@Override
			public Integer killCount(String boss)
			{
				return configManager.getRSProfileConfiguration(PersonalBest.KC_CONFIG_GROUP, boss, Integer.class);
			}
		});

		clanFetcher = new ClanFetcher(api);
		// Chat icons can't be given back, so turning the plugin off and on again reuses the same ones.
		// registerChatIcon works from any thread; reserveChatIcon must run on the client thread.
		if (!badgesReserved)
		{
			for (int i = 0; i < badgeIcons.length; i++)
			{
				badgeIcons[i] = chatIconManager.registerChatIcon(new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB));
			}
			badgesReserved = true;
		}
		codewordOverlay = new CodewordOverlay(this, config, () -> clanView);
		overlayManager.add(codewordOverlay);
		groupAlertOverlay = new GroupAlertOverlay(this, () -> config.groupOverlay(), System::currentTimeMillis);
		overlayManager.add(groupAlertOverlay);

		panel = new AsgardiansPanel(new AsgardiansPanel.Settings()
		{
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(AsgardiansConfig.GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				configManager.setConfiguration(AsgardiansConfig.GROUP, key, value);
			}
		}, () -> executor.execute(this::syncPbsNow),
			() -> executor.execute(() -> refreshClan(true)),
			() -> clientThread.invokeLater(this::markNewsRead),
			new ClanTabs.Actions()
			{
				@Override
				public void signUp(ClanData.Event event)
				{
					AsgardiansPlugin.this.signUp(event);
				}

				@Override
				public void withdraw(ClanData.Event event)
				{
					AsgardiansPlugin.this.withdraw(event);
				}

				@Override
				public void toggleSignups(ClanData.Event event)
				{
					AsgardiansPlugin.this.toggleSignups(event);
				}

				@Override
				public void chooseTile(ClanData.Event event, ClanData.Drop drop, ClanData.TileOption tile)
				{
					AsgardiansPlugin.this.chooseTile(event, drop, tile);
				}
			}, groupActions(), statsActions());
		PanelParts.setItemIcons((itemId, done) ->
		{
			net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(itemId);
			img.onLoaded(() -> SwingUtilities.invokeLater(() -> done.accept(img)));
			SwingUtilities.invokeLater(() -> done.accept(img));
		});
		panel.loadTilePictures(new AsgardiansPanel.GamePictures()
		{
			@Override
			public void item(int itemId, int quantity, java.util.function.Consumer<BufferedImage> done)
			{
				net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(itemId, quantity, false);
				img.onLoaded(() -> SwingUtilities.invokeLater(() -> done.accept(img)));
				SwingUtilities.invokeLater(() -> done.accept(img));
			}

			@Override
			public void sprite(int spriteId, java.util.function.Consumer<BufferedImage> done)
			{
				spriteManager.getSpriteAsync(spriteId, 0, sprite -> SwingUtilities.invokeLater(() -> done.accept(sprite)));
			}
		});
		PanelParts.setEventIcons((event, done) ->
		{
			switch (ClanView.group(event))
			{
				case "bingo":
					java.awt.image.BufferedImage grid = AsgardiansPanel.resource("tile_bingo.png");
					if (grid != null)
					{
						done.accept(grid);
					}
					break;
				case "botw":
					String boss = bossIn(event.getName());
					if (boss != null)
					{
						spriteManager.getSpriteAsync(HISCORE_BOSSES.get(boss).getSpriteId(), 0,
							sprite -> SwingUtilities.invokeLater(() -> done.accept(sprite)));
					}
					break;
				case "sotw":
					Skill skill = skillIn(event.getName());
					if (skill != null)
					{
						done.accept(skillIconManager.getSkillImage(skill, false));
					}
					break;
				default:
					net.runelite.client.util.AsyncBufferedImage hat = itemManager.getImage(net.runelite.api.gameval.ItemID.RED_PARTYHAT);
					hat.onLoaded(() -> SwingUtilities.invokeLater(() -> done.accept(hat)));
					done.accept(hat);
			}
		});
		PanelParts.setSkillIcons((label, name) ->
		{
			for (Skill skill : Skill.values())
			{
				if (skill.getName().equalsIgnoreCase(name))
				{
					label.setIcon(new javax.swing.ImageIcon(skillIconManager.getSkillImage(skill, false)));
				}
			}
		});
		panel.setProfileLoader(rsn -> executor.execute(() -> fetchProfile(rsn)));
		panel.setMembersRefresh(() -> executor.execute(this::fetchMemberActivity));
		panel.setStaffActions(rsn -> executor.execute(() -> staffNotes(rsn, null, null)), new StaffTabs.Actions()
		{
			@Override
			public void add(String rsn, String text)
			{
				executor.execute(() -> staffNotes(rsn, text, null));
			}

			@Override
			public void delete(String rsn, ClanData.StaffNote note)
			{
				executor.execute(() -> staffNotes(rsn, null, note.getId()));
			}

			@Override
			public void setMotd(String text)
			{
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("text", text);
				executor.execute(() -> staffPost("motd", body));
			}

			@Override
			public void postNews(String title, String text)
			{
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("title", title);
				body.addProperty("body", text);
				executor.execute(() -> staffPost("news", body));
			}
		});
		clanMap = new ClanMap(worldMapPointManager);
		navButton = NavigationButton.builder()
			.tooltip("Asgardians Clan")
			.icon(AsgIcon.create(16))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		flushTask = executor.scheduleWithFixedDelay(this::flush, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
		settingsTask = executor.scheduleWithFixedDelay(this::fetchSettings, 0, SETTINGS_MINUTES, TimeUnit.MINUTES);
		pbTask = executor.scheduleWithFixedDelay(this::pbSyncTick, 5, 2, TimeUnit.SECONDS);
		clanTask = executor.scheduleWithFixedDelay(this::clanTick, 5, 30, TimeUnit.SECONDS);
		groupTask = executor.scheduleWithFixedDelay(this::groupTick, 6, 5, TimeUnit.SECONDS);
		clientThread.invokeLater(this::onAccountState);
		log.info("Asgardians Clan started, API address {}", apiAddress());
	}

	@Override
	protected void shutDown()
	{
		chatCommandManager.unregisterCommand(AsgCommands.PREFIX);
		if (clanMap != null)
		{
			ClanMap map = clanMap;
			clientThread.invoke(map::clear);
			clanMap = null;
		}
		commandProfiles.clear();
		// Null checks: RuneLite also calls this after a start that failed part way.
		for (java.util.concurrent.Future<?> task : new java.util.concurrent.Future<?>[]{flushTask, settingsTask, pbTask, clanTask, groupTask})
		{
			if (task != null)
			{
				task.cancel(false);
			}
		}
		if (groupAlertOverlay != null)
		{
			overlayManager.remove(groupAlertOverlay);
			groupAlertOverlay = null;
		}
		if (codewordOverlay != null)
		{
			overlayManager.remove(codewordOverlay);
		}
		executor.execute(this::flush);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = null;
		panel = null;
		PanelParts.setItemIcons(null);
		PanelParts.setEventIcons(null);
		PanelParts.setSkillIcons(null);
	}

	/** The address every API call should use. */
	String apiAddress()
	{
		return ApiAddress.resolve(config.apiAddress());
	}

	// ---- game events (client thread) ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			onAccountState();
			// After a hop, a different set of clanmates is on the member's world.
			drawClanMap();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			myLocation = null;
			nerdLogged = false;
			motdShown = null;
			if (clanMap != null)
			{
				clanMap.clear();
			}
			// Anything waiting goes now; XP and activity start again for whoever logs in next.
			sendPendingKcs(true);
			xpSentTick = -1;
			xpSentTotal = -1;
			lastXp.clear();
			myActivity = null;
			loginNewsPending = true;
			executor.execute(this::flush);
			refreshPanel();
		}
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		onAccountState();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PersonalBest.CONFIG_GROUP.equals(event.getGroup()))
		{
			onPersonalBestChanged(event);
			return;
		}
		if ("runelite".equals(event.getGroup()) && "chatcommandsplugin".equals(event.getKey()))
		{
			refreshPanel();
			return;
		}
		if (!AsgardiansConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("apiAddress".equals(event.getKey()))
		{
			status = null;
			executor.execute(this::fetchSettings);
		}
		if ("showClanMap".equals(event.getKey()) || "mapOnlyMyWorld".equals(event.getKey()))
		{
			clientThread.invokeLater(this::drawClanMap);
		}
		if ("shareActivity".equals(event.getKey()) || "shareLocation".equals(event.getKey()))
		{
			executor.execute(this::sendActivity);
			refreshClanPanel();
		}
		if ("groupAlerts".equals(event.getKey()) || "groupOverlay".equals(event.getKey()))
		{
			// The Group alerts card explains what these settings turn off.
			refreshGroupsPanel();
		}
		clientThread.invokeLater(this::onAccountState);
	}

	/**
	 * The one source of NPC loot. The game reports each kill's drops itself, which is what RuneLite's Loot Tracker uses.
	 * NpcLootReceived reports the same kill again from the items on the floor, so it mustn't be recorded as well.
	 */
	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (pickpocketTick == client.getTickCount())
		{
			// Also arrives through LootReceived as a pickpocket.
			return;
		}
		NPCComposition npc = event.getComposition();
		String name = Text.removeTags(npc.getName());
		recordLoot(DropRecorder.npcLootSource(name), npc.getId(), event.getItems(), GROUP_NPCS.contains(name));
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER)
		{
			// NPC kills are recorded from ServerNpcLoot, and player kills aren't recorded.
			return;
		}
		boolean raid = false;
		for (String prefix : RAID_EVENTS)
		{
			raid |= event.getName().startsWith(prefix);
		}
		recordLoot(event.getName(), null, event.getItems(), raid);
		if (isRecording() && config.eventMessages() && isCabbage(event.getName(), event.getItems()))
		{
			// The chest's consolation prize for a rough run. The website counts them for the Cabbage high score.
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", new net.runelite.client.chat.ChatMessageBuilder()
				.append(net.runelite.client.chat.ChatColorType.HIGHLIGHT).append("Asgardians: ")
				.append(net.runelite.client.chat.ChatColorType.NORMAL)
				.append("A cabbage! That's going on the clan's Cabbage high score.").build(), null);
		}
	}

	/** Loot that is only the Fractured Archive's cabbage. */
	static boolean isCabbage(String source, Collection<ItemStack> items)
	{
		if (!FRACTURED_ARCHIVE.equals(source) || items == null || items.isEmpty())
		{
			return false;
		}
		for (ItemStack stack : items)
		{
			if (stack.getId() != net.runelite.api.gameval.ItemID.CABBAGE)
			{
				return false;
			}
		}
		return true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType messageType = event.getType();
		if (messageType == ChatMessageType.PRIVATECHAT || messageType == ChatMessageType.PRIVATECHATOUT
			|| messageType == ChatMessageType.MODPRIVATECHAT)
		{
			// Only remembered so the line can be hidden from screenshots; never recorded or sent.
			chatPrivacy.privateMessage(Text.removeTags(event.getName()), System.currentTimeMillis());
			return;
		}
		if (messageType == ChatMessageType.CLAN_CHAT && sharing && config.teamBadges())
		{
			addTeamBadge(event);
		}
		if (!isRecording())
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		long now = System.currentTimeMillis();
		ChatMessageType type = event.getType();
		if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
			|| type == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			// Raid completion messages arrive as FRIENDSCHATNOTIFICATION.
			pbs.chatMessage(message, now);
			ChatParsers.KillTime time = ChatParsers.killTime(message);
			if (time != null)
			{
				rememberKillTime(time);
			}
		}
		if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM)
		{
			if (!nerdLogged && ChatParsers.isNerdLogWarning(message))
			{
				nerdLogged = true;
				recordNerdLog(now);
			}
			if (ChatParsers.isPickpocket(message))
			{
				pickpocketTick = client.getTickCount();
				return;
			}
			ChatParsers.KillCount kc = ChatParsers.killCount(message);
			if (kc != null)
			{
				recorder.killCount(kc, now);
				if (!progressAccepted())
				{
					return;
				}
				Record kcRecord = DropRecorder.newRecord(Record.Type.kc, client.getWorld(), now);
				Record.Source source = new Record.Source(kc.getSource(), null);
				source.setKc(kc.getCount());
				kcRecord.setSource(source);
				// The kill's time may come just before or just after the kill count, so wait a moment for it.
				pendingKcs.add(new PendingKc(kcRecord, client.getTickCount(), instanceTeamSize()));
				return;
			}
			ChatParsers.PetMessage pet = ChatParsers.pet(message);
			if (pet != null)
			{
				Record record = recorder.pet(pet.isDuplicate(), client.getWorld(), now);
				submit(record, settings.getScreenshots().isPets());
				return;
			}
			String clogItem = ChatParsers.newCollectionLogItem(message);
			if (clogItem != null)
			{
				Record record = recorder.clog(clogItem, client.getVarpValue(VarPlayerID.COLLECTION_COUNT), client.getWorld(), now);
				submit(record, settings.getScreenshots().isClog());
			}
		}
		else if (type == ChatMessageType.CLAN_MESSAGE || type == ChatMessageType.CLAN_GIM_MESSAGE)
		{
			ChatParsers.ClanPet clanPet = ChatParsers.clanPet(message);
			Player local = client.getLocalPlayer();
			if (clanPet != null && local != null)
			{
				recorder.clanPet(clanPet, local.getName(), now);
			}
		}
	}

	/** Puts the sender's team badge in place of their rank icon, while an event with teams is live. Client thread. */
	private void addTeamBadge(ChatMessage event)
	{
		TeamBadges.Badge badge = badgePlayers.get(TeamBadges.key(event.getName()));
		int index = badge != null ? chatIconManager.chatIconIndex(badgeIcons[badge.getSlot()]) : -1;
		if (index >= 0)
		{
			event.getMessageNode().setName(TeamBadges.withBadge(event.getMessageNode().getName(), index));
		}
	}

	/** Repaints the badge icons for the current event's teams and remembers who is on which. Client thread. */
	private void updateTeamBadges(ClanView view)
	{
		Map<String, TeamBadges.Badge> teams = TeamBadges.teams(view);
		for (TeamBadges.Badge badge : teams.values())
		{
			String painted = badge.getTag() + badge.getColour().getRGB();
			if (!painted.equals(badgePainted[badge.getSlot()]))
			{
				chatIconManager.updateChatIcon(badgeIcons[badge.getSlot()], TeamBadges.image(badge.getTag(), badge.getColour()));
				badgePainted[badge.getSlot()] = painted;
			}
		}
		badgePlayers = TeamBadges.players(view);
	}

	// ---- events, teams and news ----

	/** Every 30 seconds: reload when due, send reminders, and keep countdowns current. Executor thread. */
	private void clanTick()
	{
		long now = System.currentTimeMillis();
		long interval = hasLiveBoard() ? BOARD_REFRESH_MILLIS : CLAN_REFRESH_MILLIS;
		if (now - lastClanFetch >= interval)
		{
			refreshClan(true);
		}
		AsgardiansPanel shown = panel;
		// Every few minutes even while closed, so the Home tiles for Drops and PBs have something to show.
		if (now - lastStatsFetch >= (shown != null && shown.statsShown() ? STATS_SHOWN_MILLIS : STATS_IDLE_MILLIS))
		{
			fetchStats();
		}
		fetchEggs();
		if ((shown != null && shown.membersShown()) || now - lastActivityFetch >= ACTIVITY_IDLE_MILLIS)
		{
			fetchMemberActivity();
		}
		clientThread.invokeLater(() ->
		{
			sendReminders();
			refreshClanPanel();
			refreshGroupsPanel();
		});
	}

	private boolean hasLiveBoard()
	{
		for (ClanView.EventView live : clanView.live())
		{
			if (live.getEvent().isHasBoard() && live.getEvent().getTeamId() != null)
			{
				return true;
			}
		}
		return false;
	}

	/** Reloads events, teams, boards and news. Executor thread. */
	private void refreshClan(boolean withBoards)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null || !sharing || !fetchingClan.compareAndSet(false, true))
		{
			refreshClanPanel();
			return;
		}
		lastClanFetch = System.currentTimeMillis();
		api.get(ApiClient.endpoint(address, "me"), key, ClanData.Me.class, profile ->
		{
			// Ignore a reply that arrives after switching account.
			if (profile.ok() && account != null && account.getHash() == who.getHash())
			{
				me = profile.getBody();
				refreshPanel();
			}
		});
		for (String period : ClanTabs.PERIODS)
		{
			api.get(ApiClient.endpoint(address, "leaderboards").newBuilder().addQueryParameter("period", period).build(), key,
				ClanData.LeaderboardList.class, list ->
				{
					if (list.ok() && account != null && account.getHash() == who.getHash())
					{
						leaderboards.put(period, ClanView.orEmpty(list.getBody().getBoards()));
						clientThread.invokeLater(this::refreshClanPanel);
					}
				});
		}
		api.get(ApiClient.endpoint(address, "motd"), key, ClanData.Motd.class, result ->
		{
			if (result.ok() && account != null && account.getHash() == who.getHash())
			{
				setMotd(result.getBody());
			}
		});
		ClanView previous = clanView;
		clanFetcher.fetch(address, key, withBoards, previous, result ->
		{
			fetchingClan.set(false);
			if (result.getFailureStatus() == 401)
			{
				configManager.unsetConfiguration(AsgardiansConfig.GROUP, installKeyName(address, who.getHash()));
				clientThread.invokeLater(this::ensureRegistered);
			}
			clanView = result.getView();
			clientThread.invokeLater(() ->
			{
				updateTeamBadges(result.getView());
				notifyClanChanges(previous, result.getView(), address);
				refreshClanPanel();
			});
		});
	}

	/** Chat messages for new announcements, events going live and reviewed tiles. Client thread. */
	private void notifyClanChanges(ClanView previous, ClanView now, String address)
	{
		if (client.getGameState() != GameState.LOGGED_IN || now.getFetchedAt() == null)
		{
			return;
		}
		String notifiedKey = "newsNotified_" + addressKey(address);
		String readKey = "newsRead_" + addressKey(address);
		String stored = configManager.getConfiguration(AsgardiansConfig.GROUP, notifiedKey);
		long latest = now.latestAnnouncementId();
		boolean firstLoadThisLogin = loginNewsPending;
		loginNewsPending = false;
		if (stored == null)
		{
			// First time on this server: start from here rather than repeating old news, now or at the next login.
			configManager.setConfiguration(AsgardiansConfig.GROUP, notifiedKey, latest);
			if (configManager.getConfiguration(AsgardiansConfig.GROUP, readKey) == null)
			{
				configManager.setConfiguration(AsgardiansConfig.GROUP, readKey, latest);
			}
		}
		else if (firstLoadThisLogin)
		{
			// The login summary covers anything new too, so it isn't announced twice.
			if (config.newsMessages())
			{
				String read = configManager.getConfiguration(AsgardiansConfig.GROUP, readKey);
				now.loginNews(read != null ? parseLong(read) : 0).forEach(this::chat);
			}
			configManager.setConfiguration(AsgardiansConfig.GROUP, notifiedKey, latest);
		}
		else if (latest > parseLong(stored))
		{
			if (config.newsMessages())
			{
				for (ClanData.Announcement a : now.newAnnouncements(parseLong(stored)))
				{
					chat("News: " + a.getTitle() + ". Open the Asgardians panel to read it.");
				}
			}
			configManager.setConfiguration(AsgardiansConfig.GROUP, notifiedKey, latest);
		}
		if (firstLoadThisLogin)
		{
			tellAboutMapOnce();
		}

		if (!config.eventMessages())
		{
			return;
		}
		if (firstLoadThisLogin)
		{
			now.buyInReminders().forEach(this::chat);
		}
		for (ClanData.Event event : now.newlyLive(previous))
		{
			chat(event.getName() + " has started." + (event.getCodeword() != null ? " Codeword: " + event.getCodeword() : ""));
		}
		String ownRsn = account != null ? account.getRsn() : null;
		long nowMillis = System.currentTimeMillis();
		announcedDrops.values().removeIf(at -> nowMillis - at > ANNOUNCED_MILLIS);
		Set<String> waiting = new java.util.HashSet<>();
		for (Map.Entry<String, ClanData.Drop> choice : now.choices())
		{
			waiting.add(ClanTabs.dropBusyKey(choice.getValue()));
		}
		signupsBusy.removeIf(k -> k.startsWith("drop:") && !waiting.contains(k));
		for (Map.Entry<String, ClanData.Drop> choice : now.choices())
		{
			if (choicesAnnounced.add(choice.getValue().getId()))
			{
				chat(choice.getKey() + ": " + (choice.getValue().getItem() != null ? choice.getValue().getItem() : "Your drop")
					+ " fits " + choice.getValue().getOptions().size() + " tiles. Pick one under Requires action on the Events tab.");
			}
		}
		for (String change : now.tileChanges(previous, (key, rsn) ->
			(rsn == null || rsn.equalsIgnoreCase(ownRsn)) && announcedDrops.remove(key) != null))
		{
			chat(change);
		}
		celebrateTiles(now.completedTiles(previous));
	}

	/** A ding for completed tiles, and fireworks when one was the member's own team's. Client thread. */
	private void celebrateTiles(List<Boolean> completed)
	{
		if (completed.isEmpty())
		{
			return;
		}
		if (config.tileSound())
		{
			// Their own sound effect volume, unless it's muted: they asked for the sound in the plugin settings.
			int volume = client.getPreferences().getSoundEffectVolume();
			client.playSoundEffect(TILE_SOUND, volume > 0 ? volume : net.runelite.api.SoundEffectVolume.MEDIUM_HIGH);
		}
		Player local = client.getLocalPlayer();
		if (config.tileFireworks() && completed.contains(true) && local != null)
		{
			// The level-up fireworks, on the member's own character and only on their screen.
			local.createSpotAnim(TILE_FIREWORKS_KEY, net.runelite.api.gameval.SpotanimID.LEVELUP_ANIM, 0, 0);
		}
	}

	/** Our own slot for the fireworks, so they don't replace anything the game is showing. */
	private static final int TILE_FIREWORKS_KEY = 0x45A6;

	/** Reminders 15 minutes before events the member has signed up for. Client thread. */
	private void sendReminders()
	{
		if (!config.eventMessages() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		for (ClanData.Event event : clanView.dueReminders(java.time.Instant.now(), REMINDER_WINDOW, reminded))
		{
			reminded.add(event.getId());
			chat(event.getName() + " starts in " + ClanView.countdown(java.time.Instant.now(), event.start()) + ".");
		}
	}

	/** The member opened the News tab. Client thread. */
	private void markNewsRead()
	{
		configManager.setConfiguration(AsgardiansConfig.GROUP, "newsRead_" + addressKey(apiAddress()),
			clanView.latestAnnouncementId());
		refreshClanPanel();
	}

	private void chat(String text)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=e8763f>Asgardians:</col> " + text.replace("<", "<lt>").replace(">", "<gt>"), null);
	}

	/** Safe from any thread: gathers online clan members on the client thread, then redraws Events and News. */
	private void refreshClanPanel()
	{
		if (!client.isClientThread())
		{
			clientThread.invokeLater(this::refreshClanPanel);
			return;
		}
		Map<String, Integer> online = onlineMembers();
		ApiClient.AccountInfo who = account;
		String notConnected = null;
		if (who == null || client.getGameState() != GameState.LOGGED_IN)
		{
			notConnected = "Log in to see clan events and news.";
		}
		else if (!sharing)
		{
			notConnected = "Events and news load once you're in the Asgardians clan with \"Share my data with Asgardians\" ticked.";
		}
		else if (installKey(apiAddress(), who) == null)
		{
			notConnected = "Connecting…";
		}
		String read = configManager.getConfiguration(AsgardiansConfig.GROUP, "newsRead_" + addressKey(apiAddress()));
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// Answers the sign-up form can fill in, read here on the client thread for the Swing dialog.
			int[] thresholds = {
				client.getVarbitValue(VarbitID.CA_THRESHOLD_EASY), client.getVarbitValue(VarbitID.CA_THRESHOLD_MEDIUM),
				client.getVarbitValue(VarbitID.CA_THRESHOLD_HARD), client.getVarbitValue(VarbitID.CA_THRESHOLD_ELITE),
				client.getVarbitValue(VarbitID.CA_THRESHOLD_MASTER), client.getVarbitValue(VarbitID.CA_THRESHOLD_GRANDMASTER)};
			signupPrefill = new SignupForm.Prefill(java.time.ZoneId.systemDefault().getId(),
				SignupForm.caTier(client.getVarbitValue(VarbitID.CA_POINTS), thresholds));
		}
		ClanTabs.Input input = new ClanTabs.Input(clanView, online, read != null ? parseLong(read) : 0, notConnected,
			java.time.ZoneId.systemDefault(), java.time.Instant.now(), apiAddress(),
			new java.util.HashMap<>(signupLists), new java.util.HashSet<>(signupsOpen), new java.util.HashSet<>(signupsBusy),
			new java.util.HashMap<>(leaderboards));
		StatsTabs.Input statsInput = new StatsTabs.Input(stats, statsUnsupported,
			notConnected == null ? null : who == null || client.getGameState() != GameState.LOGGED_IN
				? "Log in to see clan stats." : !sharing
				? "Clan stats load once you're in the Asgardians clan with \"Share my data with Asgardians\" ticked."
				: "Connecting…",
			statsError, statsFetchedAt, java.time.Instant.now(), java.time.ZoneId.systemDefault());
		MembersTabs.Input membersInput = membersInput(notConnected == null ? null
			: who == null || client.getGameState() != GameState.LOGGED_IN ? "Log in to see who's online." : notConnected);
		AsgardiansPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				target.updateClan(input);
				target.updateStats(statsInput);
				target.updateMembers(membersInput);
			});
		}
	}

	/** Online clan members: lowercase RSN to world. Client thread. */
	private Map<String, Integer> onlineMembers()
	{
		Map<String, Integer> online = new java.util.HashMap<>();
		net.runelite.api.clan.ClanChannel channel = client.getClanChannel();
		if (channel != null)
		{
			for (net.runelite.api.clan.ClanChannelMember member : channel.getMembers())
			{
				online.put(Text.sanitize(member.getName()).toLowerCase(java.util.Locale.UK), member.getWorld());
			}
		}
		return online;
	}

	// ---- group finder ----

	/**
	 * Every 5 seconds: checks in while the member is in a group (so the website knows they're still there), and
	 * otherwise loads the open groups, more often while the Groups tab is open or new groups are announced in chat.
	 */
	private void groupTick()
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		GameState state = client.getGameState();
		boolean playing = state == GameState.LOGGED_IN || state == GameState.HOPPING || state == GameState.LOADING;
		if (key == null || !sharing || !playing)
		{
			return;
		}
		ClanData.GroupList list = groups;
		boolean inGroup = list != null && list.getMine() != null;
		AsgardiansPanel shown = panel;
		long every = groupsUnsupported ? GROUP_UNSUPPORTED_MILLIS
			: inGroup || (shown != null && shown.groupsShown()) ? GROUP_IN_GROUP_MILLIS
			: anyBellRings() ? GROUP_ALERT_MILLIS : GROUP_IDLE_MILLIS;
		long now = System.currentTimeMillis();
		if (now - lastGroupsPoll < every || !groupsPolling.compareAndSet(false, true))
		{
			return;
		}
		lastGroupsPoll = now;
		if (!groupsUnsupported && (groupActivities.isEmpty() || now - lastActivitiesFetch > GROUP_ACTIVITIES_MILLIS))
		{
			lastActivitiesFetch = now;
			api.get(ApiClient.endpoint(address, "groups/activities"), key, ClanData.GroupActivityList.class, r ->
			{
				if (r.ok() && r.getBody().getActivities() != null && account == who)
				{
					groupActivities = r.getBody().getActivities();
					refreshGroupsPanel();
				}
			});
		}
		java.util.function.Consumer<ApiClient.GetResult<ClanData.GroupList>> done = r ->
		{
			groupsPolling.set(false);
			if (account != who)
			{
				return;
			}
			if (r.ok())
			{
				groupsUnsupported = false;
				applyGroups(r.getBody());
			}
			else if (r.getStatus() == 404 || (r.getStatus() >= 200 && r.getStatus() < 300))
			{
				// No groups on this website yet (a 404, or its web page instead of JSON).
				groupsUnsupported = true;
				refreshGroupsPanel();
			}
		};
		if (inGroup)
		{
			com.google.gson.JsonObject body = new com.google.gson.JsonObject();
			body.addProperty("world", client.getWorld());
			api.groups(address, key, "groups/checkin", body, done);
		}
		else
		{
			api.get(ApiClient.endpoint(address, "groups"), key, ClanData.GroupList.class, done);
		}
	}

	/** A new group list from the website: chat lines for what changed, the RuneLite party, and a redraw. */
	private void applyGroups(ClanData.GroupList now)
	{
		ApiClient.AccountInfo who = account;
		String me = who != null ? who.getRsn() : null;
		List<String> lines;
		List<String> started;
		/** Each a group and its title. */
		List<Object[]> onScreen;
		ClanData.GroupList before;
		synchronized (groupLock)
		{
			before = groups;
			groups = now;
			groupsFetchedAt = java.time.Instant.now();
			lines = Groups.changes(before, now, me, leftGroupOnPurpose);
			Map<String, Boolean> choices = groupBells();
			Map<String, Boolean> screens = groupScreens();
			boolean alertsOn = config.groupAlerts();
			boolean screenOn = config.groupOverlay();
			started = new ArrayList<>();
			onScreen = new ArrayList<>();
			for (ClanData.Group g : Groups.startedGroups(before, now, me, id -> GroupBells.rings(activity(id), choices, alertsOn)))
			{
				ClanData.GroupActivity activity = activity(g.getActivity());
				String title = Groups.shortTitle(g, activity);
				started.add(Groups.startedLine(g, title));
				if (GroupBells.onScreen(activity, choices, screens, alertsOn, screenOn))
				{
					onScreen.add(new Object[]{g, title});
				}
			}
			if (before != null && before.getMine() != null && !before.getMine().equals(now.getMine()))
			{
				leftGroupOnPurpose = null;
			}
			// A request the member took back has been dealt with.
			String asked = before != null ? before.getWaiting() : null;
			if (asked != null && asked.equals(leftGroupOnPurpose) && !asked.equals(now.getWaiting()))
			{
				leftGroupOnPurpose = null;
			}
		}
		updateParty(now);
		if (!lines.isEmpty() || !started.isEmpty())
		{
			clientThread.invokeLater(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					lines.forEach(this::chat);
					started.forEach(this::chat);
					GroupAlertOverlay overlay = groupAlertOverlay;
					if (overlay != null)
					{
						onScreen.forEach(o -> overlay.add((ClanData.Group) o[0], (String) o[1]));
					}
				}
			});
		}
		refreshGroupsPanel();
	}

	/** Puts the member in their group's RuneLite party once, and takes them out of it when they leave the group. */
	private void updateParty(ClanData.GroupList now)
	{
		ClanData.Group mine = now.mineGroup();
		String pass = mine != null && mine.getParty() != null ? mine.getParty().trim() : "";
		if (mine != null && !pass.isEmpty() && config.groupParty() && !mine.getId().equals(partyGroup))
		{
			joinParty(mine.getId(), pass);
		}
		else if (mine == null && partyPassphrase != null)
		{
			String left = partyPassphrase;
			partyGroup = null;
			partyPassphrase = null;
			SwingUtilities.invokeLater(() ->
			{
				// Only if they're still in the group's party, not one they've since moved to.
				if (left.equals(partyService.getPartyPassphrase()))
				{
					partyService.changeParty(null);
				}
			});
		}
	}

	private void joinParty(String groupId, String pass)
	{
		partyGroup = groupId;
		partyPassphrase = pass;
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				if (!pass.equals(partyService.getPartyPassphrase()))
				{
					partyService.changeParty(pass);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("Asgardians couldn't join the group's party: {}", e.getMessage());
			}
			refreshGroupsPanel();
		});
	}

	/** Safe from any thread: redraws the Groups tab from the last list. */
	private void refreshGroupsPanel()
	{
		if (!client.isClientThread())
		{
			clientThread.invokeLater(this::refreshGroupsPanel);
			return;
		}
		AsgardiansPanel target = panel;
		if (target == null)
		{
			return;
		}
		ApiClient.AccountInfo who = account;
		String notConnected = null;
		if (who == null || client.getGameState() != GameState.LOGGED_IN)
		{
			notConnected = "Log in to find or start a group.";
		}
		else if (!sharing)
		{
			notConnected = "Groups load once you're in the Asgardians clan with \"Share my data with Asgardians\" ticked.";
		}
		ClanData.GroupList list = groups;
		ClanData.Group mine = list != null ? list.mineGroup() : null;
		String pass = partyService.getPartyPassphrase();
		boolean inParty = mine != null && mine.getParty() != null && partyService.isInParty() && mine.getParty().trim().equals(pass);
		GroupTabs.Bells bells = new GroupTabs.Bells(groupBells(), config.groupAlerts(),
			Boolean.parseBoolean(configManager.getConfiguration(AsgardiansConfig.GROUP, GROUP_BELLS_OPEN_KEY)),
			groupScreens(), config.groupOverlay());
		GroupTabs.Input input = new GroupTabs.Input(list, groupActivities, groupsUnsupported, notConnected,
			who != null ? who.getRsn() : null, onlineMembers(), inParty, groupBusy, groupsFetchedAt, java.time.ZoneId.systemDefault(),
			bells);
		SwingUtilities.invokeLater(() -> target.updateGroups(input));
	}

	// ---- group alert bells ----

	/** The member's bells, saved as "id=1,id=0". Hidden from the settings panel; changed from the Groups tab. */
	private static final String GROUP_BELLS_KEY = "groupBells";
	private static final String GROUP_BELLS_OPEN_KEY = "groupBellsOpen";

	private static final String GROUP_SCREENS_KEY = "groupScreens";

	private Map<String, Boolean> groupScreens()
	{
		return GroupBells.parse(configManager.getConfiguration(AsgardiansConfig.GROUP, GROUP_SCREENS_KEY));
	}

	private Map<String, Boolean> groupBells()
	{
		return GroupBells.parse(configManager.getConfiguration(AsgardiansConfig.GROUP, GROUP_BELLS_KEY));
	}

	/** The activity with this ID, or one with no settings (alerts on by default) when the list doesn't have it. */
	private ClanData.GroupActivity activity(String id)
	{
		for (ClanData.GroupActivity a : groupActivities)
		{
			if (a.getId() != null && a.getId().equals(id))
			{
				return a;
			}
		}
		ClanData.GroupActivity unknown = new ClanData.GroupActivity();
		unknown.setId(id);
		return unknown;
	}

	/** Some new group would be announced, so it's worth checking for them more often. */
	private boolean anyBellRings()
	{
		Map<String, Boolean> choices = groupBells();
		boolean alertsOn = config.groupAlerts();
		if (groupActivities.isEmpty())
		{
			return alertsOn;
		}
		for (ClanData.GroupActivity a : groupActivities)
		{
			if (GroupBells.rings(a, choices, alertsOn))
			{
				return true;
			}
		}
		return false;
	}

	/** The member's kill count for an activity, from RuneLite's own records, or null. */
	private Integer groupKc(String activityId)
	{
		for (ClanData.GroupActivity a : groupActivities)
		{
			if (a.getId() != null && a.getId().equals(activityId) && a.getKcName() != null)
			{
				return configManager.getRSProfileConfiguration(PersonalBest.KC_CONFIG_GROUP,
					a.getKcName().toLowerCase(java.util.Locale.UK), Integer.class);
			}
		}
		return null;
	}

	/** What the Stats tab asks of the plugin. */
	private StatsTabs.Actions statsActions()
	{
		return new StatsTabs.Actions()
		{
			@Override
			public void refresh()
			{
				executor.execute(AsgardiansPlugin.this::fetchStats);
			}

			@Override
			public void opened()
			{
				if (System.currentTimeMillis() - lastStatsFetch >= STATS_OPENED_MILLIS)
				{
					executor.execute(AsgardiansPlugin.this::fetchStats);
				}
			}

			@Override
			public void icon(javax.swing.JLabel label, int itemId, int quantity)
			{
				itemManager.getImage(itemId, quantity, quantity > 1).addTo(label);
			}

			@Override
			public void bossIcon(javax.swing.JLabel label, String boss)
			{
				String name = StatsTabs.hiscoreName(boss, HISCORE_BOSSES.keySet());
				if (name == null)
				{
					return;
				}
				// Drawn as RuneLite's Hiscore panel draws them.
				spriteManager.getSpriteAsync(HISCORE_BOSSES.get(name).getSpriteId(), 0, sprite ->
					SwingUtilities.invokeLater(() -> label.setIcon(new javax.swing.ImageIcon(
						net.runelite.client.util.ImageUtil.resizeImage(
							net.runelite.client.util.ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20)))));
			}
		};
	}

	/** Reloads the Stats tab when it was opened a while after the last load. */
	private static final long STATS_OPENED_MILLIS = 30_000;
	/** While the Stats tab is open, its lists reload this often. */
	private static final long STATS_SHOWN_MILLIS = 60_000;
	/** Otherwise they reload this often, for the Home tiles. */
	private static final long STATS_IDLE_MILLIS = 5 * 60_000;

	/** Loads the clan's latest drops, clogs, PBs and pets. Executor thread. */
	private void fetchStats()
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null || !sharing || !fetchingStats.compareAndSet(false, true))
		{
			return;
		}
		lastStatsFetch = System.currentTimeMillis();
		api.get(ApiClient.endpoint(address, "stats/recent"), key, ClanData.StatsFeed.class, result ->
		{
			fetchingStats.set(false);
			if (account == null || account.getHash() != who.getHash())
			{
				return;
			}
			if (result.ok() && result.getBody().known())
			{
				stats = result.getBody();
				statsUnsupported = false;
				statsError = null;
				statsFetchedAt = java.time.Instant.now();
			}
			else if (result.getStatus() == 404 || (result.getStatus() >= 200 && result.getStatus() < 300))
			{
				// No such path: a JSON 404, the website's own page (status 200), or a reply without any list.
				statsUnsupported = true;
				statsError = null;
				statsFetchedAt = java.time.Instant.now();
			}
			else
			{
				statsError = result.getStatus() == 0 ? "Can't reach the clan website." : "Couldn't load clan stats"
					+ (result.getError() != null ? ": " + result.getError() : ".");
			}
			refreshClanPanel();
		});
	}

	// ---- handeggs ----

	/** Ice Barrage landing, the freeze sound. */
	static final int EGG_SOUND = 171;
	/** The unique-drop ding of the bosses since Phantom Muspah, DT2's included (sotn_muspah_unique_loot_02). */
	static final int TILE_SOUND = 6765;
	/** The clan's message of the day, and the text already shown in chat this login. */
	private volatile ClanData.Motd motd;
	private String motdShown;
	/** A nerd log has been recorded since this login. */
	private boolean nerdLogged;
	/** At most this many clan egg messages per check, so a burst doesn't flood chat. */
	private static final int EGG_MESSAGES_MAX = 5;

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (!(actor instanceof Player) || local == null || !EggTracker.isEggAnimation(actor.getAnimation()))
		{
			return;
		}
		EggTracker.Egg egg = eggs.animation(actor.getName(), actor.getAnimation(), client.getTickCount(), local.getName());
		log.debug("Handegg animation {} by {} on tick {}: {}", actor.getAnimation(), actor.getName(), client.getTickCount(), egg);
		if (egg != null)
		{
			onEgg(egg, local.getName());
		}
	}

	/** The player egged someone, or was egged. Client thread. */
	private void onEgg(EggTracker.Egg egg, String me)
	{
		boolean hit = DropRecorder.sameRsn(egg.getTarget(), me);
		// Only eggs between clan members count: the player must be in the clan, and so must the other player.
		String other = hit ? egg.getThrower() : egg.getTarget();
		ClanSettings clan = client.getClanSettings();
		if (!inClan() || !isClanMember(clan.getMembers(), other))
		{
			log.debug("Handegg with {} ignored: not a clan member", other);
			return;
		}
		if (hit && config.eggSound())
		{
			// Their own sound effect volume, unless it's muted: they asked for the sound in the plugin settings.
			int volume = client.getPreferences().getSoundEffectVolume();
			client.playSoundEffect(EGG_SOUND, volume > 0 ? volume : net.runelite.api.SoundEffectVolume.MEDIUM_HIGH);
		}
		if (config.eggMessages())
		{
			chat(hit ? egg.getThrower() + " egged you!" : "You egged " + egg.getTarget() + "!");
		}
		long now = System.currentTimeMillis();
		Record record = new Record(Record.Type.egg, java.time.Instant.ofEpochMilli(now).toString(), client.getWorld());
		record.createdMillis = now;
		record.setEgg(new Record.Egg(egg.getThrower(), egg.getTarget()));
		submit(record, false);
	}

	/** Shows new eggings between other clan members in chat. Executor thread, every 30 seconds. */
	private void fetchEggs()
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null || !sharing || eggsUnsupported || !config.eggMessages() || !fetchingEggs.compareAndSet(false, true))
		{
			return;
		}
		String cursor = eggCursor;
		okhttp3.HttpUrl url = ApiClient.endpoint(address, "eggs");
		if (cursor != null)
		{
			url = url.newBuilder().addQueryParameter("after", cursor).build();
		}
		api.get(url, key, ClanData.EggFeed.class, result ->
		{
			fetchingEggs.set(false);
			if (account == null || account.getHash() != who.getHash())
			{
				return;
			}
			if (!result.ok() || result.getBody().getCursor() == null)
			{
				// No such path (a JSON 404, or the website's own page with status 200): stop asking this session.
				eggsUnsupported = result.getStatus() == 404 || (result.getStatus() >= 200 && result.getStatus() < 300);
				return;
			}
			eggCursor = result.getBody().getCursor();
			if (cursor == null)
			{
				return;
			}
			List<String> lines = eggLines(result.getBody().getEggs(), who.getRsn());
			if (!lines.isEmpty())
			{
				clientThread.invokeLater(() -> lines.forEach(this::chat));
			}
		});
	}

	/**
	 * Someone typed "!asg …" in any chat. The line is replaced, on this player's screen only, with the answer. Clan-wide
	 * answers use what this plugin has already loaded; answers about whoever typed it look up their clan profile, and
	 * only for players on the clan's member list. Client thread.
	 */
	private void asgCommand(ChatMessage message, String text)
	{
		if (!config.chatCommands() || !sharing)
		{
			return;
		}
		String args = Text.removeTags(text).trim();
		if (!args.toLowerCase(java.util.Locale.UK).startsWith(AsgCommands.PREFIX))
		{
			return;
		}
		args = args.substring(AsgCommands.PREFIX.length()).trim();
		Player local = client.getLocalPlayer();
		String sender = message.getType() == ChatMessageType.PRIVATECHATOUT
			? (local != null ? local.getName() : null) : Text.sanitize(message.getName());
		if (sender == null || sender.isEmpty())
		{
			return;
		}
		net.runelite.api.MessageNode node = message.getMessageNode();
		String word = AsgCommands.word(args);
		if (!AsgCommands.personal(word))
		{
			AsgCommands.Clan clan = new AsgCommands.Clan(groups, groupActivities, clanView, new java.util.HashMap<>(leaderboards), stats,
				java.time.Instant.now());
			replaceLine(node, AsgCommands.answer(args, sender, clan));
			return;
		}
		ClanSettings clan = client.getClanSettings();
		if (!inClan() || !isClanMember(clan.getMembers(), sender))
		{
			return;
		}
		String lower = sender.toLowerCase(java.util.Locale.UK);
		Object[] cached = commandProfiles.get(lower);
		if (cached != null && System.currentTimeMillis() - (long) cached[0] < COMMAND_PROFILE_TTL)
		{
			replaceLine(node, AsgCommands.personal(word, sender, (ClanData.Me) cached[1]));
			return;
		}
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null)
		{
			return;
		}
		api.get(ApiClient.endpoint(address, "members/" + sender.replace('\u00a0', ' ') + "/profile"), key, ClanData.Me.class, result ->
		{
			ClanData.Me profile = result.ok() && result.getBody() != null && (result.getBody().getRank() != null
				|| result.getBody().getDaysInClan() != null || result.getBody().getRsn() != null) ? result.getBody() : null;
			if (profile != null)
			{
				commandProfiles.put(lower, new Object[]{System.currentTimeMillis(), profile});
			}
			clientThread.invokeLater(() -> replaceLine(node, AsgCommands.personal(word, sender, profile)));
		});
	}

	/** Shows the answer in place of the typed command. Client thread. */
	private void replaceLine(net.runelite.api.MessageNode node, String answer)
	{
		if (answer == null || node == null)
		{
			return;
		}
		node.setRuneLiteFormatMessage(answer);
		client.refreshChat();
	}

	/** The name is on the clan's member list. Names are compared the same way as RSNs elsewhere. */
	static boolean isClanMember(List<net.runelite.api.clan.ClanMember> members, String name)
	{
		for (net.runelite.api.clan.ClanMember member : ClanView.orEmpty(members))
		{
			if (member != null && DropRecorder.sameRsn(member.getName(), name))
			{
				return true;
			}
		}
		return false;
	}

	/** Chat lines for clan eggings, leaving out the player's own (they already saw those). */
	static List<String> eggLines(List<ClanData.EggAlert> alerts, String me)
	{
		List<String> lines = new ArrayList<>();
		for (ClanData.EggAlert alert : ClanView.orEmpty(alerts))
		{
			if (alert == null || alert.getThrower() == null || alert.getTarget() == null
				|| DropRecorder.sameRsn(alert.getThrower(), me) || DropRecorder.sameRsn(alert.getTarget(), me))
			{
				continue;
			}
			if (lines.size() == EGG_MESSAGES_MAX)
			{
				break;
			}
			lines.add(alert.getThrower() + " egged " + alert.getTarget() + "!");
		}
		return lines;
	}

	private GroupTabs.Actions groupActions()
	{
		return new GroupTabs.Actions()
		{
			@Override
			public void create(GroupTabs.GroupForm.Values v)
			{
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("activity", v.getActivity().getId());
				body.addProperty("mode", v.getMode());
				body.addProperty("size", v.getSize());
				body.addProperty("mass", v.isMass());
				body.addProperty("loot", v.getLoot());
				body.addProperty("note", v.getNote());
				body.addProperty("voice", v.isVoice());
				body.addProperty("voiceLimit", v.isVoiceLimit());
				body.addProperty("approval", v.isApproval());
				body.addProperty("world", client.getWorld());
				body.addProperty("kc", groupKc(v.getActivity().getId()));
				// The website names the group's RuneLite party (ASG1, ASG2…); every member's plugin then joins it.
				groupAction("groups", body, "Couldn't start the group", () ->
				{
					AsgardiansPanel target = panel;
					if (target != null)
					{
						target.groupStarted();
					}
				});
			}

			@Override
			public void bell(String activityId, boolean on)
			{
				Map<String, Boolean> choices = groupBells();
				choices.put(activityId, on);
				configManager.setConfiguration(AsgardiansConfig.GROUP, GROUP_BELLS_KEY, GroupBells.format(choices));
				refreshGroupsPanel();
			}

			@Override
			public void joinVoice(ClanData.Group group)
			{
				String link = Groups.voiceLink(group);
				if (link != null)
				{
					net.runelite.client.util.LinkBrowser.browse(link);
				}
			}

			@Override
			public void screen(String activityId, boolean on)
			{
				Map<String, Boolean> screens = groupScreens();
				screens.put(activityId, on);
				configManager.setConfiguration(AsgardiansConfig.GROUP, GROUP_SCREENS_KEY, GroupBells.format(screens));
				refreshGroupsPanel();
			}

			@Override
			public void foldBells()
			{
				boolean open = Boolean.parseBoolean(configManager.getConfiguration(AsgardiansConfig.GROUP, GROUP_BELLS_OPEN_KEY));
				configManager.setConfiguration(AsgardiansConfig.GROUP, GROUP_BELLS_OPEN_KEY, !open);
				refreshGroupsPanel();
			}

			@Override
			public void join(ClanData.Group group)
			{
				ClanData.GroupList list = groups;
				// Asking a group whose host picks doesn't take the member out of their own group yet.
				if (list != null && list.getMine() != null && !group.isApproval())
				{
					leftGroupOnPurpose = list.getMine();
				}
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("world", client.getWorld());
				body.addProperty("kc", groupKc(group.getActivity()));
				groupAction("groups/" + group.getId() + "/join", body, "Couldn't join the group", null);
			}

			@Override
			public void leave(ClanData.Group group)
			{
				leftGroupOnPurpose = group.getId();
				groupAction("groups/" + group.getId() + "/leave", new com.google.gson.JsonObject(), "Couldn't leave the group", null);
			}

			@Override
			public void close(ClanData.Group group)
			{
				ClanData.GroupList list = groups;
				int choice = javax.swing.JOptionPane.showConfirmDialog(panel, "Close the " + group.getTitle() + " group for everyone in it?",
					"Close group", javax.swing.JOptionPane.YES_NO_OPTION);
				if (choice != javax.swing.JOptionPane.YES_OPTION)
				{
					return;
				}
				if (list != null && group.getId().equals(list.getMine()))
				{
					leftGroupOnPurpose = group.getId();
				}
				groupAction("groups/" + group.getId() + "/close", new com.google.gson.JsonObject(), "Couldn't close the group", null);
			}

			@Override
			public void kick(ClanData.Group group, String rsn)
			{
				int choice = javax.swing.JOptionPane.showConfirmDialog(panel, "Remove " + rsn + " from the group?",
					"Kick", javax.swing.JOptionPane.YES_NO_OPTION);
				if (choice != javax.swing.JOptionPane.YES_OPTION)
				{
					return;
				}
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("rsn", rsn);
				groupAction("groups/" + group.getId() + "/kick", body, "Couldn't remove " + rsn, null);
			}

			@Override
			public void accept(ClanData.Group group, String rsn)
			{
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("rsn", rsn);
				groupAction("groups/" + group.getId() + "/accept", body, "Couldn't accept " + rsn, null);
			}

			@Override
			public void decline(ClanData.Group group, String rsn)
			{
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("rsn", rsn);
				groupAction("groups/" + group.getId() + "/decline", body, "Couldn't decline " + rsn, null);
			}

			@Override
			public void cancel(ClanData.Group group)
			{
				leftGroupOnPurpose = group.getId();
				groupAction("groups/" + group.getId() + "/cancel", new com.google.gson.JsonObject(), "Couldn't take back the request", null);
			}

			@Override
			public void joinParty(ClanData.Group group)
			{
				if (group.getParty() != null && !group.getParty().trim().isEmpty())
				{
					AsgardiansPlugin.this.joinParty(group.getId(), group.getParty().trim());
				}
			}

			@Override
			public void refresh()
			{
				lastGroupsPoll = 0;
				executor.execute(AsgardiansPlugin.this::groupTick);
				refreshGroupsPanel();
			}
		};
	}

	/** Sends a group action; the reply is the new group list. Errors are shown in a dialog. */
	private void groupAction(String path, com.google.gson.JsonObject body, String failTitle, Runnable onSuccess)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null || groupBusy)
		{
			return;
		}
		groupBusy = true;
		refreshGroupsPanel();
		api.groups(address, key, path, body, result ->
		{
			groupBusy = false;
			if (result.getStatus() >= 200 && result.getStatus() < 300)
			{
				if (result.getBody() != null && result.getBody().getGroups() != null)
				{
					applyGroups(result.getBody());
				}
				else
				{
					lastGroupsPoll = 0;
					executor.execute(this::groupTick);
				}
				if (onSuccess != null)
				{
					SwingUtilities.invokeLater(onSuccess);
				}
			}
			else
			{
				leftGroupOnPurpose = null;
				String reason = result.getStatus() == 0 ? "Couldn't reach the clan website."
					: result.getStatus() == 404 ? "The clan website doesn't have groups yet, or that group has gone."
					: result.getError() != null && !result.getError().isEmpty() ? result.getError()
					: "The clan website said no (" + result.getStatus() + ").";
				SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(panel, reason, failTitle,
					javax.swing.JOptionPane.WARNING_MESSAGE));
				lastGroupsPoll = 0;
			}
			refreshGroupsPanel();
		});
	}

	// ---- sign-ups (buttons call these on the Swing thread) ----

	private void signUp(ClanData.Event event)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null)
		{
			return;
		}
		Map<String, Object> answers = SignupForm.ask(panel, event, who.getRsn(), signupPrefill);
		if (answers == null)
		{
			return;
		}
		signupsBusy.add(event.getId());
		refreshClanPanel();
		api.signUp(address, key, event.getId(), answers, result -> afterSignup(event, result, true));
	}

	private void withdraw(ClanData.Event event)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null)
		{
			return;
		}
		int choice = javax.swing.JOptionPane.showConfirmDialog(panel,
			"Withdraw " + who.getRsn() + " from " + event.getName() + "?", "Withdraw",
			javax.swing.JOptionPane.YES_NO_OPTION);
		if (choice != javax.swing.JOptionPane.YES_OPTION)
		{
			return;
		}
		signupsBusy.add(event.getId());
		refreshClanPanel();
		api.withdraw(address, key, event.getId(), result -> afterSignup(event, result, false));
	}

	/** OkHttp thread: report the outcome, then reload events and any open sign-up list. */
	private void afterSignup(ClanData.Event event, ApiClient.GetResult<ClanData.SignupResult> result, boolean joining)
	{
		signupsBusy.remove(event.getId());
		boolean ok = result.getStatus() >= 200 && result.getStatus() < 300;
		if (ok)
		{
			String status = result.getBody() != null ? result.getBody().getMyStatus() : null;
			String message = !joining ? "You've withdrawn from " + event.getName() + "."
				: "waitlist".equals(status) ? "You're on the waitlist for " + event.getName() + "."
				: "You're signed up for " + event.getName() + ".";
			clientThread.invokeLater(() -> chat(message));
		}
		else
		{
			String reason = result.getStatus() == 0 ? "Couldn't reach the clan website."
				: result.getError() != null && !result.getError().isEmpty() ? result.getError()
				: "The clan website said no (" + result.getStatus() + ").";
			SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(panel, reason,
				joining ? "Couldn't sign up" : "Couldn't withdraw", javax.swing.JOptionPane.WARNING_MESSAGE));
		}
		if (signupsOpen.contains(event.getId()))
		{
			loadSignups(event);
		}
		executor.execute(() -> refreshClan(false));
	}

	/** Swing thread: sends which tile a drop that fits several should count for, then reloads the board. */
	private void chooseTile(ClanData.Event event, ClanData.Drop drop, ClanData.TileOption tile)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		String busyKey = ClanTabs.dropBusyKey(drop);
		if (key == null || !signupsBusy.add(busyKey))
		{
			return;
		}
		refreshClanPanel();
		String item = drop.getItem() != null ? drop.getItem() : "Your drop";
		String tileName = tile.getName() != null ? tile.getName() : "that tile";
		api.chooseTile(address, key, event.getId(), drop.getId(), tile.getTileId(), result ->
		{
			if (result.getStatus() >= 200 && result.getStatus() < 300)
			{
				clientThread.invokeLater(() -> chat(event.getName() + ": " + item + " now counts towards \"" + tileName + "\"."));
			}
			else
			{
				signupsBusy.remove(busyKey);
				String reason = result.getStatus() == 0 ? "Couldn't reach the clan website."
					: result.getError() != null && !result.getError().isEmpty() ? result.getError()
					: "The clan website said no (" + result.getStatus() + ").";
				SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(panel, reason,
					"Couldn't choose the tile", javax.swing.JOptionPane.WARNING_MESSAGE));
			}
			// After a success the buttons stay hidden until a reload no longer lists the drop as needing a choice.
			executor.execute(() -> refreshClan(true));
		});
	}

	private void toggleSignups(ClanData.Event event)
	{
		if (!signupsOpen.remove(event.getId()))
		{
			signupsOpen.add(event.getId());
			loadSignups(event);
		}
		refreshClanPanel();
	}

	private void loadSignups(ClanData.Event event)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null)
		{
			return;
		}
		api.get(ApiClient.endpoint(address, "events/" + event.getId() + "/signups"), key, ClanData.SignupList.class, result ->
		{
			if (result.ok())
			{
				signupLists.put(event.getId(), result.getBody());
			}
			else
			{
				ClanData.SignupList empty = new ClanData.SignupList();
				empty.setSignups(List.of());
				signupLists.put(event.getId(), empty);
			}
			refreshClanPanel();
		});
	}

	private static String addressKey(String address)
	{
		return ApiClient.sha256(address.toLowerCase()).substring(0, 16);
	}

	private static long parseLong(String value)
	{
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	// ---- deaths ----

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() == client.getLocalPlayer() && event.getTarget() != null)
		{
			lastTarget = new WeakReference<>(event.getTarget());
			if (event.getTarget() instanceof NPC && event.getTarget().getName() != null)
			{
				activityTarget = Text.removeTags(event.getTarget().getName());
				activityTargetTick = client.getTickCount();
			}
		}
	}

	/** The hiscore boss named in an event's name ("BOTW: Vorkath" gives Vorkath), longest match first. */
	static String bossIn(String eventName)
	{
		String name = eventName != null ? eventName.toLowerCase(java.util.Locale.UK) : "";
		String best = null;
		for (String boss : HISCORE_BOSSES.keySet())
		{
			if (name.contains(boss.toLowerCase(java.util.Locale.UK)) && (best == null || boss.length() > best.length()))
			{
				best = boss;
			}
		}
		return best;
	}

	/** The skill named in an event's name ("SOTW: Slayer" gives Slayer). */
	static Skill skillIn(String eventName)
	{
		String name = eventName != null ? eventName.toLowerCase(java.util.Locale.UK) : "";
		for (Skill skill : Skill.values())
		{
			if (!"Overall".equals(skill.getName()) && name.contains(skill.getName().toLowerCase(java.util.Locale.UK)))
			{
				return skill;
			}
		}
		return null;
	}

	// ---- activity, for the Members page ----

	/** Skills whose XP only comes from fighting, so they don't count as skilling. */
	private static final Set<Skill> COMBAT_SKILLS = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED);
	/** Work out the activity this often, in game ticks. */
	private static final int ACTIVITY_TICKS = 10;
	/** Send the activity at least this often while it stays the same, so the website knows the member's still there. */
	private static final long ACTIVITY_RESEND_MILLIS = 60_000;
	/** After the website says it has no activity feed, try again this much later. */
	private static final long ACTIVITY_RETRY_MILLIS = 30 * 60_000;
	/** Load clanmates' activities this often while the Members page is closed. */
	private static final long ACTIVITY_IDLE_MILLIS = 120_000;
	private String activityTarget;
	private int activityTargetTick = -1;
	private String activitySkill;
	private int activitySkillTick = -1;
	private int activitySlayerXpTick = -1;
	private final Map<Skill, Integer> lastXp = new java.util.EnumMap<>(Skill.class);
	/** What the member is doing, worked out on the client thread. */
	private volatile String myActivity;
	private volatile String sentActivity;
	private volatile boolean sentSharing = true;
	private volatile long sentActivityAt;
	private volatile boolean activityUnsupported;
	private volatile long activityUnsupportedUntil;
	private volatile long lastActivityFetch;
	/** Clanmates' activities from the website: lowercase RSN to activity. */
	private volatile Map<String, String> memberActivities = Map.of();
	/** Clanmates the website has heard from lately, so they have the plugin: lowercase RSNs. */
	private volatile Set<String> memberPlugins = Set.of();
	private final AtomicBoolean fetchingActivity = new AtomicBoolean();

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Integer before = lastXp.put(event.getSkill(), event.getXp());
		if (before != null && event.getXp() > before && event.getSkill() == Skill.SLAYER)
		{
			activitySlayerXpTick = client.getTickCount();
		}
		else if (before != null && event.getXp() > before && !COMBAT_SKILLS.contains(event.getSkill()))
		{
			activitySkill = event.getSkill().getName();
			activitySkillTick = client.getTickCount();
		}
	}

	/** Every few ticks: what the member is doing now. Client thread. */
	private void updateActivity(Player local)
	{
		int tick = client.getTickCount();
		if (tick % ACTIVITY_TICKS != 0)
		{
			return;
		}
		Actor target = local.getInteracting();
		if (target instanceof NPC && target.getName() != null)
		{
			activityTarget = Text.removeTags(target.getName());
			activityTargetTick = tick;
		}
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		WorldPoint where = local.getLocalLocation() != null ? WorldPoint.fromLocalInstance(client, local.getLocalLocation()) : null;
		net.runelite.api.WorldView top = client.getTopLevelWorldView();
		myLocation = config.shareLocation() && top != null && !top.isInstance() ? local.getWorldLocation() : null;
		myActivity = ActivityTracker.describe(new ActivityTracker.Snapshot(tick,
			client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1,
			client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) >= 2,
			activityTarget, activityTargetTick, client.getVarpValue(VarPlayerID.SLAYER_COUNT), activitySlayerXpTick,
			activitySkill, activitySkillTick, bank != null && !bank.isHidden(), where != null ? where.getRegionID() : -1));
		executor.execute(this::sendActivity);
	}

	/** Tells the website what the member is doing, when it changed or a minute has passed. Executor thread. */
	private void sendActivity()
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		long now = System.currentTimeMillis();
		if (key == null || !sharing || (activityUnsupported && now < activityUnsupportedUntil))
		{
			return;
		}
		boolean share = config.shareActivity();
		String activity = share ? myActivity : null;
		WorldPoint location = myLocation;
		boolean moved = !java.util.Objects.equals(location, sentLocation) && now - sentActivityAt >= LOCATION_RESEND_MILLIS;
		if (java.util.Objects.equals(activity, sentActivity) && share == sentSharing && now - sentActivityAt < ACTIVITY_RESEND_MILLIS
			&& !moved && (location == null) == (sentLocation == null))
		{
			return;
		}
		sentActivity = activity;
		sentSharing = share;
		sentActivityAt = now;
		sentLocation = location;
		com.google.gson.JsonObject body = new com.google.gson.JsonObject();
		body.addProperty("sharing", share);
		body.addProperty("activity", activity);
		body.addProperty("world", client.getWorld());
		// Only the member's own position, only with "Share my location on the map" on, never in instances.
		body.addProperty("location", location != null);
		if (location != null)
		{
			body.addProperty("x", location.getX());
			body.addProperty("y", location.getY());
			body.addProperty("plane", location.getPlane());
		}
		api.post(ApiClient.endpoint(address, "members/activity"), key, body, com.google.gson.JsonObject.class, result ->
		{
			if (result.getStatus() == 404)
			{
				activityUnsupported = true;
				activityUnsupportedUntil = System.currentTimeMillis() + ACTIVITY_RETRY_MILLIS;
			}
		});
	}

	/** While the world map is open, reloads where clanmates are every few seconds. Client thread. */
	private void maybeReloadMap()
	{
		if (!config.showClanMap() || System.currentTimeMillis() - lastActivityFetch < MAP_REFRESH_MILLIS)
		{
			return;
		}
		Widget map = client.getWidget(InterfaceID.Worldmap.UNIVERSE);
		if (map != null && !map.isHidden())
		{
			lastActivityFetch = System.currentTimeMillis();
			executor.execute(this::fetchMemberActivity);
		}
	}

	/** Puts clanmates on the world map, or takes them off. Client thread. */
	private void drawClanMap()
	{
		ClanMap map = clanMap;
		if (map == null)
		{
			return;
		}
		if (!config.showClanMap() || client.getGameState() != GameState.LOGGED_IN)
		{
			map.clear();
			return;
		}
		Player local = client.getLocalPlayer();
		String me = local != null && local.getName() != null ? Text.sanitize(local.getName()) : null;
		// Worlds from clan chat are the freshest.
		Map<String, Integer> worlds = new java.util.HashMap<>();
		net.runelite.api.clan.ClanChannel channel = client.getClanChannel();
		if (channel != null)
		{
			for (net.runelite.api.clan.ClanChannelMember m : channel.getMembers())
			{
				worlds.put(Text.sanitize(m.getName()).toLowerCase(java.util.Locale.UK), m.getWorld());
			}
		}
		List<ClanMap.Pin> pins = new ArrayList<>();
		for (ClanMap.Pin p : mapPins)
		{
			Integer w = worlds.get(p.getName().toLowerCase(java.util.Locale.UK));
			pins.add(w != null ? new ClanMap.Pin(p.getName(), w, p.getAt(), p.getActivity()) : p);
		}
		map.show(ClanMap.shown(pins, me, client.getWorld(), config.mapOnlyMyWorld()), client.getWorld());
	}

	/** Loads what clanmates are doing. Executor thread. */
	private void fetchMemberActivity()
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		if (key == null || !sharing || !fetchingActivity.compareAndSet(false, true))
		{
			return;
		}
		lastActivityFetch = System.currentTimeMillis();
		api.get(ApiClient.endpoint(address, "members/activity"), key, ClanData.MemberActivityList.class, result ->
		{
			fetchingActivity.set(false);
			if (account == null || account.getHash() != who.getHash())
			{
				return;
			}
			if (result.ok() && result.getBody().getMembers() != null)
			{
				Map<String, String> map = new java.util.HashMap<>();
				Set<String> plugins = new java.util.HashSet<>();
				List<ClanMap.Pin> pins = new ArrayList<>();
				for (ClanData.MemberActivity m : result.getBody().getMembers())
				{
					if (m == null || m.getRsn() == null)
					{
						continue;
					}
					String lower = Text.sanitize(m.getRsn()).toLowerCase(java.util.Locale.UK);
					plugins.add(lower);
					if (m.getActivity() != null)
					{
						map.put(lower, m.getActivity());
					}
					if (m.getX() != null && m.getY() != null)
					{
						pins.add(new ClanMap.Pin(Text.sanitize(m.getRsn()), m.getWorld() != null ? m.getWorld() : 0,
							new WorldPoint(m.getX(), m.getY(), m.getPlane() != null ? m.getPlane() : 0), m.getActivity()));
					}
				}
				memberActivities = map;
				memberPlugins = plugins;
				mapPins = pins;
				clientThread.invokeLater(this::drawClanMap);
				activityUnsupported = false;
			}
			else if (result.getStatus() == 404 || (result.getStatus() >= 200 && result.getStatus() < 300))
			{
				activityUnsupported = true;
				activityUnsupportedUntil = System.currentTimeMillis() + ACTIVITY_RETRY_MILLIS;
			}
			refreshClanPanel();
		});
	}

	/**
	 * Staff notes: loads them, adds one (text) or deletes one (id). About a member when rsn is set, else the general
	 * notes. The website only answers clan staff. Executor thread.
	 */
	private void staffNotes(String rsn, String text, String deleteId)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		AsgardiansPanel target = panel;
		if (target == null)
		{
			return;
		}
		if (key == null || !sharing)
		{
			SwingUtilities.invokeLater(() -> target.updateStaffNotes(rsn, new StaffTabs.Input(null, false,
				"Turn on \"Share my data with Asgardians\" and log in to see staff notes.", java.time.Instant.now())));
			return;
		}
		String path = rsn != null ? "staff/members/" + rsn.replace('\u00a0', ' ') + "/notes" : "staff/notes";
		java.util.function.Consumer<ApiClient.GetResult<ClanData.StaffNoteList>> done = result ->
		{
			String error = result.ok() ? null
				: result.getStatus() == 403 ? "Only clan staff can see staff notes."
				: result.getStatus() == 404 || (result.getStatus() >= 200 && result.getStatus() < 300)
					? "The clan website doesn't have staff notes yet."
				: text != null ? "Your note couldn't be saved. Try again in a moment." : "Notes couldn't be loaded. Try again in a moment.";
			List<ClanData.StaffNote> notes = result.ok() && result.getBody() != null && result.getBody().getNotes() != null
				? result.getBody().getNotes() : null;
			SwingUtilities.invokeLater(() -> target.updateStaffNotes(rsn, new StaffTabs.Input(notes, false, error, java.time.Instant.now())));
		};
		okhttp3.HttpUrl url = ApiClient.endpoint(address, path);
		if (text != null)
		{
			com.google.gson.JsonObject body = new com.google.gson.JsonObject();
			body.addProperty("text", text);
			api.post(url, key, body, ClanData.StaffNoteList.class, done);
		}
		else if (deleteId != null)
		{
			api.delete(ApiClient.endpoint(address, path + "/" + deleteId), key, ClanData.StaffNoteList.class, done);
		}
		else
		{
			api.get(url, key, ClanData.StaffNoteList.class, done);
		}
	}

	/** Loads a clanmate's profile for the panel. Executor thread. */
	private void fetchProfile(String rsn)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		AsgardiansPanel target = panel;
		if (key == null || !sharing || target == null)
		{
			if (target != null)
			{
				SwingUtilities.invokeLater(() -> target.updateProfile(rsn, null, false));
			}
			return;
		}
		api.get(ApiClient.endpoint(address, "members/" + rsn.replace('\u00a0', ' ') + "/profile"), key, ClanData.Me.class, result ->
		{
			boolean ok = result.ok() && result.getBody() != null && (result.getBody().getRank() != null
				|| result.getBody().getDaysInClan() != null || result.getBody().getRsn() != null);
			boolean unsupported = !ok && (result.getStatus() == 404 || (result.getStatus() >= 200 && result.getStatus() < 300));
			SwingUtilities.invokeLater(() -> target.updateProfile(rsn, ok ? result.getBody() : null, unsupported));
		});
	}

	/** Who's in the clan: online members with their world, the rest offline, with activities where known. Client thread. */
	private MembersTabs.Input membersInput(String notConnected)
	{
		Player local = client.getLocalPlayer();
		String me = local != null && local.getName() != null ? Text.sanitize(local.getName()) : null;
		if (notConnected != null)
		{
			return new MembersTabs.Input(List.of(), me, null, null, config.shareActivity(), activityUnsupported, notConnected);
		}
		Map<String, String> activities = memberActivities;
		Set<String> plugins = memberPlugins;
		Map<String, MembersTabs.Member> members = new LinkedHashMap<>();
		net.runelite.api.clan.ClanChannel channel = client.getClanChannel();
		if (channel != null)
		{
			for (net.runelite.api.clan.ClanChannelMember m : channel.getMembers())
			{
				String name = Text.sanitize(m.getName());
				String lower = name.toLowerCase(java.util.Locale.UK);
				members.put(lower, new MembersTabs.Member(name, m.getWorld(), activities.get(lower), plugins.contains(lower)));
			}
		}
		ClanSettings settings = client.getClanSettings();
		if (settings != null)
		{
			for (net.runelite.api.clan.ClanMember m : settings.getMembers())
			{
				String name = Text.sanitize(m.getName());
				String lower = name.toLowerCase(java.util.Locale.UK);
				members.putIfAbsent(lower, new MembersTabs.Member(name, null, null, plugins.contains(lower)));
			}
		}
		return new MembersTabs.Input(new ArrayList<>(members.values()), me, client.getWorld(), myActivity, config.shareActivity(),
			activityUnsupported, null);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player local = client.getLocalPlayer();
		if (event.getActor() == local && local != null && isRecording() && config.shareDeaths())
		{
			recordDeath(local);
		}
		if (event.getActor() == local || event.getActor() == lastTarget.get())
		{
			lastTarget = new WeakReference<>(null);
		}
	}

	private void recordDeath(Player local)
	{
		WorldView view = local.getWorldView();
		WorldPoint where = view.isInstance()
			? WorldPoint.fromLocalInstance(client, local.getLocalLocation(), view.getPlane())
			: local.getWorldLocation();
		int regionId = where.getRegionID();
		Death.Place place = Death.PLACES.get(regionId);
		Widget pestControl = client.getWidget(InterfaceID.PestStatusOverlay.PEST_STATUS_PORT2);
		if (place == null && pestControl != null && !pestControl.isHidden())
		{
			place = new Death.Place("Pest Control", true);
		}
		boolean wilderness = client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) > 0;
		Widget pvpSafe = client.getWidget(InterfaceID.PvpIcons.PVPW_SAFE);
		boolean pvp = (wilderness || WorldType.isPvpWorld(client.getWorldType())) && (pvpSafe == null || pvpSafe.isHidden());

		Death death = new Death();
		death.setRegionId(regionId);
		death.setPlane(where.getPlane());
		death.setInstanced(view.isInstance());
		death.setWilderness(wilderness);
		death.setArea(place != null ? place.getName() : null);
		death.setSafe(place != null && place.isSafe());
		death.setKiller(identifyKiller(local, pvp));

		int keepCount = 0;
		if (!"ULTIMATE_IRONMAN".equals(accountType()))
		{
			// Protect Item from the normal or Ruinous Powers prayer book.
			boolean protectItem = client.getVarbitValue(Prayer.PROTECT_ITEM.getVarbit()) == 1
				|| client.getVarbitValue(Prayer.RP_PROTECT_ITEM.getVarbit()) == 1;
			keepCount = (local.getSkullIcon() == -1 ? 3 : 0) + (protectItem ? 1 : 0);
		}
		death.setKeepCount(keepCount);
		death.setMessage(Death.message(config.deathMessage(), death.getKiller().getName(), death.getArea(), 0));
		if (!death.isSafe())
		{
			Death.Split split = Death.split(heldItems(), keepCount);
			death.setValueLost(split.lostValue());
			List<Record.Item> lost = new ArrayList<>();
			for (Death.Held held : split.getLost())
			{
				lost.add(new Record.Item(held.getId(), held.getName(), held.getQty(), held.getPrice()));
			}
			death.setLostItems(lost);
		}

		if (death.getMessage() != null)
		{
			// Now the value lost is known.
			death.setMessage(Death.message(config.deathMessage(), death.getKiller().getName(), death.getArea(), death.getValueLost()));
		}
		long now = System.currentTimeMillis();
		Record record = new Record(Record.Type.death, java.time.Instant.ofEpochMilli(now).toString(), client.getWorld());
		record.createdMillis = now;
		record.setDeath(death);
		Death.Killer killer = death.getKiller();
		if (killer.getName() != null)
		{
			record.setSource(new Record.Source(killer.getName(), killer.getNpcId()));
		}
		submit(record, settings.getScreenshots().isDeaths());
	}

	/** Once per RuneLite profile: tells the member the clan map shows them, and how to stop it. Client thread. */
	private void tellAboutMapOnce()
	{
		if ("true".equals(configManager.getConfiguration(AsgardiansConfig.GROUP, MAP_NOTICE_KEY)))
		{
			return;
		}
		configManager.setConfiguration(AsgardiansConfig.GROUP, MAP_NOTICE_KEY, "true");
		chat(config.shareLocation()
			? "New: clanmates on your world map. Clanmates can now see where you are on theirs. Don't want that? Turn off \"Share my location on the map\" in the plugin settings."
			: "New: clanmates on your world map. Turn on \"Share my location on the map\" in the plugin settings to show up on theirs.");
	}

	/** Set once the member has been told about the clan map. */
	private static final String MAP_NOTICE_KEY = "mapNoticeShown";

	/** A new message of the day: onto Home, and into chat once a login (again if staff change it). Any thread. */
	private void setMotd(ClanData.Motd next)
	{
		motd = next;
		AsgardiansPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.updateMotd(next));
		}
		clientThread.invokeLater(() ->
		{
			String text = next != null && next.getText() != null ? next.getText().trim() : "";
			if (text.isEmpty() || text.equals(motdShown) || !config.motdAtLogin()
				|| client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			motdShown = text;
			chat("Message of the day: " + text + (next.getAuthor() != null ? " (" + next.getAuthor() + ")" : ""));
		});
	}

	/** Staff set the message of the day (empty clears it) or post to News. Executor thread. */
	private void staffPost(String which, com.google.gson.JsonObject body)
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		String key = who != null ? installKey(address, who) : null;
		AsgardiansPanel target = panel;
		if (target == null)
		{
			return;
		}
		if (key == null || !sharing)
		{
			SwingUtilities.invokeLater(() -> target.staffResult(which, "Log in with sharing on first.", true));
			return;
		}
		boolean news = "news".equals(which);
		@SuppressWarnings("unchecked")
		Class<Object> type = (Class<Object>) (news ? ClanData.Announcement.class : ClanData.Motd.class);
		api.post(ApiClient.endpoint(address, news ? "staff/announcements" : "staff/motd"), key, body, type, result ->
			{
				int status = result.getStatus();
				boolean ok = status >= 200 && status < 300 && result.getBody() != null;
				String message = ok ? (news ? "Posted to News." : body.get("text").getAsString().isEmpty() ? "Cleared." : "Saved. Members see it at their next login.")
					: status == 403 ? "Only clan staff can do that."
					: status == 404 || (status >= 200 && status < 300) ? "The clan website can't do this yet."
					: "That didn't work. Try again in a moment.";
				SwingUtilities.invokeLater(() -> target.staffResult(which, message, !ok));
				if (ok && !news)
				{
					setMotd((ClanData.Motd) result.getBody());
				}
				else if (ok)
				{
					refreshClan(false);
				}
			});
	}

	/** Five hours fifty played: the game's first 6-hour logout warning. Counted once a login. Client thread. */
	private void recordNerdLog(long now)
	{
		Record record = DropRecorder.newRecord(Record.Type.nerdlog, client.getWorld(), now);
		submit(record, false);
		if (config.eventMessages())
		{
			chat("Nerd log! 5 hours 50 played. That's going on the clan's Nerd log high score.");
		}
	}

	/** Inventory and equipment, merged by item. */
	private List<Death.Held> heldItems()
	{
		Map<Integer, Integer> merged = new LinkedHashMap<>();
		for (int containerId : new int[]{net.runelite.api.gameval.InventoryID.INV, net.runelite.api.gameval.InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					merged.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
				}
			}
		}
		List<Death.Held> held = new ArrayList<>();
		merged.forEach((id, qty) -> held.add(
			new Death.Held(id, itemManager.getItemComposition(id).getMembersName(), qty, itemManager.getItemPrice(id))));
		return held;
	}

	/**
	 * Works out what killed the player, the way Dink does: the last thing the player attacked if it is still attacking
	 * them, else (in PvP) another player attacking them, else the strongest NPC attacking them. Players are never named.
	 */
	private Death.Killer identifyKiller(Player local, boolean pvp)
	{
		Actor last = lastTarget.get();
		if (attacking(local, last))
		{
			if (last instanceof NPC && validKiller(((NPC) last).getTransformedComposition()))
			{
				return npcKiller((NPC) last);
			}
			if (last instanceof Player && pvp && !isFriendly((Player) last))
			{
				return Death.Killer.player();
			}
		}
		if (pvp)
		{
			for (Player player : client.getTopLevelWorldView().players())
			{
				if (attacking(local, player) && !isFriendly(player))
				{
					return Death.Killer.player();
				}
			}
		}
		NPC best = null;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (attacking(local, npc) && validKiller(npc.getTransformedComposition())
				&& (best == null || killerRank(local, npc) > killerRank(local, best)))
			{
				best = npc;
			}
		}
		return best != null ? npcKiller(best) : Death.Killer.unknown();
	}

	private static boolean attacking(Player local, Actor actor)
	{
		return actor != null && actor != local && !actor.isDead() && actor.getInteracting() == local;
	}

	private static boolean isFriendly(Player player)
	{
		return player.isClanMember() || player.isFriend() || player.isFriendsChatMember();
	}

	private static boolean validKiller(NPCComposition npc)
	{
		return npc != null && npc.isInteractible() && !npc.isFollower() && npc.getCombatLevel() > 0;
	}

	/** Prefers attackable, then high level, then nearby NPCs. */
	private static long killerRank(Player local, NPC npc)
	{
		NPCComposition comp = npc.getTransformedComposition();
		boolean attackable = comp.getActions() != null && java.util.Arrays.asList(comp.getActions()).contains("Attack");
		int distance = local.getLocalLocation().distanceTo(npc.getLocalLocation());
		return (attackable ? 1L << 40 : 0) + ((long) comp.getCombatLevel() << 16) - Math.min(distance, 0xFFFF);
	}

	private static Death.Killer npcKiller(NPC npc)
	{
		NPCComposition comp = npc.getTransformedComposition();
		return new Death.Killer("npc", comp.getName(), comp.getId(), comp.getCombatLevel());
	}

	// ---- personal bests ----

	private void onPersonalBestChanged(ConfigChanged event)
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null || !profile.equals(event.getProfile()) || event.getNewValue() == null || !isRecording())
		{
			return;
		}
		String key = event.getKey();
		String value = event.getNewValue();
		// RuneLite writes the PB while handling the same chat message we read "(new personal best)" from, and the
		// order of subscribers isn't fixed, so decide once every subscriber has seen this message.
		clientThread.invokeLater(() ->
		{
			PersonalBest pb = pbs.changed(key, value, System.currentTimeMillis());
			if (pb == null)
			{
				return;
			}
			long now = System.currentTimeMillis();
			Record record = new Record(Record.Type.pb, java.time.Instant.ofEpochMilli(now).toString(), client.getWorld());
			record.createdMillis = now;
			record.setPb(pb);
			record.setSource(new Record.Source(pb.getBoss(), null));
			record.getSource().setKc(pb.getKc());
			submit(record, settings.getScreenshots().isPbs());
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.MENU:
			case InterfaceID.MENU_NEW:
				advLogLoaded = true;
				break;
			case InterfaceID.JOURNALSCROLL:
				scrollLoaded = true;
				break;
			case InterfaceID.QUESTSCROLL_SPEEDRUN:
				recordSpeedrun();
				break;
		}
	}

	/**
	 * A quest speedrun has finished: the game shows the quest, the run's time and the member's best. Speedrunning
	 * worlds are otherwise left out of recording, so this is the one record sent from them.
	 */
	private void recordSpeedrun()
	{
		Widget title = client.getWidget(InterfaceID.QuestscrollSpeedrun.QUEST_TITLE);
		Widget time = client.getWidget(InterfaceID.QuestscrollSpeedrun.TIME_TEXT);
		Widget best = client.getWidget(InterfaceID.QuestscrollSpeedrun.BEST_TEXT);
		if (title == null || time == null || !config.shareData() || settings.isKillSwitch() || !inClan() || !progressAccepted())
		{
			return;
		}
		String quest = ChatParsers.speedrunQuest(title.getText());
		Double seconds = ChatParsers.runTime(time.getText());
		Double bestSeconds = best != null ? ChatParsers.runTime(best.getText()) : null;
		if (quest == null || seconds == null)
		{
			return;
		}
		Record record = DropRecorder.newRecord(Record.Type.speedrun, client.getWorld(), System.currentTimeMillis());
		record.setSpeedrun(new Record.Speedrun(quest, seconds, bestSeconds, bestSeconds != null && seconds <= bestSeconds));
		submit(record, true);
	}

	/** Records the website may not take yet. */
	private static final Set<Record.Type> PROGRESS_TYPES = EnumSet.of(Record.Type.kc, Record.Type.xp, Record.Type.speedrun);
	/** After the website refuses one, wait this long before making them again. */
	private static final long PROGRESS_RETRY_MILLIS = 30 * 60_000;
	private volatile long progressPausedUntil;

	/** KC, XP and speedrun records are being made: the website hasn't refused one lately. */
	private boolean progressAccepted()
	{
		return System.currentTimeMillis() >= progressPausedUntil;
	}

	// ---- kill times on kc records ----

	/** A kill count and its time are paired when they come within this many game ticks of each other. */
	static final int KILL_TIME_TICKS = 3;

	/** A kc record waiting a moment for its kill's time. */
	@lombok.Value
	private static class PendingKc
	{
		Record record;
		int tick;
		/** The instance's player count as a team size, or null outside an instance. */
		String teamSize;
	}

	private final List<PendingKc> pendingKcs = new ArrayList<>();
	private ChatParsers.KillTime lastKillTime;
	private int lastKillTimeTick = -100;

	/** Remembers the latest kill time; a whole raid's time isn't replaced by a room's from the same moment. Client thread. */
	private void rememberKillTime(ChatParsers.KillTime time)
	{
		int tick = client.getTickCount();
		boolean recentTotal = lastKillTime != null && lastKillTime.isTotal() && tick - lastKillTimeTick <= KILL_TIME_TICKS;
		if (recentTotal && !time.isTotal())
		{
			return;
		}
		lastKillTime = time;
		lastKillTimeTick = tick;
	}

	/** Sends kc records once their moment to pick up a time has passed (or all of them, when leaving). Client thread. */
	private void sendPendingKcs(boolean all)
	{
		int tick = client.getTickCount();
		for (java.util.Iterator<PendingKc> it = pendingKcs.iterator(); it.hasNext(); )
		{
			PendingKc p = it.next();
			if (!all && tick - p.getTick() < KILL_TIME_TICKS)
			{
				continue;
			}
			it.remove();
			ChatParsers.KillTime time = lastKillTime;
			String boss = p.getRecord().getSource().getName();
			if (time != null && Math.abs(lastKillTimeTick - p.getTick()) <= KILL_TIME_TICKS && fits(boss, time))
			{
				PersonalBest asPb = PersonalBest.of(boss, time.getSeconds());
				String size = time.getTeamSize() != null ? time.getTeamSize() : p.getTeamSize();
				p.getRecord().setTime(new Record.KillTime(time.getSeconds(), asPb.getTicks(), asPb.isPrecise(), size));
				// One time belongs to one kill.
				lastKillTime = null;
			}
			submit(p.getRecord(), false);
		}
	}

	/** A TzHaar-Ket-Rak challenge time never counts for TzTok-Jad (the full Fight Cave). */
	static boolean fits(String boss, ChatParsers.KillTime time)
	{
		return !(time.isChallenge() && boss != null && boss.equalsIgnoreCase("TzTok-Jad"));
	}

	/** In an instance, how many players are in it, as a team size ("1", "4", "11-15"); null outside one. */
	private String instanceTeamSize()
	{
		WorldView view = client.getTopLevelWorldView();
		if (view == null || !view.isInstance())
		{
			return null;
		}
		return teamSize(partySize());
	}

	/** A player count as PB team sizes write it. */
	static String teamSize(int players)
	{
		if (players <= 10)
		{
			return String.valueOf(Math.max(1, players));
		}
		return players <= 15 ? "11-15" : players <= 23 ? "16-23" : "24+";
	}

	/** Send the member's XP this often while it changes, in game ticks (about 10 minutes). */
	private static final int XP_TICKS = 1000;
	private int xpSentTick = -1;
	private long xpSentTotal = -1;

	/** Shortly after login, then every ten minutes while it changes: the member's XP in each skill. Client thread. */
	private void maybeSendXp()
	{
		int tick = client.getTickCount();
		if (xpSentTick < 0)
		{
			// Wait a few ticks after login for the skills to arrive.
			xpSentTick = tick - XP_TICKS + 10;
			return;
		}
		if (tick - xpSentTick < XP_TICKS || !isRecording() || !progressAccepted())
		{
			return;
		}
		long total = client.getOverallExperience();
		xpSentTick = tick;
		if (total <= 0 || total == xpSentTotal)
		{
			return;
		}
		xpSentTotal = total;
		Map<String, Integer> xp = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			if (!"Overall".equals(skill.getName()))
			{
				xp.put(skill.getName(), client.getSkillExperience(skill));
			}
		}
		Record record = DropRecorder.newRecord(Record.Type.xp, client.getWorld(), System.currentTimeMillis());
		record.setXp(xp);
		record.setTotalXp(total);
		// Through submit, so it shows under Recent submissions straight away.
		submit(record, false);
	}

	/**
	 * When the member opens their own collection log, ask the game for every item. This is the one use of
	 * Client.menuAction RuneLite's Plugin Hub rules allow, written exactly as they give it. Re-running the log's init
	 * script afterwards closes the search again, so the log looks as normal.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ClogCapture.SETUP_SCRIPT)
		{
			return;
		}
		if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
		{
			// Someone else's log, opened from their adventure log.
			clogCapture.reset();
			return;
		}
		long now = System.currentTimeMillis();
		if (!isRecording() || now - lastAutoFetchMillis < ClogCapture.AUTO_FETCH_COOLDOWN_MILLIS)
		{
			// Also stops the init script below, which runs setup again, from starting a second fetch.
			return;
		}
		lastAutoFetchMillis = now;
		client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
		client.runScript(ClogCapture.INIT_SCRIPT);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != ClogCapture.ITEM_SCRIPT || event.getScriptEvent() == null)
		{
			return;
		}
		if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
		{
			// Someone else's log, opened from their adventure log: never record it as ours.
			clogCapture.reset();
			return;
		}
		if (!isRecording())
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		if (args != null && args.length > 2 && args[1] instanceof Integer && args[2] instanceof Integer)
		{
			clogCapture.item((Integer) args[1], (Integer) args[2], client.getTickCount());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		updateActivity(local);
		maybeReloadMap();
		maybeSendXp();
		sendPendingKcs(false);
		Map<Integer, Integer> captured = clogCapture.finishIfQuiet(client.getTickCount());
		if (captured != null && captured.equals(lastClogCapture))
		{
			// Nothing new since the last read this session (the log is read every time it's opened).
			captured = null;
		}
		if (captured != null)
		{
			lastClogCapture = captured;
			List<ClogCapture.Item> items = ClogCapture.named(captured,
				id -> itemManager.getItemComposition(id).getMembersName());
			pendingClog = new PendingClog(items, client.getVarpValue(VarPlayerID.COLLECTION_COUNT),
				client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX));
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Asgardians Clan: read " + items.size() + " collection log items, sending to the clan website.", null);
			setClogStatus("Sending " + items.size() + " collection log items…", false);
			executor.execute(this::flush);
		}
		if (advLogLoaded)
		{
			advLogLoaded = false;
			String owner = adventureLogOwner(InterfaceID.MenuNew.TITLE);
			advLogOwner = owner != null ? owner : adventureLogOwner(InterfaceID.Menu.LJ_LAYER2);
		}
		if (scrollLoaded)
		{
			scrollLoaded = false;
			Widget text = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);
			if (text != null && local.getName() != null && local.getName().equals(advLogOwner) && mentionsFastest(text))
			{
				// RuneLite reads these counters into its PB store on this tick; sync once it has.
				configManager.setConfiguration(AsgardiansConfig.GROUP, countersKey(client.getAccountHash()), true);
				pbs.requestSync(System.currentTimeMillis());
				refreshPanel();
			}
		}
	}

	private String adventureLogOwner(int componentId)
	{
		Widget container = client.getWidget(componentId);
		if (container == null)
		{
			return null;
		}
		String own = adventureLogTitle(container.getText());
		if (own != null || container.getChildren() == null)
		{
			return own;
		}
		for (Widget child : container.getChildren())
		{
			String found = child != null ? adventureLogTitle(child.getText()) : null;
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private static String adventureLogTitle(String text)
	{
		String plain = text != null ? Text.removeTags(text) : "";
		return plain.startsWith("The Exploits of ") ? plain.substring("The Exploits of ".length()) : null;
	}

	private static boolean mentionsFastest(Widget layer)
	{
		Widget[] lines = layer.getStaticChildren();
		if (lines == null)
		{
			return false;
		}
		for (Widget line : lines)
		{
			if (line != null && line.getText() != null && line.getText().contains("Fastest"))
			{
				return true;
			}
		}
		return false;
	}

	private static String countersKey(long accountHash)
	{
		return "countersOpened_" + accountHash;
	}

	private boolean chatCommandsEnabled()
	{
		return !"false".equals(configManager.getConfiguration("runelite", "chatcommandsplugin"));
	}

	/** The "Sync all PBs now" button. Executor thread. */
	private void syncPbsNow()
	{
		pbs.requestSyncNow(System.currentTimeMillis());
		flush();
	}

	/** Sends RuneLite's whole PB store. Executor thread; only called from flush with a valid key. */
	private void syncPersonalBests(String address, String key, ApiClient.AccountInfo who)
	{
		if (!syncingPbs.compareAndSet(false, true))
		{
			return;
		}
		pbs.syncStarted();
		List<PersonalBest> all = pbs.snapshot();
		api.syncPersonalBests(address, key, who, all, result ->
		{
			syncingPbs.set(false);
			if (result.ok())
			{
				pbSyncedThisSession.add(who.getHash());
				setPbStatus(all.size() + " PBs synced at "
					+ java.time.LocalTime.now().withNano(0), false);
			}
			else if (result.getStatus() == 401)
			{
				configManager.unsetConfiguration(AsgardiansConfig.GROUP, installKeyName(address, who.getHash()));
				pbs.requestSync(System.currentTimeMillis());
				clientThread.invokeLater(this::ensureRegistered);
			}
			else if (result.getStatus() == 0 || result.getStatus() == 429 || result.getStatus() >= 500)
			{
				pbs.requestSync(System.currentTimeMillis());
				setPbStatus("PB sync failed, will retry", true);
			}
			else
			{
				log.warn("Asgardians rejected PB sync ({}): {}", result.getStatus(), result.getError());
				setPbStatus("The server rejected the PB sync (" + result.getStatus() + ")", true);
			}
		});
	}

	/** Sends the latest captured collection log. Executor thread; only called from flush with a valid key. */
	private void syncCollectionLog(String address, String key, ApiClient.AccountInfo who)
	{
		PendingClog clog = pendingClog;
		if (clog == null || !syncingClog.compareAndSet(false, true))
		{
			return;
		}
		api.syncCollectionLog(address, key, who, clog.getItems(), clog.getObtained(), clog.getTotal(), result ->
		{
			syncingClog.set(false);
			if (result.ok())
			{
				if (pendingClog == clog)
				{
					pendingClog = null;
				}
				configManager.setConfiguration(AsgardiansConfig.GROUP, clogKey(who.getHash()), true);
				setClogStatus(clog.getItems().size() + " collection log items synced at "
					+ java.time.LocalTime.now().withNano(0), false);
			}
			else if (result.getStatus() == 401)
			{
				configManager.unsetConfiguration(AsgardiansConfig.GROUP, installKeyName(address, who.getHash()));
				clientThread.invokeLater(this::ensureRegistered);
			}
			else if (result.getStatus() == 0 || result.getStatus() == 429 || result.getStatus() >= 500)
			{
				setClogStatus("Collection log sync failed, will retry", true);
			}
			else
			{
				if (pendingClog == clog)
				{
					pendingClog = null;
				}
				log.warn("Asgardians rejected collection log sync ({}): {}", result.getStatus(), result.getError());
				setClogStatus("The server rejected the collection log (" + result.getStatus() + ")", true);
			}
		});
	}

	private static String clogKey(long accountHash)
	{
		return "clogSynced_" + accountHash;
	}

	private void setClogStatus(String text, boolean error)
	{
		clogStatus = text;
		clogStatusIsError = error;
		refreshPanel();
	}

	private void setPbStatus(String text, boolean error)
	{
		pbStatus = text;
		pbStatusIsError = error;
		refreshPanel();
	}

	private void recordLoot(String source, Integer npcId, Collection<ItemStack> stacks, boolean withParty)
	{
		if (!isRecording() || stacks.isEmpty())
		{
			return;
		}
		Map<Integer, Integer> merged = new LinkedHashMap<>();
		for (ItemStack stack : stacks)
		{
			merged.merge(itemManager.canonicalize(stack.getId()), stack.getQuantity(), Integer::sum);
		}
		Record record = recorder.loot(source, npcId, merged, withParty ? partySize() : null, client.getWorld(),
			System.currentTimeMillis());
		if (record != null)
		{
			submit(record, settings.getScreenshots().wantsLoot(record));
		}
	}

	/**
	 * How many players are in the instance, including the player. Only a count: nothing about the other players is
	 * sent (RuneLite doesn't allow plugins to crowdsource other players' names, gear or locations).
	 */
	private int partySize()
	{
		int count = 0;
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player != null)
			{
				count++;
			}
		}
		return Math.max(count, 1);
	}

	private void submit(Record record, boolean wantsScreenshot)
	{
		boolean screenshot = wantsScreenshot && config.uploadScreenshots();
		record.setScreenshot(screenshot);
		record.awaitingScreenshot = screenshot;
		queue.add(record);
		if (screenshot)
		{
			executor.schedule(() -> clientThread.invokeLater(() -> captureWithoutPrivateChat(record)),
				SCREENSHOT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
		}
		refreshPanel();
	}

	/**
	 * Hides private messages for one frame, takes the screenshot, and puts them back. Fails safe: if they can't be
	 * hidden for certain, the record goes without a picture. Client thread.
	 */
	private void captureWithoutPrivateChat(Record record)
	{
		List<Widget> hidden = new ArrayList<>();
		AtomicBoolean restored = new AtomicBoolean();
		Runnable restore = () ->
		{
			if (restored.compareAndSet(false, true) && !hidden.isEmpty())
			{
				clientThread.invoke(() -> hidden.forEach(widget -> widget.setHidden(false)));
			}
		};
		try
		{
			hide(client.getWidget(InterfaceID.PmChat.CONTAINER), hidden);
			Widget[] roots = client.getWidgetRoots();
			if (roots != null)
			{
				for (Widget root : roots)
				{
					hidePrivateLines(root, 0, hidden);
				}
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Asgardians couldn't check for private messages, skipping the screenshot", e);
			restore.run();
			queue.noScreenshot(record.getId());
			return;
		}
		if (hidden.isEmpty() && chatPrivacy.messagedRecently(System.currentTimeMillis()))
		{
			// A private message was just sent or received but nothing was found to hide, so it may still be on screen.
			queue.noScreenshot(record.getId());
			return;
		}
		drawManager.requestNextFrameListener(image ->
		{
			restore.run();
			takeScreenshot(record, image);
		});
		// If no frame is drawn (client minimised), don't leave chat hidden.
		executor.schedule(restore, 3, TimeUnit.SECONDS);
	}

	private static void hide(Widget widget, List<Widget> hidden)
	{
		if (widget != null && !widget.isHidden())
		{
			widget.setHidden(true);
			hidden.add(widget);
		}
	}

	private void hidePrivateLines(Widget widget, int depth, List<Widget> hidden)
	{
		if (widget == null || depth > 8 || widget.isHidden())
		{
			return;
		}
		String text = widget.getText();
		if (text != null && !text.isEmpty() && chatPrivacy.isPrivate(Text.removeTags(text)))
		{
			hide(widget, hidden);
			return;
		}
		for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget child : children)
				{
					hidePrivateLines(child, depth + 1, hidden);
				}
			}
		}
	}

	private void takeScreenshot(Record record, Image image)
	{
		BufferedImage shot = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = shot.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		executor.execute(() ->
		{
			try (ByteArrayOutputStream out = new ByteArrayOutputStream())
			{
				ImageIO.write(shot, "png", out);
				queue.attachScreenshot(record.getId(), out.toByteArray());
			}
			catch (IOException e)
			{
				log.warn("Asgardians screenshot failed", e);
			}
			if (config.saveScreenshots())
			{
				imageCapture.saveScreenshot(shot, fileName(record), "Asgardians", false, false);
			}
		});
	}

	private static String fileName(Record record)
	{
		return record.describe().replaceAll("[^A-Za-z0-9 ()'-]", "").trim();
	}

	/** Recording needs consent, a logged-in clan member, an ordinary world and no server kill switch. */
	private boolean isRecording()
	{
		return config.shareData()
			&& !settings.isKillSwitch()
			&& client.getGameState() == GameState.LOGGED_IN
			&& inClan()
			&& client.getWorldType().stream().noneMatch(IGNORED_WORLDS::contains);
	}

	private boolean inClan()
	{
		ClanSettings clan = client.getClanSettings();
		return clan != null && CLAN_NAME.equalsIgnoreCase(clan.getName());
	}

	// ---- account and registration ----

	/** Captures who is logged in, registers if needed and refreshes the panel. Client thread. */
	private void onAccountState()
	{
		Player local = client.getLocalPlayer();
		if (client.getGameState() == GameState.LOGGED_IN && local != null && local.getName() != null)
		{
			long hash = client.getAccountHash();
			ApiClient.AccountInfo previous = account;
			if (previous != null && previous.getHash() != hash)
			{
				// Records belong to the account that earned them; a switch drops anything still unsent.
				queue.clear();
				me = null;
				leaderboards.clear();
				stats = null;
				statsUnsupported = false;
				statsError = null;
				statsFetchedAt = null;
				lastStatsFetch = 0;
				eggCursor = null;
				eggsUnsupported = false;
				eggs.clear();
				groups = null;
				groupActivities = List.of();
				lastGroupsPoll = 0;
				lastActivitiesFetch = 0;
				badgePlayers = Map.of();
				pendingClog = null;
				clogCapture.reset();
			}
			account = new ApiClient.AccountInfo(hash, Text.sanitize(local.getName()), accountType());
			if (config.shareData() && inClan())
			{
				ensureRegistered();
				if (!pbSyncedThisSession.contains(hash))
				{
					// Once per session, after RuneLite has loaded this account's stored PBs.
					pbs.requestSync(System.currentTimeMillis());
				}
			}
		}
		sharing = isRecording();
		if (loginNewsPending && sharing)
		{
			// Load straight away at login so unread news appears now, not at the next scheduled reload.
			executor.execute(() -> refreshClan(true));
		}
		refreshPanel();
	}

	private String accountType()
	{
		switch (client.getVarbitValue(VarbitID.IRONMAN))
		{
			case 1:
				return "IRONMAN";
			case 2:
				return "ULTIMATE_IRONMAN";
			case 3:
				return "HARDCORE_IRONMAN";
			case 4:
				return "GROUP_IRONMAN";
			case 5:
				return "HARDCORE_GROUP_IRONMAN";
			case 6:
				return "UNRANKED_GROUP_IRONMAN";
			default:
				return "NORMAL";
		}
	}

	private String installKeyName(String address, long accountHash)
	{
		return "installKey_" + ApiClient.sha256(address.toLowerCase()).substring(0, 16) + "_" + accountHash;
	}

	private String installKey(String address, ApiClient.AccountInfo who)
	{
		return configManager.getConfiguration(AsgardiansConfig.GROUP, installKeyName(address, who.getHash()));
	}

	private void ensureRegistered()
	{
		ApiClient.AccountInfo who = account;
		String address = apiAddress();
		if (who == null || installKey(address, who) != null || !registering.compareAndSet(false, true))
		{
			if (who != null && installKey(address, who) != null && status == null)
			{
				setStatus("Connected as " + who.getRsn(), false);
			}
			return;
		}
		setStatus("Connecting to " + address + "…", false);
		try
		{
			api.register(address, who.getRsn(), who.getHash(), who.getType(), result -> onRegistered(address, who, result));
		}
		catch (IllegalArgumentException e)
		{
			registering.set(false);
			setStatus("The API address isn't valid: " + address, true);
		}
	}

	private void onRegistered(String address, ApiClient.AccountInfo who, ApiClient.RegisterResult result)
	{
		registering.set(false);
		if (result.getInstallKey() != null)
		{
			configManager.setConfiguration(AsgardiansConfig.GROUP, installKeyName(address, who.getHash()), result.getInstallKey());
			setStatus("Connected as " + result.getDisplayName()
				+ (result.isHeld() ? " (held for staff review)" : ""), result.isHeld());
			executor.execute(this::flush);
			executor.execute(() -> refreshClan(true));
			return;
		}
		lastRegisterFailure = System.currentTimeMillis();
		if (result.getStatus() == 0)
		{
			setStatus("Can't reach " + address + ". Will retry.", true);
		}
		else if (result.getStatus() == 403)
		{
			setStatus(who.getRsn() + " isn't on the Asgardians roster yet. Nothing will be sent.", true);
		}
		else if (result.getStatus() == 404)
		{
			setStatus("This server doesn't have the plugin endpoints (404). Check the API address.", true);
		}
		else
		{
			setStatus("Couldn't connect (" + result.getStatus() + ")"
				+ (result.getError() != null ? ": " + result.getError() : ""), true);
		}
	}

	// ---- uploads (executor thread) ----

	/** Runs every few seconds so a debounced PB sync goes out soon after it is due. */
	private void pbSyncTick()
	{
		if (pbs.syncDue(System.currentTimeMillis()))
		{
			flush();
		}
	}

	private void flush()
	{
		ApiClient.AccountInfo who = account;
		boolean pbSyncDue = pbs.syncDue(System.currentTimeMillis()) && sharing;
		boolean clogDue = pendingClog != null;
		if (who == null || (queue.size() == 0 && !pbSyncDue && !clogDue) || settings.isKillSwitch())
		{
			return;
		}
		String address = apiAddress();
		String key = installKey(address, who);
		if (key == null)
		{
			if (System.currentTimeMillis() - lastRegisterFailure > REGISTER_RETRY_MILLIS)
			{
				clientThread.invokeLater(this::ensureRegistered);
			}
			return;
		}
		if (pbSyncDue)
		{
			syncPersonalBests(address, key, who);
		}
		if (clogDue)
		{
			syncCollectionLog(address, key, who);
		}
		if (queue.size() == 0 || !uploading.compareAndSet(false, true))
		{
			return;
		}
		List<Record> batch = queue.takeBatch(System.currentTimeMillis());
		if (batch.isEmpty())
		{
			uploading.set(false);
			return;
		}
		api.submitRecords(address, key, who, batch, queue.screenshotsFor(batch), result ->
		{
			uploading.set(false);
			if (result.ok())
			{
				queue.sent(batch);
				setStatus("Connected as " + who.getRsn(), false);
				announceDrops(batch);
				if (hasWatchedItem(batch))
				{
					// A watched item probably counts for an event, so reload the boards soon to announce the submission.
					executor.schedule(() -> refreshClan(true), WATCHED_REFRESH_SECONDS, TimeUnit.SECONDS);
				}
				if (queue.size() > 0)
				{
					executor.execute(this::flush);
				}
			}
			else if (result.getStatus() == 401)
			{
				// Key revoked or unknown on this server: forget it and register again.
				configManager.unsetConfiguration(AsgardiansConfig.GROUP, installKeyName(address, who.getHash()));
				queue.requeue(batch);
				setStatus("Reconnecting…", false);
				clientThread.invokeLater(this::ensureRegistered);
			}
			else if (result.getStatus() == 0 || result.getStatus() == 429 || result.getStatus() >= 500)
			{
				queue.requeue(batch);
				setStatus("Can't reach " + address + ". Will retry.", true);
			}
			else
			{
				// The server refused the batch. Retry its records one by one so only the bad one is dropped.
				log.warn("Asgardians rejected {} record(s) ({}): {}", batch.size(), result.getStatus(), result.getError());
				boolean progress = batch.size() == 1 && PROGRESS_TYPES.contains(batch.get(0).getType());
				if (progress)
				{
					// A website that doesn't take KC, XP or speedrun records yet: stop making them for a while.
					progressPausedUntil = System.currentTimeMillis() + PROGRESS_RETRY_MILLIS;
				}
				if (queue.rejected(batch, result.getError()))
				{
					executor.execute(this::flush);
				}
				else if (!progress)
				{
					setStatus("The server rejected a record: " + result.getError(), true);
				}
			}
		});
	}

	/** "<event>: Unicorn horn counts towards "3x Unicorn Horn". Drop submitted." for drops that count on the board. */
	private void announceDrops(List<Record> batch)
	{
		if (!config.eventMessages())
		{
			return;
		}
		ClanView view = clanView;
		List<String> lines = new ArrayList<>();
		for (Record record : batch)
		{
			if (record.getType() != Record.Type.loot)
			{
				continue;
			}
			for (ClanView.DropMatch match : view.dropMatches(record.getItems()))
			{
				match.getKeys().forEach(k -> announcedDrops.put(k, System.currentTimeMillis()));
				lines.add(match.getMessage());
			}
		}
		if (!lines.isEmpty())
		{
			clientThread.invokeLater(() -> lines.forEach(this::chat));
		}
	}

	private boolean hasWatchedItem(List<Record> batch)
	{
		for (Record record : batch)
		{
			if (settings.getScreenshots().watched(record))
			{
				return true;
			}
		}
		return false;
	}

	private void fetchSettings()
	{
		try
		{
			api.fetchSettings(apiAddress(), fetched -> settings = fetched);
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Asgardians settings skipped: {}", e.getMessage());
		}
	}

	// ---- panel ----

	private void setStatus(String text, boolean error)
	{
		status = text;
		statusIsError = error;
		refreshPanel();
	}

	/** Safe from any thread: reads game state on the client thread, then updates the panel on the Swing thread. */
	private void refreshPanel()
	{
		if (!client.isClientThread())
		{
			clientThread.invokeLater(this::refreshPanel);
			return;
		}
		Player local = client.getLocalPlayer();
		String rsn = client.getGameState() == GameState.LOGGED_IN && local != null ? local.getName() : null;
		boolean countersOpened = rsn != null
			&& Boolean.parseBoolean(configManager.getConfiguration(AsgardiansConfig.GROUP, countersKey(client.getAccountHash())));
		AsgardiansPanel.State state = new AsgardiansPanel.State(rsn, inClan(), config.shareData(), apiAddress(),
			status, statusIsError, queue.size(), queue.recent(),
			chatCommandsEnabled(), countersOpened, rsn != null && client.getVarbitValue(VarbitID.OPTION_PRECISE_TIMING) == 1,
			pbStatus, pbStatusIsError,
			rsn != null && Boolean.parseBoolean(configManager.getConfiguration(AsgardiansConfig.GROUP, clogKey(client.getAccountHash()))),
			clogStatus, clogStatusIsError, me);
		AsgardiansPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(state));
		}
	}
}
