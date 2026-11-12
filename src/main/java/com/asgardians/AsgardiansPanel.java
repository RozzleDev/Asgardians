package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
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
import static com.asgardians.PanelParts.status;
import static com.asgardians.PanelParts.wrap;
import java.awt.BorderLayout;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * The side panel. It opens on Home, a grid of tiles that each open a page: Events, Bingo (the running event's board,
 * tasks, scores and team), Groups, Members (who's online and what they're doing), Drops, Leaderboard, Profile, News and
 * PBs. The header has a back arrow and a cog for the settings page; the line at the bottom shows whether the plugin is
 * connected. Swing thread only.
 */
class AsgardiansPanel extends PluginPanel
{
	static final String HOME = "home";
	static final String EVENTS = "events";
	static final String BINGO = "bingo";
	static final String GROUPS = "groups";
	static final String MEMBERS = "members";
	static final String DROPS = "drops";
	static final String LEADERBOARD = "leaderboard";
	static final String PROFILE = "profile";
	/** A clanmate's profile. */
	static final String MEMBER = "member";
	static final String NEWS = "news";
	static final String PBS = "pbs";
	static final String SETTINGS = "settings";
	static final String STAFF = "staff";
	/** One event's details. */
	static final String EVENT = "event";

	/** Everything the Profile page's own sections show, captured off the Swing thread. */
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

	/** Where the panel keeps the member's choices: the plugin's saved settings in RuneLite, or a map in tests. */
	interface Settings
	{
		String get(String key);

		void set(String key, String value);
	}

	private final JPanel homeTab = PanelParts.column();
	private final JPanel statusTab = PanelParts.column();
	private final JPanel memberTab = PanelParts.column();
	private final JPanel eventsTab = PanelParts.column();
	private final JPanel bingoTab = PanelParts.column();
	private final JPanel leadersTab = PanelParts.column();
	private final JPanel newsTab = PanelParts.column();
	private final JPanel groupsTab = PanelParts.column();
	private final JPanel membersTab = PanelParts.column();
	/** The Members page below its search box, which stays put so typing isn't interrupted. */
	private final JPanel membersList = PanelParts.column();
	private final javax.swing.JTextField memberSearch = new javax.swing.JTextField();
	private final JPanel statsTab = PanelParts.column();
	private final JPanel pbsTab = PanelParts.column();
	private final JPanel settingsTab = PanelParts.column();
	private final JPanel eventTab = PanelParts.column();
	/** Staff notes: the general notes page, and the notes under a clanmate's profile. Each list sits above a form
	 * that isn't redrawn, so a note being typed survives refreshes. */
	private final JPanel staffTab = PanelParts.column();
	private final JPanel staffList = PanelParts.column();
	private final JPanel memberProfileColumn = PanelParts.column();
	private final JPanel memberNotesHolder = PanelParts.column();
	private final JPanel memberNotesList = PanelParts.column();
	private final JPanel staffHomeRow = PanelParts.column();
	private StaffTabs.Form staffForm;
	private StaffTabs.MotdForm motdForm;
	private StaffTabs.NewsForm newsForm;
	/** The message of the day on Home. */
	private final JPanel motdRow = PanelParts.column();
	/** Staff note actions from the panel's own controls: deleting asks first. */
	private StaffTabs.Actions staffUi;
	private StaffTabs.Form memberNotesForm;
	private StaffTabs.Actions staffActions = new StaffTabs.Actions()
	{
		@Override
		public void add(String rsn, String text)
		{
		}

		@Override
		public void delete(String rsn, ClanData.StaffNote note)
		{
		}
	};
	/** Loads staff notes: about a member (rsn), or the general ones (null). */
	private java.util.function.Consumer<String> staffLoader = rsn -> { };
	private StaffTabs.Input staffNotes = new StaffTabs.Input(null, true, null, java.time.Instant.now());
	private String memberNotesRsn;
	private StaffTabs.Input memberNotes = new StaffTabs.Input(null, true, null, java.time.Instant.now());
	/** The event whose details are open. */
	private String detailsEventId;
	private final Map<String, JPanel> pages = new HashMap<>();
	/** Holds the page on show, inside the scroll pane. */
	private final JPanel view = new ScrollColumn();
	private final javax.swing.JScrollPane scroll;
	private final PanelChrome.Header header;
	private final PanelChrome.Footer footer;
	private final Map<String, PanelChrome.Tile> tiles = new HashMap<>();
	/** The "Join voice" button on Home, shown while the member's group has a Discord channel. */
	private final JPanel voiceRow = PanelParts.column();
	private String page = HOME;
	/** The pages the member came through, newest last, for the back arrow. */
	private final java.util.Deque<String> history = new java.util.ArrayDeque<>();
	/** Announcements opened to their full text. */
	private final Set<Long> openNews = new java.util.HashSet<>();
	/** The longest back trail kept. */
	private static final int HISTORY = 30;
	private final StatsTabs.Actions statsActions;
	/** The panel is open on Drops or PBs. Read off the Swing thread. */
	private volatile boolean statsShown;
	/** The panel is open on Members. Read off the Swing thread. */
	private volatile boolean membersShown;
	private StatsTabs.Input lastStats;
	private String statsPage;
	private String statsMinimum;
	private final GroupTabs.GroupForm groupForm;
	private final GroupTabs.Actions groupActions;
	private GroupTabs.Input lastGroups;
	private MembersTabs.Input lastMembers;
	/** The panel is open on Groups, so the plugin checks groups more often. Read off the Swing thread. */
	private volatile boolean groupsShown;
	private boolean panelOpen;
	private State lastState;
	private int unread;
	private final Runnable syncPbs;
	private final Runnable refreshClan;
	private final Runnable newsOpened;
	private final ClanTabs.Actions eventActions;
	private final Settings settings;
	/** Asks the plugin for a clanmate's profile; the answer comes back through {@link #updateProfile}. */
	private java.util.function.Consumer<String> profileLoader = rsn -> { };
	/** Reloads what clanmates are doing. */
	private Runnable membersRefresh = () -> { };
	/** The clanmate whose profile is open, and what the website sent for them. */
	private String memberRsn;
	private ClanData.Me memberProfile;
	private boolean memberLoading;
	private boolean memberUnsupported;

