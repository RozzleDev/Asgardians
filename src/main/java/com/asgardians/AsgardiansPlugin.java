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
import net.runelite.api.events.InteractingChanged;
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
	private static final String[] RAID_EVENTS = {"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"};
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
	private ItemManager itemManager;

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

	@Inject
	private OverlayManager overlayManager;

	private ClanFetcher clanFetcher;
	private CodewordOverlay codewordOverlay;
	private volatile ClanView clanView = ClanView.empty();
	private volatile long lastClanFetch;
	/** The member's clan profile for the Connection card. */
	private volatile ClanData.Me me;
	/** Set until the first load after logging in, which lists any unread announcements in chat. */
	private volatile boolean loginNewsPending = true;
	private final AtomicBoolean fetchingClan = new AtomicBoolean();
	/** Event IDs already reminded about this session. */
	private final Set<String> reminded = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private ScheduledFuture<?> clanTask;
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

	@Provides
	AsgardiansConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AsgardiansConfig.class);
	}

	@Override
	protected void startUp()
	{
		api = new ApiClient(okHttpClient, gson);
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
		codewordOverlay = new CodewordOverlay(this, config, () -> clanView);
		overlayManager.add(codewordOverlay);

		panel = new AsgardiansPanel(() -> executor.execute(this::syncPbsNow),
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
			});
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
		clientThread.invokeLater(this::onAccountState);
		log.info("Asgardians Clan started, API address {}", apiAddress());
	}

	@Override
	protected void shutDown()
	{
		flushTask.cancel(false);
		settingsTask.cancel(false);
		pbTask.cancel(false);
		clanTask.cancel(false);
		overlayManager.remove(codewordOverlay);
		executor.execute(this::flush);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
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
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
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
		}
		if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM)
		{
			if (ChatParsers.isPickpocket(message))
			{
				pickpocketTick = client.getTickCount();
				return;
			}
			ChatParsers.KillCount kc = ChatParsers.killCount(message);
			if (kc != null)
			{
				recorder.killCount(kc, now);
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
		clientThread.invokeLater(() ->
		{
			sendReminders();
			refreshClanPanel();
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

		if (!config.eventMessages())
		{
			return;
		}
		for (ClanData.Event event : now.newlyLive(previous))
		{
			chat(event.getName() + " has started." + (event.getCodeword() != null ? " Codeword: " + event.getCodeword() : ""));
		}
		for (String change : now.tileChanges(previous))
		{
			chat(change);
		}
	}

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
		Map<String, Integer> online = new java.util.HashMap<>();
		net.runelite.api.clan.ClanChannel channel = client.getClanChannel();
		if (channel != null)
		{
			for (net.runelite.api.clan.ClanChannelMember member : channel.getMembers())
			{
				online.put(Text.sanitize(member.getName()).toLowerCase(java.util.Locale.UK), member.getWorld());
			}
		}
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
			new java.util.HashMap<>(signupLists), new java.util.HashSet<>(signupsOpen), new java.util.HashSet<>(signupsBusy));
		AsgardiansPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.updateClan(input));
		}
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
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player local = client.getLocalPlayer();
		if (event.getActor() == local && local != null && isRecording())
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
		}
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
				if (queue.rejected(batch, result.getError()))
				{
					executor.execute(this::flush);
				}
				else
				{
					setStatus("The server rejected a record: " + result.getError(), true);
				}
			}
		});
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
