package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEST_SERVER;
import static com.asgardians.PanelParts.TODO;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.badge;
import static com.asgardians.PanelParts.button;
import static com.asgardians.PanelParts.check;
import static com.asgardians.PanelParts.dot;
import static com.asgardians.PanelParts.dotRow;
import static com.asgardians.PanelParts.small;
import static com.asgardians.PanelParts.statRow;
import static com.asgardians.PanelParts.status;
import static com.asgardians.PanelParts.wrap;
import java.awt.BorderLayout;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Side panel with six tabs. Groups: find or start a group for group content. Stats: the clan's latest drops,
 * collection log slots, PBs and pets. Status: connection, personal bests, collection log and recent submissions.
 * Events: the running event by team, and the schedule. Leaderboard: the current competition and this month's or
 * this year's boards. News: staff announcements. Swing thread only.
 */
class AsgardiansPanel extends PluginPanel
{
	/** Everything the Status tab shows, captured off the Swing thread. */
	@Value
	static class State
	{
		String rsn;
		boolean inClan;
		boolean sharing;
		String address;
		/** Registration or upload status, e.g. "Connected as Str4tty" or "Can't reach the server". */
		String status;
		boolean statusIsError;
		int queued;
		List<RecordQueue.Entry> recent;
		/** RuneLite's Chat Commands plugin, which records PB times. */
		boolean chatCommandsOn;
		/** The member has opened Adventure Log → Counters, which loads raid, Inferno and Colosseum PBs. */
		boolean countersOpened;
		/** The game's precise timing setting, which makes PB times include hundredths. */
		boolean preciseTimingOn;
		String pbStatus;
		boolean pbStatusIsError;
		/** The whole collection log has been sent at least once for this account. */
		boolean clogSynced;
		String clogStatus;
		boolean clogStatusIsError;
		/** The member's clan profile; null until loaded. */
		ClanData.Me me;
	}

	private final JPanel statusTab = PanelParts.column();
	private final JPanel eventsTab = PanelParts.column();
	private final JPanel leadersTab = PanelParts.column();
	private final JPanel newsTab = PanelParts.column();
	private final JPanel groupsTab = PanelParts.column();
	private final JPanel statsTab = PanelParts.column();
	private final StatsTabs.Actions statsActions;
	private TabBar.Tab statsButton;
	/** The panel is open on the Stats tab. Read off the Swing thread. */
	private volatile boolean statsShown;
	private StatsTabs.Input lastStats;
	private String statsPage;
	private String statsMinimum;
	private final GroupTabs.GroupForm groupForm;
	private final GroupTabs.Actions groupActions;
	private TabBar.Tab groupsButton;
	private TabBar tabBar;
	/** The panel is open on the Groups tab, so the plugin checks groups more often. Read off the Swing thread. */
	private volatile boolean groupsShown;
	private boolean panelOpen;
	private final TabBar.Tab newsButton;
	private final Runnable syncPbs;
	private final Runnable refreshClan;
	private final Runnable newsOpened;
	private final ClanTabs.Actions eventActions;
	private final Settings settings;

	/** Where the panel keeps the member's choices: the plugin's saved settings in RuneLite, or a map in tests. */
	interface Settings
	{
		String get(String key);

		void set(String key, String value);
	}
	/** The last data drawn, so the panel's own controls can redraw without waiting for a reload. */
	private ClanTabs.Input lastInput;
	/** The member's choices: Events page, open event and teams, leaderboard period and folded boards. */
	private String eventsPage;
	private String eventId;
	private final Map<String, String> teams = new HashMap<>();
	private String period;
	private final Set<String> folded = new LinkedHashSet<>();

	private static final String PERIOD_KEY = "leaderboardPeriod";
	private static final String FOLDED_KEY = "foldedLeaderboards";
	private static final String STATS_PAGE_KEY = "statsPage";
	private static final String STATS_MINIMUM_KEY = "statsMinimum";