	/** The last data drawn, so the panel's own controls can redraw without waiting for a reload. */
	private ClanTabs.Input lastInput;
	/** The member's choices. */
	private String eventsPage;
	private String eventId;
	private final Map<String, String> teams = new HashMap<>();
	private String period;
	private final Set<String> folded = new LinkedHashSet<>();
	private String bingoView;
	private boolean hideDone;
	private String tileId;
	private String board;
	private String memberFilter;
	private boolean showOffline;
	private String pbCategory;
	private String pbGroup;
	/** The Boss kills board's pick: category (null for all bosses), group and boss. */
	private final String[] kcPick = new String[3];
	/** The XP board's skill, or null for all skills. */
	private String xpSkill;
	private String pbBoss;
	private String pbSize;

	private static final String PERIOD_KEY = "leaderboardPeriod";
	private static final String FOLDED_KEY = "foldedLeaderboards";
	private static final String STATS_PAGE_KEY = "statsPage";
	private static final String STATS_MINIMUM_KEY = "statsMinimum";
	private static final String BOARD_KEY = "leaderboardBoard";
	private static final String HIDE_DONE_KEY = "bingoHideDone";
	private static final String MEMBER_FILTER_KEY = "membersFilter";
	private static final String SHOW_OFFLINE_KEY = "membersShowOffline";

	AsgardiansPanel(Settings settings, Runnable syncPbs, Runnable refreshClan, Runnable newsOpened,
		ClanTabs.Actions eventActions, GroupTabs.Actions groupActions, StatsTabs.Actions statsActions)
	{
		// The panel scrolls its own pages, so the header and the status line stay put.
		super(false);
		this.statsActions = statsActions;
		PanelParts.setBossIcons(statsActions::bossIcon);
		this.groupActions = groupActions;
		this.groupForm = new GroupTabs.GroupForm(groupActions::create);
		this.settings = settings;
		period = ClanTabs.period(settings.get(PERIOD_KEY));
		statsPage = StatsTabs.page(settings.get(STATS_PAGE_KEY));
		statsMinimum = StatsTabs.minimum(settings.get(STATS_MINIMUM_KEY));
		board = settings.get(BOARD_KEY);
		String savedHide = settings.get(HIDE_DONE_KEY);
		hideDone = savedHide == null || Boolean.parseBoolean(savedHide.trim());
		memberFilter = MembersTabs.filter(settings.get(MEMBER_FILTER_KEY));
		showOffline = "true".equals(settings.get(SHOW_OFFLINE_KEY));
		String savedFolded = settings.get(FOLDED_KEY);
		if (savedFolded != null && !savedFolded.isEmpty())
		{
			folded.addAll(Arrays.asList(savedFolded.split("\n")));
		}
		this.syncPbs = syncPbs;
		this.refreshClan = refreshClan;
		this.newsOpened = newsOpened;
		this.eventActions = eventActions;
		setLayout(new BorderLayout(0, 8));
		setBackground(PanelParts.BACKGROUND);
		setBorder(new EmptyBorder(10, 10, 6, 10));

		pages.put(HOME, homeTab);
		pages.put(EVENTS, eventsTab);
		pages.put(BINGO, bingoTab);
		pages.put(GROUPS, groupsTab);
		pages.put(MEMBERS, membersTab);
		buildMemberSearch();
		pages.put(DROPS, statsTab);
		pages.put(LEADERBOARD, leadersTab);
		pages.put(PROFILE, statusTab);
		pages.put(MEMBER, memberTab);
		pages.put(NEWS, newsTab);
		pages.put(PBS, pbsTab);
		pages.put(SETTINGS, settingsTab);
		pages.put(EVENT, eventTab);
		pages.put(STAFF, staffTab);
		buildStaff();

		header = new PanelChrome.Header(this::refreshPage, () ->
		{
			if (SETTINGS.equals(page))
			{
				back();
			}
			else
			{
				show(SETTINGS);
			}
		}, () -> show(HOME));
		add(header, BorderLayout.NORTH);

		view.setLayout(new BorderLayout());
		view.setOpaque(false);
		scroll = new javax.swing.JScrollPane(view, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(6, 0));
		add(scroll, BorderLayout.CENTER);

		footer = new PanelChrome.Footer(() -> show(PROFILE));
		add(footer, BorderLayout.SOUTH);

		buildHome();
		show(HOME);

		update(new State(null, false, false, AsgardiansConfig.DEFAULT_API_ADDRESS, null, false, 0, List.of(),
			true, false, false, null, false, false, null, false, null));
		updateClan(new ClanTabs.Input(ClanView.empty(), java.util.Map.of(), 0, "Log in to see clan events.",
			java.time.ZoneId.systemDefault(), java.time.Instant.now(), AsgardiansConfig.DEFAULT_API_ADDRESS,
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(), java.util.Map.of()));
		updateStats(new StatsTabs.Input(null, false, "Log in to see clan stats.", null, null, java.time.Instant.now(),
			java.time.ZoneId.systemDefault()));
		updateMembers(new MembersTabs.Input(List.of(), null, null, null, true, false, "Log in to see who's online."));
	}

	/** How the panel asks for a clanmate's profile. */
	void setProfileLoader(java.util.function.Consumer<String> loader)
	{
		profileLoader = loader != null ? loader : rsn -> { };
	}

	/** How the panel loads and changes staff notes. */
	void setStaffActions(java.util.function.Consumer<String> loader, StaffTabs.Actions actions)
	{
		staffLoader = loader;
		staffActions = actions;
	}

	/** True when the website says the member is clan staff. */
	boolean isStaff()
	{
		return lastState != null && lastState.getMe() != null && Boolean.TRUE.equals(lastState.getMe().getStaff());
	}

	/** The clan's message of the day changed (null or empty text: none). Shown on Home. */
	void updateMotd(ClanData.Motd motd)
	{
		motdRow.removeAll();
		String text = motd != null && motd.getText() != null && !motd.getText().isBlank() ? motd.getText().trim() : null;
		if (text != null)
		{
			Card card = new Card(null).stripe(HIGHLIGHT);
			card.add(PanelParts.plain("MESSAGE OF THE DAY", HIGHLIGHT));
			card.add(small(text, PanelParts.TEXT));
			if (motd.getAuthor() != null)
			{
				card.add(small("- " + motd.getAuthor(), QUIET));
			}
			card.setAlignmentX(LEFT_ALIGNMENT);
			motdRow.add(card);
			motdRow.add(Box.createVerticalStrut(6));
		}
		motdRow.setVisible(text != null);
		if (motdForm != null)
		{
			motdForm.current(text);
		}
		motdRow.revalidate();
		motdRow.repaint();
	}

	/** How a staff change went: "motd" or "news". */
	void staffResult(String which, String message, boolean error)
	{
		if ("motd".equals(which) && motdForm != null)
		{
			motdForm.status(message, error);
		}
		else if ("news".equals(which) && newsForm != null)
		{
			newsForm.status(message, error);
			if (!error)
			{
				newsForm.clearText();
			}
		}
	}

	/** Staff notes arrived: about a member (rsn), or the general ones (null). */
	void updateStaffNotes(String rsn, StaffTabs.Input input)
	{
		if (rsn == null)
		{
			staffNotes = input;
			keepingScroll(staffList, () -> StaffTabs.drawNotes(staffList, null, staffNotes, staffUi));
		}
		else if (DropRecorder.sameRsn(rsn, memberNotesRsn))
		{
			memberNotes = input;
			keepingScroll(memberNotesList, () -> StaffTabs.drawNotes(memberNotesList, memberNotesRsn, memberNotes, staffUi));
		}
	}

	private void buildStaff()
	{
		StaffTabs.Actions forward = staffUi = new StaffTabs.Actions()
		{
			@Override
			public void add(String rsn, String text)
			{
				staffActions.add(rsn, text);
			}

			@Override
			public void delete(String rsn, ClanData.StaffNote note)
			{
				int answer = javax.swing.JOptionPane.showConfirmDialog(AsgardiansPanel.this,
					"Delete this note for everyone? It can't be undone.", "Delete note", javax.swing.JOptionPane.YES_NO_OPTION);
				if (answer == javax.swing.JOptionPane.YES_OPTION)
				{
					staffActions.delete(rsn, note);
				}
			}
		};
		staffForm = new StaffTabs.Form("Only clan staff see these, here and on the website. E.g. \"Holding 100m for Saturday's drop party\".", forward);
		memberNotesForm = new StaffTabs.Form("Only clan staff see notes about members, here and on the website.", forward);
		StaffTabs.drawNotes(staffList, null, staffNotes, forward);
		motdForm = new StaffTabs.MotdForm(new StaffTabs.Actions()
		{
			@Override
			public void add(String rsn, String text)
			{
			}

			@Override
			public void delete(String rsn, ClanData.StaffNote note)
			{
			}

			@Override
			public void setMotd(String text)
			{
				staffActions.setMotd(text);
			}
		});
		newsForm = new StaffTabs.NewsForm(new StaffTabs.Actions()
		{
			@Override
			public void add(String rsn, String text)
			{
			}

			@Override
			public void delete(String rsn, ClanData.StaffNote note)
			{
			}

			@Override
			public void postNews(String title, String body)
			{
				staffActions.postNews(title, body);
			}
		});
		Card motdCard = new Card("Message of the day");
		motdCard.add(small("Every member sees it at login and on Home.", QUIET));
		motdCard.gap();
		motdCard.add(motdForm);
		addCard(staffTab, motdCard);
		Card newsCard = new Card("Post to News");
		newsCard.add(small("Shows on the News page and in chat, like posts made on the website.", QUIET));
		newsCard.gap();
		newsCard.add(newsForm);
		addCard(staffTab, newsCard);
		addCard(staffTab, StaffTabs.card("Staff notes", staffList, staffForm));
		memberTab.add(memberProfileColumn);
		memberProfileColumn.setAlignmentX(LEFT_ALIGNMENT);
		memberNotesHolder.setAlignmentX(LEFT_ALIGNMENT);
		memberNotesHolder.add(Box.createVerticalStrut(PanelParts.GAP));
		memberTab.add(memberNotesHolder);
		addCard(memberNotesHolder, StaffTabs.card("Staff notes", memberNotesList, memberNotesForm));
		memberNotesHolder.setVisible(false);
	}

	void setMembersRefresh(Runnable refresh)
	{
		membersRefresh = refresh != null ? refresh : () -> { };
	}

	// ---- navigation ----

	/** Opens a page, or Home. The back arrow then walks back through every page opened since Home. */
	void show(String name)
	{
		String next = pages.containsKey(name) ? name : HOME;
		if (next.equals(HOME))
		{
			history.clear();
		}
		else if (!next.equals(page))
		{
			history.addLast(here());
			while (history.size() > HISTORY)
			{
				history.removeFirst();
			}
		}
		open(next);
	}

	/** Goes back to the page before this one (Home when there's none). */
	void back()
	{
		open(history.isEmpty() ? HOME : history.removeLast());
	}

	/** Where the member is now, as the back trail remembers it: a clanmate's profile keeps their name. */
	private String here()
	{
		if (EVENT.equals(page) && detailsEventId != null)
		{
			return EVENT + ":" + detailsEventId;
		}
		return MEMBER.equals(page) && memberRsn != null ? MEMBER + ":" + memberRsn : page;
	}