	AsgardiansPanel(Settings settings, Runnable syncPbs, Runnable refreshClan, Runnable newsOpened,
		ClanTabs.Actions eventActions, GroupTabs.Actions groupActions, StatsTabs.Actions statsActions)
	{
		this.statsActions = statsActions;
		this.groupActions = groupActions;
		this.groupForm = new GroupTabs.GroupForm(groupActions::create);
		this.settings = settings;
		String savedPeriod = settings.get(PERIOD_KEY);
		period = ClanTabs.period(savedPeriod);
		statsPage = StatsTabs.page(settings.get(STATS_PAGE_KEY));
		statsMinimum = StatsTabs.minimum(settings.get(STATS_MINIMUM_KEY));
		String savedFolded = settings.get(FOLDED_KEY);
		if (savedFolded != null && !savedFolded.isEmpty())
		{
			folded.addAll(Arrays.asList(savedFolded.split("\n")));
		}
		this.syncPbs = syncPbs;
		this.refreshClan = refreshClan;
		this.newsOpened = newsOpened;
		this.eventActions = eventActions;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		JLabel title = new JLabel("Asgardians Clan", new ImageIcon(AsgIcon.create(16)), JLabel.LEFT);
		title.setIconTextGap(6);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		top.add(title);
		top.add(Box.createVerticalStrut(6));

		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		TabBar tabs = new TabBar(display);
		tabBar = tabs;
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		TabBar.Tab status = tabs.addTab("Status", statusTab, null);
		tabs.addTab("Events", eventsTab, null);
		groupsButton = tabs.addTab("Groups", groupsTab, groupActions::refresh);
		tabs.addTab("Leaderboard", leadersTab, null);
		statsButton = tabs.addTab("Stats", statsTab, statsActions::opened);
		newsButton = tabs.addTab("News", newsTab, newsOpened);
		tabs.onChange(this::updateGroupsShown);
		tabs.select(status);
		top.add(tabs);
		top.add(Box.createVerticalStrut(8));

		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		update(new State(null, false, false, AsgardiansConfig.DEFAULT_API_ADDRESS, null, false, 0, List.of(),
			true, false, false, null, false, false, null, false, null));
		updateClan(new ClanTabs.Input(ClanView.empty(), java.util.Map.of(), 0, "Log in to see clan events.",
			java.time.ZoneId.systemDefault(), java.time.Instant.now(), AsgardiansConfig.DEFAULT_API_ADDRESS,
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(), java.util.Map.of()));
		updateStats(new StatsTabs.Input(null, false, "Log in to see clan stats.", null, null, java.time.Instant.now(),
			java.time.ZoneId.systemDefault()));
	}

	/** Redraws the Stats tab. */
	void updateStats(StatsTabs.Input input)
	{
		lastStats = input;
		StatsTabs.Ui ui = new StatsTabs.Ui(statsPage, statsMinimum);
		keepingScroll(statsTab, () -> StatsTabs.draw(statsTab, input, ui, statsUi, statsActions));
	}

	/** The Stats tab's page and drop value buttons. */
	private final StatsTabs.UiActions statsUi = new StatsTabs.UiActions()
	{
		@Override
		public void page(String page)
		{
			statsPage = page;
			settings.set(STATS_PAGE_KEY, page);
			redrawStats();
		}

		@Override
		public void minimum(String minimum)
		{
			statsMinimum = minimum;
			settings.set(STATS_MINIMUM_KEY, minimum);
			redrawStats();
		}
	};

	private void redrawStats()
	{
		if (lastStats != null)
		{
			updateStats(lastStats);
		}
	}

	/** True while the side panel is open on the Stats tab. Safe from any thread. */
	boolean statsShown()
	{
		return statsShown;
	}

	/** Redraws the Groups tab. */
	void updateGroups(GroupTabs.Input input)
	{
		// The list is checked every few seconds. Redrawing takes the member's cursor out of the form and closes its
		// drop-downs, so only redraw when something shown has changed, and never while a drop-down is open.
		Object key = GroupTabs.redrawKey(input);
		if (key.equals(lastGroupsKey) || groupForm.isPicking())
		{
			return;
		}
		lastGroupsKey = key;
		// GroupTabs.draw leaves the "Start a group" form in place, so whatever the member is typing keeps its cursor.
		keepingScroll(groupsTab, () -> GroupTabs.draw(groupsTab, input, groupForm, groupActions));
	}

	private Object lastGroupsKey;