	private void open(String next)
	{
		if (next.startsWith(EVENT + ":"))
		{
			detailsEventId = next.substring(EVENT.length() + 1);
			drawEventDetails();
			next = EVENT;
		}
		if (next.startsWith(MEMBER + ":"))
		{
			String rsn = next.substring(MEMBER.length() + 1);
			if (!DropRecorder.sameRsn(rsn, memberRsn))
			{
				memberRsn = rsn;
				memberProfile = null;
				memberLoading = true;
				memberUnsupported = false;
				profileLoader.accept(rsn);
			}
			drawMemberProfile();
			next = MEMBER;
		}
		page = next;
		if (SETTINGS.equals(page))
		{
			drawSettings();
		}
		JPanel target = pages.get(page);
		view.removeAll();
		view.add(target, BorderLayout.NORTH);
		view.revalidate();
		view.repaint();
		javax.swing.SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new java.awt.Point(0, 0)));
		updateHeader();
		updateShown();
		switch (page)
		{
			case GROUPS:
				groupActions.refresh();
				break;
			case DROPS:
			case PBS:
				statsActions.opened();
				break;
			case NEWS:
				newsOpened.run();
				break;
			case MEMBERS:
				membersRefresh.run();
				break;
			case STAFF:
				staffLoader.accept(null);
				break;
			default:
		}
	}

	/** The page on show. */
	String page()
	{
		return page;
	}

	/** Opens a clanmate's profile (or the member's own), loading it from the website. */
	void openProfile(String rsn)
	{
		if (rsn == null)
		{
			return;
		}
		if (lastState != null && DropRecorder.sameRsn(rsn, lastState.getRsn()))
		{
			show(PROFILE);
			return;
		}
		if (MEMBER.equals(page) && memberRsn != null && !DropRecorder.sameRsn(rsn, memberRsn))
		{
			// From one clanmate's profile to another: back returns to the first.
			history.addLast(here());
		}
		if (!DropRecorder.sameRsn(rsn, memberRsn) || memberProfile == null)
		{
			memberRsn = rsn;
			memberProfile = null;
			memberLoading = true;
			memberUnsupported = false;
			profileLoader.accept(rsn);
		}
		drawMemberProfile();
		show(MEMBER);
	}

	/** A clanmate's profile arrived (or didn't: null, with unsupported when the website has none). */
	void updateProfile(String rsn, ClanData.Me profile, boolean unsupported)
	{
		if (!DropRecorder.sameRsn(rsn, memberRsn))
		{
			return;
		}
		memberProfile = profile;
		memberLoading = false;
		memberUnsupported = unsupported;
		drawMemberProfile();
		updateHeader();
	}

	/** Reloads what the page on show comes from. */
	private void refreshPage()
	{
		switch (page)
		{
			case GROUPS:
				groupActions.refresh();
				break;
			case DROPS:
			case PBS:
				statsActions.refresh();
				break;
			case MEMBERS:
				membersRefresh.run();
				break;
			case MEMBER:
				if (memberRsn != null)
				{
					memberLoading = memberProfile == null;
					profileLoader.accept(memberRsn);
					if (isStaff())
					{
						staffLoader.accept(memberRsn);
					}
				}
				break;
			case STAFF:
				staffLoader.accept(null);
				break;
			case HOME:
				refreshClan.run();
				statsActions.refresh();
				groupActions.refresh();
				membersRefresh.run();
				break;
			default:
				refreshClan.run();
		}
	}

	private void updateHeader()
	{
		if (page.equals(HOME))
		{
			header.home("Clan hub");
			return;
		}
		String title;
		String sub;
		switch (page)
		{
			case EVENTS:
				title = "Events";
				sub = tiles.get(EVENTS).line();
				break;
			case BINGO:
				ClanView.EventView ev = lastInput != null ? ClanTabs.bingoEvent(lastInput.getView(), eventId) : null;
				title = ev != null && ev.getEvent().getName() != null ? ev.getEvent().getName() : "Bingo";
				sub = ev != null && ev.getEvent().end() != null && lastInput != null
					? "Ends in " + ClanView.countdown(lastInput.getNow(), ev.getEvent().end()) : "No bingo running";
				break;
			case GROUPS:
				title = "Groups";
				sub = tiles.get(GROUPS).line();
				break;
			case MEMBERS:
				title = "Members";
				sub = lastMembers != null && lastMembers.getNotConnected() == null
					? MembersTabs.online(lastMembers) + " online" + offlineCount() : " ";
				break;
			case DROPS:
				title = "Drops";
				sub = "The clan's latest drops";
				break;
			case LEADERBOARD:
				title = "Leaderboard";
				sub = ClanTabs.periodLabel(period);
				break;
			case PROFILE:
				title = lastState != null && lastState.getRsn() != null ? lastState.getRsn() : "Profile";
				sub = "Your clan profile";
				break;
			case MEMBER:
				title = memberRsn != null ? memberRsn : "Profile";
				sub = "Clan profile";
				break;
			case NEWS:
				title = "News";
				sub = unread > 0 ? unread + " new" : "Clan announcements";
				break;
			case EVENT:
				ClanData.Event shown = detailsEvent();
				title = shown != null && shown.getName() != null ? shown.getName() : "Event";
				sub = shown != null ? ClanTabs.typeTag(shown) + (shown.isLive() ? " · live" : shown.isUpcoming() ? " · coming up" : "") : " ";
				break;
			case SETTINGS:
				title = "Settings";
				sub = "Same as in RuneLite";
				break;
			case STAFF:
				title = "Staff tools";
				sub = "Staff only";
				break;
			default:
				title = "Personal bests";
				sub = "Clan records";
		}
		header.page(title, sub, this::back);
	}

	private String offlineCount()
	{
		int offline = lastMembers.getMembers().size() - MembersTabs.online(lastMembers);
		return offline > 0 ? " · " + offline + " offline" : "";
	}

	private void buildHome()
	{
		motdRow.setAlignmentX(LEFT_ALIGNMENT);
		motdRow.setVisible(false);
		homeTab.add(motdRow);
		JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
		grid.setOpaque(false);
		addTile(grid, EVENTS, PanelChrome.Icon.EVENTS, "Events");
		addTile(grid, BINGO, PanelChrome.Icon.BINGO, "Bingo");
		addTile(grid, GROUPS, PanelChrome.Icon.GROUPS, "Groups");
		addTile(grid, MEMBERS, PanelChrome.Icon.MEMBERS, "Members");
		addTile(grid, DROPS, PanelChrome.Icon.DROPS, "Drops");
		addTile(grid, LEADERBOARD, PanelChrome.Icon.LEADERBOARD, "Leaderboard");
		addTile(grid, PROFILE, PanelChrome.Icon.PROFILE, "Profile");
		addTile(grid, NEWS, PanelChrome.Icon.NEWS, "News");
		grid.setAlignmentX(LEFT_ALIGNMENT);
		homeTab.add(grid);
		homeTab.add(Box.createVerticalStrut(6));
		// The odd one out goes across the width.
		JPanel wide = new JPanel(new java.awt.GridLayout(1, 1));
		wide.setOpaque(false);
		addTile(wide, PBS, PanelChrome.Icon.PBS, "PBs");
		wide.setAlignmentX(LEFT_ALIGNMENT);
		homeTab.add(wide);
		voiceRow.setAlignmentX(LEFT_ALIGNMENT);
		homeTab.add(voiceRow);
		staffHomeRow.setAlignmentX(LEFT_ALIGNMENT);
		homeTab.add(staffHomeRow);
		JPanel staffButton = new JPanel(new BorderLayout());
		staffButton.setOpaque(false);
		staffButton.add(button("Staff tools", () -> show(STAFF)), BorderLayout.CENTER);
		staffHomeRow.add(Box.createVerticalStrut(6));
		staffHomeRow.add(staffButton);
		staffHomeRow.setVisible(false);
	}

	private void addTile(JPanel parent, String name, PanelChrome.Icon icon, String label)
	{
		PanelChrome.Tile tile = new PanelChrome.Tile(icon, label, () -> show(name));
		tiles.put(name, tile);
		parent.add(tile);
	}

	/** The live lines on the Home tiles, from whatever has loaded. */
	private void updateTiles()
	{
		boolean clan = lastInput != null && lastInput.getNotConnected() == null;
		if (clan)
		{
			int live = lastInput.getView().live().size();
			int soon = lastInput.getView().upcoming().size();
			tiles.get(EVENTS).setLine(live > 0 ? live + " live" + (soon > 0 ? ", " + soon + " soon" : "")
				: soon > 0 ? soon + " upcoming" : "Nothing on", live > 0 ? GOOD : QUIET);
			ClanTabs.Rank rank = ClanTabs.myRank(lastInput, period);
			tiles.get(LEADERBOARD).setLine(rank != null ? "You're #" + rank.getRank() + " · " + rank.getBoard()
				: ClanTabs.periodLabel(period), QUIET);
			tiles.get(NEWS).setLine(unread > 0 ? unread + " new" : "Up to date", unread > 0 ? HIGHLIGHT : QUIET);
			ClanView.EventView bingo = ClanTabs.bingoEvent(lastInput.getView(), eventId);
			if (bingo != null)
			{
				ClanData.Team team = bingo.getTeam();
				Integer place = null;
				for (ClanData.Team t : bingo.teams())
				{
					if (team != null && t.getId() != null && t.getId().equals(team.getId()))
					{
						place = t.getRank();
					}
				}
				tiles.get(BINGO).setLine(team != null ? team.getName() + (place != null ? " #" + place : "")
					: bingo.getEvent().getName(), HIGHLIGHT);
			}
			else
			{
				ClanView.EventView next = null;
				for (ClanView.EventView v : lastInput.getView().upcoming())
				{
					if (next == null && ("bingo".equals(ClanView.group(v.getEvent())) || v.getEvent().isHasBoard()))
					{
						next = v;
					}
				}
				tiles.get(BINGO).setLine(next != null && next.getEvent().start() != null
					? "Next in " + ClanView.countdown(lastInput.getNow(), next.getEvent().start()) : "None running", QUIET);
			}
		}
		else
		{
			for (String t : new String[]{EVENTS, LEADERBOARD, NEWS, BINGO})
			{
				tiles.get(t).setLine(" ", QUIET);
			}
		}
		// Groups
		ClanData.GroupList list = lastGroups != null ? lastGroups.getGroups() : null;
		if (list != null)
		{
			ClanData.Group mine = list.getGroups() != null ? list.mineGroup() : null;
			int open = list.getGroups() != null ? list.getGroups().size() : 0;
			ClanData.GroupActivity activity = mine != null ? GroupTabs.activity(mine, lastGroups.getActivities()) : null;
			tiles.get(GROUPS).setLine(mine != null ? "In: " + Groups.shortTitle(mine, activity)
				: open == 0 ? "None open" : open + " open", mine != null ? HIGHLIGHT : open > 0 ? GOOD : QUIET);
		}
		else
		{
			tiles.get(GROUPS).setLine(" ", QUIET);
		}
		// Members
		if (lastMembers != null && lastMembers.getNotConnected() == null)
		{
			int online = MembersTabs.online(lastMembers);
			tiles.get(MEMBERS).setLine(online + " online", online > 0 ? GOOD : QUIET);
		}
		else
		{
			tiles.get(MEMBERS).setLine(" ", QUIET);
		}
		// Drops and PBs
		ClanData.StatsFeed feed = lastStats != null ? lastStats.getFeed() : null;
		ClanData.StatEntry drop = feed != null && feed.getDrops() != null && !feed.getDrops().isEmpty() ? feed.getDrops().get(0) : null;
		java.time.Instant now = lastStats != null ? lastStats.getNow() : java.time.Instant.now();
		String ago = drop != null ? ClanView.ago(drop.time(), now).replace(" ago", "") : "";
		tiles.get(DROPS).setLine(drop != null && drop.getItem() != null
			? drop.getItem() + (ago.isEmpty() ? "" : " · " + ago) : " ", QUIET);
		tiles.get(PBS).setLine(pbLine(feed), QUIET);
		// Profile
		State st = lastState;
		if (st != null && st.getRsn() != null)
		{
			String rank = st.getMe() != null && st.getMe().getRank() != null ? " · " + ProfileText.rank(st.getMe().getRank()) : "";
			tiles.get(PROFILE).setLine(st.getRsn() + rank, QUIET);
		}
		else
		{
			tiles.get(PROFILE).setLine("Log in", QUIET);
		}
		updateVoice();
		updateHeader();
	}

	/** Where the Home tiles' OSRS pictures come from: RuneLite's copy of the game's items and sprites. */
	interface GamePictures
	{
		/** An item's picture; {@code quantity} picks the stack picture, e.g. a pile of coins. Swing thread. */
		void item(int itemId, int quantity, java.util.function.Consumer<java.awt.image.BufferedImage> done);

		/** One of the game's sprites. Swing thread. */
		void sprite(int spriteId, java.util.function.Consumer<java.awt.image.BufferedImage> done);
	}

	/** The game's Quests tab icon (the blue quest point star). */
	static final int SPRITE_QUESTS = 776;
	/** The game's Skills (stats) tab icon. */
	static final int SPRITE_STATS = 898;
	/** The game's Clan tab icon. */
	static final int SPRITE_CLAN = 2307;

	/**
	 * Gives the Home tiles their OSRS pictures: the Quests, Clan and Skills tab icons from the game, coins, the
	 * newspaper and the gold speedrun trophy as items, and the Grid Master, Grouping and Account icons, which RuneLite
	 * has no IDs for, from the plugin's own small copies.
	 */
	void loadTilePictures(GamePictures pictures)
	{
		pictures.sprite(SPRITE_QUESTS, img -> setTilePicture(EVENTS, img));
		pictures.sprite(SPRITE_CLAN, img -> setTilePicture(MEMBERS, img));
		pictures.sprite(SPRITE_STATS, img -> setTilePicture(LEADERBOARD, img));
		pictures.item(net.runelite.api.gameval.ItemID.COINS, 10000, img -> setTilePicture(DROPS, img));
		pictures.item(NEWSPAPER, 1, img -> setTilePicture(NEWS, img));
		pictures.item(GOLD_SPEEDRUN_TROPHY, 1, img -> setTilePicture(PBS, img));
		setTilePicture(BINGO, resource("tile_bingo.png"));
		setTilePicture(GROUPS, resource("tile_groups.png"));
		setTilePicture(PROFILE, resource("tile_profile.png"));
	}

	/** The newspaper item. */
	static final int NEWSPAPER = 11171;
	/** The gold speedrun trophy item. */
	static final int GOLD_SPEEDRUN_TROPHY = 27422;

	static java.awt.image.BufferedImage resource(String name)
	{
		try
		{
			return net.runelite.client.util.ImageUtil.loadImageResource(AsgardiansPanel.class, name);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Puts an OSRS picture on a Home tile in place of its line icon. */
	void setTilePicture(String page, java.awt.image.BufferedImage picture)
	{
		PanelChrome.Tile tile = tiles.get(page);
		if (tile != null)
		{
			tile.setPicture(picture);
		}
	}

	/** The PBs tile: a clan record when the website sends them, else the newest PB. */
	private static String pbLine(ClanData.StatsFeed feed)
	{
		if (feed == null)
		{
			return " ";
		}
		if (feed.getRecords() != null)
		{
			for (ClanData.PbRecord r : feed.getRecords())
			{
				if (r != null && r.getBoss() != null && r.getEntries() != null && !r.getEntries().isEmpty() && r.getEntries().get(0) != null
					&& r.getEntries().get(0).getSeconds() != null)
				{
					return "Record: " + r.getBoss() + " " + StatsTabs.time(r.getEntries().get(0).getSeconds());
				}
			}
		}
		ClanData.StatEntry pb = feed.getPbs() != null && !feed.getPbs().isEmpty() ? feed.getPbs().get(0) : null;
		return pb != null ? (pb.getRsn() != null ? pb.getRsn() + ": " : "") + (pb.getBoss() != null ? pb.getBoss() : "PB") + " "
			+ StatsTabs.right(pb, StatsTabs.PBS) : " ";
	}

	/** Home's "Join voice" button, while the member's group has a Discord voice channel. */
	private void updateVoice()
	{
		ClanData.GroupList list = lastGroups != null ? lastGroups.getGroups() : null;
		ClanData.Group mine = list != null && list.getGroups() != null ? list.mineGroup() : null;
		voiceRow.removeAll();
		if (mine != null && Groups.voiceLink(mine) != null)
		{
			voiceRow.add(Box.createVerticalStrut(8));
			javax.swing.JButton join = PanelParts.button("Join voice" + (mine.getParty() != null ? " · " + mine.getParty() : ""), true,
				() -> groupActions.joinVoice(mine));
			join.setToolTipText("Open your group's Discord voice channel");
			join.setAlignmentX(LEFT_ALIGNMENT);
			join.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, join.getPreferredSize().height));
			voiceRow.add(join);
		}
		voiceRow.revalidate();
		voiceRow.repaint();
	}

	/** The card at the bottom: who's logged in, their rank and world, and whether the plugin is connected. */
	private void updateFooter(State state)
	{
		String rsn = state.getRsn();
		Integer world = lastMembers != null ? lastMembers.getMyWorld() : null;
		String where = world != null ? " · W" + world : "";
		ClanData.Me me = state.getMe();
		String rank = me != null && me.getRank() != null ? ProfileText.rank(me.getRank()) : null;
		String activity = lastMembers != null && lastMembers.isSharing() ? lastMembers.getMyActivity() : null;
		Integer picture = ActivityTracker.icon(activity);
		if (picture == null && me != null && me.getPets() != null && !me.getPets().isEmpty())
		{
			picture = me.getPets().get(0).getItemId();
		}
		footer.who(rsn, rsn != null ? rank : null, rsn != null ? picture : null);
		if (rsn == null)
		{
			footer.set(QUIET, "Log in to connect", "Log in to connect");
		}
		else if (!state.isInClan())
		{
			footer.set(TODO, "Not in the clan" + where, "Not in the Asgardians clan. Nothing is sent.");
		}
		else if (!state.isSharing())
		{
			footer.set(TODO, "Sharing off" + where, "Turn on \"Share my data with Asgardians\" in Settings.");
		}
		else if (state.getStatus() == null)
		{
			footer.set(QUIET, "Connecting…" + where, "Connecting to the clan website");
		}
		else if (state.isStatusIsError())
		{
			footer.set(BAD, "Not connected" + where, state.getStatus());
		}
		else
		{
			footer.set(GOOD, "Connected" + where, state.getStatus());
		}
	}

	// ---- updates from the plugin ----

	/** Redraws the Drops and PBs pages. */
	void updateStats(StatsTabs.Input input)
	{
		lastStats = input;
		String dropsPage = StatsTabs.PBS.equals(statsPage) ? StatsTabs.DROPS : statsPage;
		keepingScroll(statsTab, () -> StatsTabs.draw(statsTab, input, new StatsTabs.Ui(dropsPage, statsMinimum), statsUi, statsActions));
		drawPbs();
		updateTiles();
		redrawProfile();
	}

	private void drawPbs()
	{
		if (lastStats == null)
		{
			return;
		}
		String me = lastState != null ? lastState.getRsn() : null;
		keepingScroll(pbsTab, () -> StatsTabs.pbs(pbsTab, lastStats, new StatsTabs.PbUi(pbCategory, pbGroup, pbBoss, pbSize), pbActions,
			statsUi, statsActions, me));
	}

	/** Opens the PBs page on a category, group and boss (previews and tests). */
	void showPbs(String category, String group, String boss)
	{
		pbActions.pick(category, group, boss, null);
		show(PBS);
	}

	private final StatsTabs.PbActions pbActions = new StatsTabs.PbActions()
	{
		@Override
		public void pick(String category, String group, String boss, String teamSize)
		{
			pbCategory = category;
			pbGroup = group;
			pbBoss = boss;
			pbSize = teamSize;
			drawPbs();
		}

		@Override
		public void openProfile(String rsn)
		{
			AsgardiansPanel.this.openProfile(rsn);
		}
	};

	/** The Drops page's drop-downs. */
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

	/** True while the side panel is open on Drops or PBs. Safe from any thread. */
	boolean statsShown()
	{
		return statsShown;
	}

	/** True while the side panel is open on Members. Safe from any thread. */
	boolean membersShown()
	{
		return membersShown;
	}

	/** Redraws the Groups page. */
	void updateGroups(GroupTabs.Input input)
	{
		lastGroups = input;
		updateTiles();
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

	/** Redraws the Members page. */
	void updateMembers(MembersTabs.Input input)
	{
		lastMembers = input;
		if (lastState != null)
		{
			updateFooter(lastState);
		}
		drawMembers();
		updateTiles();
		redrawProfile();
		if (MEMBER.equals(page))
		{
			drawMemberProfile();
		}
	}

	private void drawMembers()
	{
		if (lastMembers != null)
		{
			memberSearch.getParent().setVisible(lastMembers.getNotConnected() == null);
			keepingScroll(membersList, () -> MembersTabs.draw(membersList, lastMembers,
				new MembersTabs.Ui(memberFilter, showOffline, memberSearch.getText()), membersUi));
		}
	}

	/** The search box at the top of Members: the list narrows as the member types. */
	private void buildMemberSearch()
	{
		memberSearch.setToolTipText("Type part of a name");
		memberSearch.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		memberSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				drawMembers();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				drawMembers();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
				drawMembers();
			}
		});
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(PanelParts.plain("Search", QUIET), BorderLayout.WEST);
		row.add(memberSearch, BorderLayout.CENTER);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		membersTab.add(row);
		membersTab.add(Box.createVerticalStrut(2));
		membersTab.add(membersList);
		membersList.setAlignmentX(LEFT_ALIGNMENT);
	}

	private final MembersTabs.Actions membersUi = new MembersTabs.Actions()
	{
		@Override
		public void filter(String filter)
		{
			memberFilter = MembersTabs.filter(filter);
			settings.set(MEMBER_FILTER_KEY, memberFilter);
			drawMembers();
		}

		@Override
		public void showOffline(boolean show)
		{
			showOffline = show;
			settings.set(SHOW_OFFLINE_KEY, String.valueOf(show));
			drawMembers();
		}

		@Override
		public void toggleSharing()
		{
			boolean on = lastMembers != null && lastMembers.isSharing();
			settings.set("shareActivity", String.valueOf(!on));
			if (lastMembers != null)
			{
				MembersTabs.Input in = lastMembers;
				updateMembers(new MembersTabs.Input(in.getMembers(), in.getMe(), in.getMyWorld(), in.getMyActivity(), !on,
					in.isActivityUnsupported(), in.getNotConnected()));
			}
		}

		@Override
		public void openProfile(String rsn)
		{
			AsgardiansPanel.this.openProfile(rsn);
		}
	};

	/** Redraws a page without jumping back to the top of the side panel. */
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
				java.awt.Component v = viewport.getView();
				int maxY = v != null ? Math.max(0, v.getHeight() - viewport.getExtentSize().height) : 0;
				viewport.setViewPosition(new java.awt.Point(at.x, Math.min(at.y, maxY)));
			});
		}
	}

	/** Clears the "Start a group" note once a group has started. */
	void groupStarted()
	{
		groupForm.reset();
	}

	/** True while the side panel is open on Groups. Safe from any thread. */
	boolean groupsShown()
	{
		return groupsShown;
	}

	@Override
	public void onActivate()
	{
		panelOpen = true;
		updateShown();
	}

	@Override
	public void onDeactivate()
	{
		panelOpen = false;
		updateShown();
	}

	private void updateShown()
	{
		groupsShown = panelOpen && GROUPS.equals(page);
		statsShown = panelOpen && (DROPS.equals(page) || PBS.equals(page));
		membersShown = panelOpen && (MEMBERS.equals(page) || MEMBER.equals(page));
	}

	/** Redraws the Profile page's own sections. */
	void update(State state)
	{
		lastState = state;
		updateFooter(state);
		redrawProfile();
		updateTiles();
		staffHomeRow.setVisible(isStaff());
	}

	/** The member's own profile: the clan profile, then what the plugin is sending. */
	private void redrawProfile()
	{
		State state = lastState;
		if (state == null)
		{
			return;
		}
		keepingScroll(statusTab, () ->
		{
			statusTab.removeAll();
			boolean connected = state.getRsn() != null && state.isInClan() && state.isSharing() && state.getStatus() != null
				&& !state.isStatusIsError() && ApiAddress.isDefault(state.getAddress());
			if (state.getRsn() != null && state.getMe() != null && state.isInClan() && state.isSharing())
			{
				Integer world = lastMembers != null ? lastMembers.getMyWorld() : null;
				String activity = lastMembers != null && lastMembers.isSharing() ? lastMembers.getMyActivity() : null;
				ProfileTabs.draw(statusTab, new ProfileTabs.Input(state.getRsn(), state.getMe(), true, false, false, world, activity,
					myRankings(), dropsOf(state.getRsn()), now()), profileActions);
			}
			if (!connected || state.getMe() == null || state.getQueued() > 0)
			{
				addCard(statusTab, connectionCard(state));
			}
			if (state.getRsn() != null && state.isInClan() && state.isSharing())
			{
				statusTab.add(Box.createVerticalStrut(PanelParts.GAP + 2));
				JLabel plugin = PanelParts.section("What the plugin sends");
				plugin.setAlignmentX(LEFT_ALIGNMENT);
				statusTab.add(plugin);
				addCard(statusTab, pbCard(state));
				addCard(statusTab, clogCard(state));
			}
			if (!state.getRecent().isEmpty())
			{
				addCard(statusTab, recentCard(state));
			}
		});
	}

	/** A clanmate's profile, with their world and activity from the Members list. */
	private void drawMemberProfile()
	{
		if (memberRsn == null)
		{
			return;
		}
		Integer world = null;
		String activity = null;
		if (lastMembers != null)
		{
			for (MembersTabs.Member m : lastMembers.getMembers())
			{
				if (DropRecorder.sameRsn(m.getName(), memberRsn))
				{
					world = m.getWorld();
					activity = m.getActivity();
				}
			}
		}
		Integer w = world;
		String a = activity;
		keepingScroll(memberProfileColumn, () ->
		{
			memberProfileColumn.removeAll();
			ProfileTabs.draw(memberProfileColumn, new ProfileTabs.Input(memberRsn, memberProfile, false, memberLoading, memberUnsupported, w, a,
				null, dropsOf(memberRsn), now()), profileActions);
		});
		// Staff see their notes about the member under the profile; the form keeps whatever is being typed.
		boolean staff = isStaff();
		memberNotesHolder.setVisible(staff);
		if (staff && !DropRecorder.sameRsn(memberRsn, memberNotesRsn))
		{
			memberNotesRsn = memberRsn;
			memberNotesForm.about(memberRsn);
			memberNotes = new StaffTabs.Input(null, true, null, now());
			StaffTabs.drawNotes(memberNotesList, memberNotesRsn, memberNotes, staffUi);
			staffLoader.accept(memberRsn);
		}
	}

	private final ProfileTabs.Actions profileActions = new ProfileTabs.Actions()
	{
		@Override
		public void openBoard(String title)
		{
			period = ClanTabs.MONTH;
			settings.set(PERIOD_KEY, period);
			uiActions.board(title);
			show(LEADERBOARD);
		}

		@Override
		public void bossIcon(JLabel label, String boss)
		{
			statsActions.bossIcon(label, boss);
		}
	};

	/** The member's places on this month's leaderboards. */
	private List<ClanData.ProfileRank> myRankings()
	{
		List<ClanData.ProfileRank> out = new ArrayList<>();
		if (lastInput == null || lastInput.getLeaderboards() == null)
		{
			return out;
		}
		for (ClanData.Leaderboard b : lastInput.getLeaderboards().getOrDefault(ClanTabs.MONTH, List.of()))
		{
			if (b != null && b.getMe() != null && b.getMe().getRank() != null)
			{
				ClanData.ProfileRank r = new ClanData.ProfileRank();
				r.setBoard(b.getTitle());
				r.setRank(b.getMe().getRank());
				r.setValue(b.getMe().getValue());
				out.add(r);
			}
		}
		return out;
	}

	/** Someone's drops in the clan feed. */
	private List<ClanData.StatEntry> dropsOf(String rsn)
	{
		List<ClanData.StatEntry> out = new ArrayList<>();
		ClanData.StatsFeed feed = lastStats != null ? lastStats.getFeed() : null;
		if (feed == null || feed.getDrops() == null || rsn == null)
		{
			return out;
		}
		for (ClanData.StatEntry e : feed.getDrops())
		{
			if (e != null && DropRecorder.sameRsn(e.getRsn(), rsn))
			{
				out.add(e);
			}
		}
		return out;
	}

	private java.time.Instant now()
	{
		return lastStats != null ? lastStats.getNow() : lastInput != null ? lastInput.getNow() : java.time.Instant.now();
	}

	private void drawSettings()
	{
		SettingsTabs.draw(settingsTab, settings, () ->
		{
			drawSettings();
			settingsTab.revalidate();
			settingsTab.repaint();
			if (lastMembers != null)
			{
				boolean sharing = SettingsTabs.value(settings, SettingsTabs.GROUPS.get(0).getSettings().get(1));
				MembersTabs.Input in = lastMembers;
				lastMembers = new MembersTabs.Input(in.getMembers(), in.getMe(), in.getMyWorld(), in.getMyActivity(), sharing,
					in.isActivityUnsupported(), in.getNotConnected());
				drawMembers();
			}
		});
	}

	/** Redraws Events, Bingo, Leaderboard and News. */
	void updateClan(ClanTabs.Input input)
	{
		lastInput = input;
		ClanTabs.Ui ui = ui();
		keepingScroll(eventsTab, () -> ClanTabs.events(eventsTab, input, refreshClan, eventActions, ui, uiActions));
		keepingScroll(bingoTab, () -> ClanTabs.bingo(bingoTab, input, eventActions, ui, uiActions));
		keepingScroll(leadersTab, () -> ClanTabs.leaderboards(leadersTab, input, refreshClan, ui, uiActions, kcPick.clone(), xpSkill));
		keepingScroll(newsTab, () -> ClanTabs.news(newsTab, input, refreshClan, new java.util.HashSet<>(openNews), id ->
		{
			if (!openNews.remove(id))
			{
				openNews.add(id);
			}
			redraw();
		}));
		drawEventDetails();
		unread = input.getNotConnected() == null ? input.getView().unread(input.getLastReadAnnouncementId()) : 0;
		updateTiles();
		redrawProfile();
	}

	private ClanTabs.Ui ui()
	{
		return new ClanTabs.Ui(eventsPage, eventId, new HashMap<>(teams), period, new LinkedHashSet<>(folded), bingoView, hideDone,
			tileId, board);
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

		@Override
		public void open(String id)
		{
			eventId = id;
			tileId = null;
			redraw();
			if (!BINGO.equals(page))
			{
				show(BINGO);
			}
		}

		@Override
		public void bingoView(String view)
		{
			bingoView = view;
			redraw();
		}

		@Override
		public void hideDone(boolean hide)
		{
			hideDone = hide;
			settings.set(HIDE_DONE_KEY, String.valueOf(hide));
			redraw();
		}

		@Override
		public void tile(String id)
		{
			tileId = id;
			redraw();
		}

		@Override
		public void xpPick(String skill)
		{
			xpSkill = skill;
			redraw();
		}

		@Override
		public void kcPick(String category, String group, String boss)
		{
			kcPick[0] = category;
			kcPick[1] = group;
			kcPick[2] = boss;
			redraw();
		}

		@Override
		public void details(String id)
		{
			if (EVENT.equals(page) && detailsEventId != null && !detailsEventId.equals(id))
			{
				history.addLast(here());
			}
			detailsEventId = id;
			drawEventDetails();
			show(EVENT);
		}

		@Override
		public void board(String title)
		{
			board = title;
			settings.set(BOARD_KEY, title);
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

	private void drawEventDetails()
	{
		if (lastInput != null && detailsEventId != null)
		{
			keepingScroll(eventTab, () -> ClanTabs.eventDetails(eventTab, lastInput, eventActions, uiActions, detailsEventId));
		}
	}

	/** The event whose details are open, from the last data loaded. */
	private ClanData.Event detailsEvent()
	{
		if (lastInput == null || detailsEventId == null)
		{
			return null;
		}
		for (ClanView.EventView v : lastInput.getView().getEvents())
		{
			if (detailsEventId.equals(v.getEvent().getId()))
			{
				return v.getEvent();
			}
		}
		return null;
	}

	/** Opens the Bingo page with a tile picked (previews and tests). */
	void showTile(String tileId)
	{
		show(BINGO);
		uiActions.tile(tileId);
	}

	/** Opens the Leaderboard on a board, and within Boss kills on a category, group and boss (previews and tests). */
	void showBoard(String title, String category, String group, String boss)
	{
		uiActions.board(title);
		uiActions.kcPick(category, group, boss);
		show(LEADERBOARD);
	}

	/** Opens the Leaderboard on a board and, within XP, a skill (previews and tests). */
	void showSkill(String title, String skill)
	{
		uiActions.board(title);
		uiActions.xpPick(skill);
		show(LEADERBOARD);
	}

	/** Opens an event's details page (previews and tests). */
	void showEvent(String eventId)
	{
		uiActions.details(eventId);
	}

	/** The scroll pane's view: as wide as the panel, as tall as the page. */
	private static class ScrollColumn extends JPanel implements javax.swing.Scrollable
	{
		@Override
		public java.awt.Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction)
		{
			return visible.height - 16;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	// ---- the Profile page's plugin sections ----

	private static Card connectionCard(State state)
	{
		Card card = new Card("Connection");
		card.add(small("Also shown at the bottom of the panel.", QUIET));
		card.gap();
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
				card.add(status(TODO, "Sharing is off. Turn on \"Share my data with Asgardians\" in Settings (the cog at the top)."));
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
		if (state.getQueued() > 0)
		{
			card.add(small(state.getQueued() + " waiting to send", QUIET));
		}
		return card;
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