	/** Redraws a tab without jumping back to the top of the side panel. */
	private void keepingScroll(JPanel tab, Runnable redraw)
	{
		javax.swing.JViewport viewport = (javax.swing.JViewport) javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, tab);
		java.awt.Point at = viewport != null ? viewport.getViewPosition() : null;
		redraw.run();
		tab.revalidate();
		tab.repaint();
		if (viewport != null)
		{
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				java.awt.Component view = viewport.getView();
				int maxY = view != null ? Math.max(0, view.getHeight() - viewport.getExtentSize().height) : 0;
				viewport.setViewPosition(new java.awt.Point(at.x, Math.min(at.y, maxY)));
			});
		}
	}

	/** Clears the "Start a group" note once a group has started. */
	void groupStarted()
	{
		groupForm.reset();
	}

	/** True while the side panel is open on the Groups tab. Safe from any thread. */
	boolean groupsShown()
	{
		return groupsShown;
	}

	@Override
	public void onActivate()
	{
		panelOpen = true;
		updateGroupsShown();
	}

	@Override
	public void onDeactivate()
	{
		panelOpen = false;
		updateGroupsShown();
	}

	private void updateGroupsShown()
	{
		groupsShown = panelOpen && tabBar != null && groupsButton != null && tabBar.isSelected(groupsButton);
		statsShown = panelOpen && tabBar != null && statsButton != null && tabBar.isSelected(statsButton);
	}

	/** Redraws the Status tab. */
	void update(State state)
	{
		statusTab.removeAll();
		addCard(statusTab, connectionCard(state));
		if (state.getRsn() != null && state.isInClan() && state.isSharing())
		{
			addCard(statusTab, pbCard(state));
			addCard(statusTab, clogCard(state));
		}
		if (!state.getRecent().isEmpty())
		{
			addCard(statusTab, recentCard(state));
		}
		statusTab.revalidate();
		statusTab.repaint();
	}

	/** Redraws the Events, Leaderboard and News tabs. */
	void updateClan(ClanTabs.Input input)
	{
		lastInput = input;
		ClanTabs.Ui ui = new ClanTabs.Ui(eventsPage, eventId, new HashMap<>(teams), period, new LinkedHashSet<>(folded));
		keepingScroll(eventsTab, () -> ClanTabs.events(eventsTab, input, refreshClan, eventActions, ui, uiActions));
		keepingScroll(leadersTab, () -> ClanTabs.leaderboards(leadersTab, input, refreshClan, ui, uiActions));
		keepingScroll(newsTab, () -> ClanTabs.news(newsTab, input, refreshClan));
		int unread = input.getNotConnected() == null ? input.getView().unread(input.getLastReadAnnouncementId()) : 0;
		newsButton.setText(unread > 0 ? "News (" + unread + ")" : "News");
		eventsTab.revalidate();
		eventsTab.repaint();
		leadersTab.revalidate();
		leadersTab.repaint();
		newsTab.revalidate();
		newsTab.repaint();
	}

	/** The panel's own controls: they change what's shown, then redraw from the data already loaded. */
	private final ClanTabs.UiActions uiActions = new ClanTabs.UiActions()
	{
		@Override
		public void eventsPage(String page)
		{
			eventsPage = page;
			redraw();
		}

		@Override
		public void event(String id)
		{
			eventId = id;
			redraw();
		}

		@Override
		public void team(String event, String team)
		{
			teams.put(event, team);
			redraw();
		}

		@Override
		public void period(String value)
		{
			period = value;
			settings.set(PERIOD_KEY, value);
			redraw();
		}

		@Override
		public void fold(String title)
		{
			if (!folded.remove(title))
			{
				folded.add(title);
			}
			settings.set(FOLDED_KEY, String.join("\n", folded));
			redraw();
		}
	};

	private void redraw()
	{
		if (lastInput != null)
		{
			updateClan(lastInput);
		}
	}

	// ---- Status tab ----

	private static Card connectionCard(State state)
	{
		Card card = new Card("Connection");
		String address = state.getAddress();
		if (!ApiAddress.isDefault(address))
		{
			card.add(badge("TEST SERVER", TEST_SERVER));
			card.add(small(address.replaceFirst("^https?://", "") + (ApiAddress.fromLaunchFlag() ? " (launch flag)" : ""), QUIET));
			card.gap();
		}
		if (ApiAddress.isInsecure(address))
		{
			card.add(status(BAD, "Plain http: your install key is sent unencrypted"));
		}

		if (state.getRsn() == null)
		{
			card.add(status(QUIET, "Log in to connect"));
		}
		else
		{
			card.add(PanelParts.heading(state.getRsn()));
			if (!state.isInClan())
			{
				card.add(status(TODO, "Not in the Asgardians clan. Nothing is sent."));
			}
			else if (!state.isSharing())
			{
				card.add(status(TODO, "Sharing is off. Tick \"Share my data with Asgardians\" in the plugin settings."));
			}
			else if (state.getStatus() == null)
			{
				card.add(status(QUIET, "Connecting…"));
			}
			else
			{
				card.add(status(state.isStatusIsError() ? BAD : GOOD, state.getStatus()));
			}
		}
		if (state.getMe() != null && state.isInClan() && state.isSharing())
		{
			addProfile(card, state.getMe());
		}
		if (state.getQueued() > 0)
		{
			card.add(small(state.getQueued() + " waiting to send", QUIET));
		}
		return card;
	}

	/** The member's clan profile. Totals the website doesn't send are left out. */
	private static void addProfile(Card card, ClanData.Me me)
	{
		card.gap();
		card.addWide(statRow("Clan rank", ProfileText.rank(me.getRank())));
		if (me.getDaysInClan() != null)
		{
			card.addWide(statRow("In clan", ProfileText.time(me.getDaysInClan())));
		}
		if (me.getSplitCredit() != null)
		{
			card.addWide(statRow("Clan credit", ProfileText.amount(me.getSplitCredit())));
		}
		if (me.getDrops() != null)
		{
			String value = me.getDropValue() != null ? " (" + ProfileText.amount(me.getDropValue()) + ")" : "";
			card.addWide(statRow("Drops recorded", String.format("%,d", me.getDrops()) + value));
		}
		if (me.getClogObtained() != null)
		{
			card.addWide(statRow("Collection log", String.format("%,d", me.getClogObtained())));
		}
		if (me.getPbs() != null)
		{
			card.addWide(statRow("PBs on record", String.format("%,d", me.getPbs())));
		}
		if (me.getDeaths() != null)
		{
			card.addWide(statRow("Deaths", String.format("%,d", me.getDeaths())));
		}
		card.addWide(statRow("Discord", me.isDiscordLinked() ? "Linked" : "Not linked"));
		String discordHelp = ProfileText.discordHelp(me);
		if (discordHelp != null)
		{
			card.add(small(discordHelp, TODO));
		}
		String next = ProfileText.nextRank(me);
		if (next != null)
		{
			card.gap();
			card.add(small(next, TODO));
		}
		if (me.isHeld())
		{
			card.gap();
			card.add(status(BAD, "Your account is held for staff review."));
		}
	}

	private Card pbCard(State state)
	{
		Card card = new Card("Personal bests");
		card.add(check(state.isChatCommandsOn(), "Chat Commands plugin on",
			"Turn on RuneLite's Chat Commands plugin. It records your PB times."));
		card.add(check(state.isCountersOpened(), "Adventure Log counters read",
			"Open Adventure Log → Counters once to load raid, Inferno and Colosseum PBs."));
		card.add(check(state.isPreciseTimingOn(), "Precise timing on",
			"Turn on precise timing in the game settings so times include hundredths."));
		if (state.getPbStatus() != null)
		{
			card.gap();
			card.add(small(state.getPbStatus(), state.isPbStatusIsError() ? BAD : QUIET));
		}
		card.gap();
		card.addWide(button("Sync all PBs now", syncPbs));
		return card;
	}

	private static Card clogCard(State state)
	{
		Card card = new Card("Collection log");
		card.add(check(state.isClogSynced(), "Whole log synced",
			"Open your collection log once to send every slot."));
		if (state.getClogStatus() != null)
		{
			card.add(small(state.getClogStatus(), state.isClogStatusIsError() ? BAD : QUIET));
		}
		if (state.isClogSynced())
		{
			card.gap();
			card.add(small("New slots are sent as you get them, and the whole log is refreshed whenever you open it.", QUIET));
		}
		return card;
	}

	private static Card recentCard(State state)
	{
		Card card = new Card("Recent submissions");
		SimpleDateFormat time = new SimpleDateFormat("HH:mm");
		for (RecordQueue.Entry entry : state.getRecent())
		{
			Record record = entry.record;
			Color colour;
			String tip;
			switch (entry.status)
			{
				case SENT:
					colour = GOOD;
					tip = "Sent";
					break;
				case FAILED:
					colour = TODO;
					tip = "Not sent yet, will retry";
					break;
				case REJECTED:
					colour = BAD;
					tip = "Rejected by the server" + (entry.reason != null ? ": " + entry.reason : "");
					break;
				default:
					colour = QUIET;
					tip = "Waiting to send";
			}
			String right = time.format(new Date(record.createdMillis));
			if (record.totalValue > 0)
			{
				right = QuantityFormatter.quantityToStackSize(record.totalValue) + "  " + right;
			}
			card.addWide(dotRow(colour, record.describe(), right, record.describe() + " — " + tip));
		}
		card.gap();
		JLabel legend = new JLabel(wrap(dot(GOOD) + " sent &nbsp;" + dot(QUIET) + " waiting &nbsp;"
			+ dot(TODO) + " retrying &nbsp;" + dot(BAD) + " rejected (hover for why)"));
		legend.setFont(FontManager.getRunescapeSmallFont());
		legend.setForeground(QUIET);
		card.add(legend);
		return card;
	}
}
