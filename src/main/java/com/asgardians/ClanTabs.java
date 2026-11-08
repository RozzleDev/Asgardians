package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.TODO;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.badge;
import static com.asgardians.PanelParts.button;
import static com.asgardians.PanelParts.dotRow;
import static com.asgardians.PanelParts.heading;
import static com.asgardians.PanelParts.small;
import java.awt.Color;
import net.runelite.client.ui.FontManager;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Value;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/** Builds the Events, Leaderboard and News tabs from a {@link ClanView}. Swing thread only. */
final class ClanTabs
{
	/** What the tabs need besides the view itself. */
	@Value
	static class Input
	{
		ClanView view;
		/** Online clan members: lowercased RSN to world. */
		Map<String, Integer> online;
		long lastReadAnnouncementId;
		/** Null when connected; otherwise why the tabs can't load. */
		String notConnected;
		ZoneId zone;
		Instant now;
		/** The API address, whose domain links must be on. */
		String address;
		/** Sign-up lists loaded so far, by event ID. */
		Map<String, ClanData.SignupList> signupLists;
		/** Events whose "Who's signed up" list is open. */
		java.util.Set<String> expanded;
		/** Events with a sign-up or withdraw request in flight. */
		java.util.Set<String> busy;
		/** Leaderboards by period: WEEK, MONTH, YEAR and ALL. */
		Map<String, List<ClanData.Leaderboard>> leaderboards;
	}

	/** Choices the member has made in the panel, kept by the panel between redraws. */
	@Value
	static class Ui
	{
		/** CURRENT or SCHEDULE; kept for saved choices, the Events page now lists everything. */
		String eventsPage;
		/** The event open on the Bingo page, when several are running. */
		String eventId;
		/** The team open on the Bingo page, by event ID. */
		Map<String, String> teams;
		/** WEEK, MONTH, YEAR or ALL. */
		String period;
		/** Titles of the leaderboards the member has folded away. */
		java.util.Set<String> folded;
		/** The Bingo page's tab: BOARD, LIST, SCORES or TEAM; null picks Board when there is one. */
		String bingoView;
		/** The Bingo page's List hides finished tasks. */
		boolean hideDone;
		/** The tile picked on the board, shown under it. */
		String tileId;
		/** The leaderboard picked in the drop-down; null picks the first. */
		String board;

		Ui(String eventsPage, String eventId, Map<String, String> teams, String period, java.util.Set<String> folded)
		{
			this(eventsPage, eventId, teams, period, folded, null, true, null, null);
		}

		Ui(String eventsPage, String eventId, Map<String, String> teams, String period, java.util.Set<String> folded,
			String bingoView, boolean hideDone, String tileId, String board)
		{
			this.eventsPage = eventsPage;
			this.eventId = eventId;
			this.teams = teams;
			this.period = period;
			this.folded = folded;
			this.bingoView = bingoView;
			this.hideDone = hideDone;
			this.tileId = tileId;
			this.board = board;
		}
	}

	static final String BOARD = "board";
	static final String LIST = "list";
	static final String SCORES = "scores";
	static final String TEAM = "team";

	static final String CURRENT = "current";
	static final String SCHEDULE = "schedule";
	static final String WEEK = "week";
	static final String MONTH = "month";
	static final String YEAR = "year";
	static final String ALL = "all";
	/** The leaderboard periods, in the order of the drop-down. */
	static final List<String> PERIODS = List.of(WEEK, MONTH, YEAR, ALL);
	private static final List<String> PERIOD_LABELS = List.of("This week", "This month", "This year", "All time");

	/**
	 * A known period, or MONTH. Null-safe: List.of(...).contains(null) throws, and a member who never picked a
	 * period has none saved.
	 */
	static String period(String saved)
	{
		return saved != null && PERIODS.contains(saved) ? saved : MONTH;
	}

	/** "This month", "This year" or "All time". */
	static String periodLabel(String saved)
	{
		return PERIOD_LABELS.get(PERIODS.indexOf(period(saved)));
	}

	/** The member's best place on a leaderboard for the period, for the Home tile. */
	@Value
	static class Rank
	{
		int rank;
		String board;
	}

	/** The member's best (lowest) place across the period's leaderboards, or null when they aren't on any. */
	static Rank myRank(Input in, String period)
	{
		Rank best = null;
		if (in.getLeaderboards() == null)
		{
			return null;
		}
		for (ClanData.Leaderboard board : in.getLeaderboards().getOrDefault(period(period), List.of()))
		{
			if (board != null && board.getMe() != null && board.getMe().getRank() != null && board.getMe().getRank() > 0
				&& (best == null || board.getMe().getRank() < best.getRank()))
			{
				best = new Rank(board.getMe().getRank(), board.getTitle() != null ? board.getTitle() : "");
			}
		}
		return best;
	}

	/** What the panel's own controls do: page, event, team and period choices, and folding boards. Swing thread. */
	interface UiActions
	{
		void eventsPage(String page);

		void event(String eventId);

		void team(String eventId, String teamId);

		void period(String period);

		void fold(String title);

		/** Opens a running event on the Bingo page. */
		default void open(String eventId)
		{
		}

		/** Board, List, Scores or Team on the Bingo page. */
		default void bingoView(String view)
		{
		}

		default void hideDone(boolean hide)
		{
		}

		/** Picks a tile on the board (null to close it). */
		default void tile(String tileId)
		{
		}

		/** Picks a leaderboard. */
		default void board(String title)
		{
		}

		/** Opens an event's details page. */
		default void details(String eventId)
		{
		}

		/** Picks a skill within the XP board (null for all skills). */
		default void xpPick(String skill)
		{
		}

		/** Picks within the Boss kills board: a category (null for all bosses), a group and a boss. */
		default void kcPick(String category, String group, String boss)
		{
		}
	}

	/** What the sign-up buttons do. Called on the Swing thread. */
	interface Actions
	{
		void signUp(ClanData.Event event);

		void withdraw(ClanData.Event event);

		void toggleSignups(ClanData.Event event);

		/** Puts a drop that fits several tiles on the one the member picked. */
		void chooseTile(ClanData.Event event, ClanData.Drop drop, ClanData.TileOption tile);
	}

	/** The key a drop uses in {@link Input#getBusy()} while its tile choice is being sent. */
	static String dropBusyKey(ClanData.Drop drop)
	{
		return "drop:" + drop.getId();
	}

	/** The key a Recent drops section uses in {@link Ui#getFolded()}. */
	static String dropsFoldKey(String eventId)
	{
		return "drops:" + eventId;
	}

	private static final int RECENT_DROPS = 10;

	/**
	 * Links come from the server, so only http(s) links on the clan website's own domain (or its subdomains) are opened,
	 * never anything else a compromised or mistaken server might send.
	 */
	static boolean safeLink(String url, String address)
	{
		okhttp3.HttpUrl link = url != null ? okhttp3.HttpUrl.parse(url) : null;
		okhttp3.HttpUrl api = address != null ? okhttp3.HttpUrl.parse(address) : null;
		if (link == null || api == null)
		{
			return false;
		}
		String site = siteDomain(api.host());
		String host = link.host();
		return host.equals(site) || host.endsWith("." + site);
	}

	/** "dev.asgardiansclan.com" and "asgardiansclan.com" both give "asgardiansclan.com"; IPs and localhost stay as they are. */
	static String siteDomain(String host)
	{
		if (host.matches("[0-9.]+") || host.contains(":") || !host.contains("."))
		{
			return host;
		}
		String[] parts = host.split("\\.");
		return parts[parts.length - 2] + "." + parts[parts.length - 1];
	}

	/** A link to a page on the clan website, or null when it isn't one the plugin will open. */
	private static javax.swing.JLabel websiteLink(String text, String url, Input in)
	{
		return safeLink(url, in.getAddress()) ? PanelParts.link(text, () -> LinkBrowser.browse(url)) : null;
	}

	private static final float LEFT = java.awt.Component.LEFT_ALIGNMENT;

	private ClanTabs()
	{
	}

	/**
	 * The Events page. Live events are highlighted cards with the event's picture, where the member stands, their
	 * progress and a bar of how far through it is; upcoming ones are picture cards with a tick when the member is in, or a
	 * Sign up button. Bingo cards open the Bingo page; the rest open their details page.
	 */
	static void events(JPanel column, Input in, Runnable refresh, Actions actions, Ui ui, UiActions uiActions)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Events");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		ClanView view = in.getView();
		List<ClanView.EventView> live = view.live();
		List<ClanView.EventView> upcoming = view.upcoming();
		if (!live.isEmpty())
		{
			addCard(column, PanelParts.section("Live now"));
			for (ClanView.EventView event : live)
			{
				column.add(Box.createVerticalStrut(5));
				addLeft(column, liveCard(event, in, uiActions));
			}
		}
		if (!upcoming.isEmpty())
		{
			addCard(column, PanelParts.section("Upcoming"));
			for (ClanView.EventView event : upcoming)
			{
				column.add(Box.createVerticalStrut(5));
				addLeft(column, upcomingCard(event, in, actions, uiActions));
			}
		}
		if (view.getFetchedAt() != null && live.isEmpty() && upcoming.isEmpty())
		{
			Card card = new Card("Events");
			card.add(small("No events running or coming up right now.", QUIET));
			addCard(column, card);
		}
		addCard(column, footer(view, in, refresh));
	}

	// ---- event cards ----

	static final Color BINGO_COLOUR = new Color(0xE8, 0x70, 0x1E);
	static final Color BOTW_COLOUR = new Color(0xE0, 0x4E, 0x40);
	static final Color SOTW_COLOUR = new Color(0x5C, 0xA8, 0xE8);

	/** "Bingo", "BOTW", "SOTW" or "Other". */
	static String typeTag(ClanData.Event event)
	{
		switch (ClanView.group(event))
		{
			case "bingo":
				return "Bingo";
			case "botw":
				return "BOTW";
			case "sotw":
				return "SOTW";
			default:
				return "Other";
		}
	}

	static Color typeColour(ClanData.Event event)
	{
		switch (ClanView.group(event))
		{
			case "bingo":
				return BINGO_COLOUR;
			case "botw":
				return BOTW_COLOUR;
			case "sotw":
				return SOTW_COLOUR;
			default:
				return QUIET;
		}
	}

	/**
	 * The member's own team: the one the website sent as theirs, else the team the event names as theirs ({@code teamId})
	 * from the event's list of teams. Null when they aren't on one.
	 */
	static ClanData.Team ownTeam(ClanView.EventView view)
	{
		if (view.getTeam() != null)
		{
			return view.getTeam();
		}
		String id = view.getEvent().getTeamId();
		if (id == null)
		{
			return null;
		}
		for (ClanData.Team t : view.teams())
		{
			if (id.equals(t.getId()))
			{
				return t;
			}
		}
		return null;
	}

	/** Whether the member is in, and how to say it: "Team Odin · #2", "Enrolled via WOM", "Signed up". Null when not. */
	static String enrolment(ClanView.EventView view)
	{
		ClanData.Event event = view.getEvent();
		String group = ClanView.group(event);
		if ("botw".equals(group) || "sotw".equals(group))
		{
			// Wise Old Man enrols the whole clan.
			return "Enrolled via WOM";
		}
		ClanData.Team team = ownTeam(view);
		if (team != null)
		{
			Integer rank = null;
			for (ClanData.Team t : view.teams())
			{
				if (t.getId() != null && t.getId().equals(team.getId()))
				{
					rank = t.getRank();
				}
			}
			return (team.getName() != null ? team.getName() : "Your team") + (rank != null ? " · #" + rank : "");
		}
		ClanData.Signup s = event.getSignup();
		if (s != null && s.isSignedUp())
		{
			return "waitlist".equals(s.getMyStatus()) ? "On the waitlist" : "confirmed".equals(s.getMyStatus()) ? "Confirmed" : "Signed up";
		}
		return event.isIn() ? "Signed up" : null;
	}

	/** One more line on a card: tiles done for bingo, the member's result for competitions, else the summary. */
	static String progressLine(ClanView.EventView view)
	{
		ClanData.Event event = view.getEvent();
		if (event.getMyProgress() != null)
		{
			return "You: " + event.getMyProgress() + (event.getMyRank() != null ? " · #" + event.getMyRank() : "");
		}
		ClanData.Board board = ownTeam(view) != null ? view.boardOf(ownTeam(view).getId()) : null;
		if (board == null)
		{
			board = view.getBoard();
		}
		if (board != null && board.getDone() != null && board.getTotal() != null)
		{
			return board.getDone() + "/" + board.getTotal() + " tiles";
		}
		return event.getSummary();
	}

	/** The event's name, with "Skill of the Week:" and "Boss of the Week:" shortened to SOTW and BOTW. */
	static String shortName(ClanData.Event event)
	{
		String name = event.getName() != null ? event.getName() : "Event";
		return name.replaceFirst("(?i)^skill of the week\\s*[:\\-–]\\s*", "SOTW: ").replaceFirst("(?i)^boss of the week\\s*[:\\-–]\\s*", "BOTW: ");
	}

	/** "Day 7 of 13" for a running event, or null without dates. */
	static String dayOf(ClanData.Event event, Instant now)
	{
		Instant start = event.start(), end = event.end();
		if (start == null || end == null || !end.isAfter(start))
		{
			return null;
		}
		long dayMillis = java.time.Duration.ofDays(1).toMillis();
		long total = Math.max(1, (end.toEpochMilli() - start.toEpochMilli() + dayMillis - 1) / dayMillis);
		long day = Math.max(1, Math.min(total, (now.toEpochMilli() - start.toEpochMilli()) / dayMillis + 1));
		return "Day " + day + " of " + total;
	}

	/** How far through a running event is, from 0 to 1. */
	static double share(ClanData.Event event, Instant now)
	{
		Instant start = event.start(), end = event.end();
		if (start == null || end == null || !end.isAfter(start))
		{
			return 0;
		}
		return Math.max(0, Math.min(1, (now.toEpochMilli() - start.toEpochMilli()) / (double) (end.toEpochMilli() - start.toEpochMilli())));
	}

	/** Picture, name and tag; the line under it is the member's status. */
	private static JPanel cardTop(ClanView.EventView view, int pictureSize, boolean live)
	{
		ClanData.Event event = view.getEvent();
		JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(PanelParts.eventBox(event, pictureSize), java.awt.BorderLayout.WEST);
		JPanel words = PanelParts.column();
		JPanel title = new JPanel(new java.awt.BorderLayout(4, 0));
		title.setOpaque(false);
		title.add(PanelParts.bold(shortName(event), TEXT), java.awt.BorderLayout.CENTER);
		title.add(PanelParts.tag(typeTag(event), typeColour(event)), java.awt.BorderLayout.EAST);
		title.setAlignmentX(LEFT);
		words.add(title);
		if (!live)
		{
			JLabel when = PanelParts.cut(startsLine(event, null), QUIET);
			when.setAlignmentX(LEFT);
			words.add(when);
		}
		return withWords(row, words);
	}

	private static JPanel withWords(JPanel row, JPanel words)
	{
		row.add(words, java.awt.BorderLayout.CENTER);
		return row;
	}

	/** A tick and the member's status, or nothing when they aren't in. */
	private static JPanel statusRow(String status, Color colour)
	{
		JPanel row = new JPanel(new java.awt.BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(PanelParts.tickIcon(), java.awt.BorderLayout.WEST);
		row.add(PanelParts.cut(status, colour), java.awt.BorderLayout.CENTER);
		row.setAlignmentX(LEFT);
		return row;
	}

	private static String startsLine(ClanData.Event event, Instant now)
	{
		Instant start = event.start();
		if (start == null)
		{
			return "Coming up";
		}
		return "Starts " + ClanView.shortDate(start, java.time.ZoneId.systemDefault()) + (now != null ? " · in " + ClanView.countdown(now, start) : "");
	}

	/** A running event: highlighted, with the member's status, progress and a bar of how far through it is. */
	private static Card liveCard(ClanView.EventView view, Input in, UiActions uiActions)
	{
		ClanData.Event event = view.getEvent();
		Color colour = typeColour(event);
		Card card = new Card(null).outlined(colour, PanelParts.CARD_WARM).stripe(colour);
		JPanel top = cardTop(view, 40, true);
		JPanel words = (JPanel) ((java.awt.BorderLayout) top.getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER);
		String status = enrolment(view);
		if (status != null)
		{
			words.add(statusRow(status, "bingo".equals(ClanView.group(event)) ? HIGHLIGHT : GOOD));
		}
		else
		{
			JLabel not = PanelParts.cut("You're not in this one", QUIET);
			not.setAlignmentX(LEFT);
			words.add(not);
		}
		String progress = progressLine(view);
		if (progress != null)
		{
			JLabel p = PanelParts.cut(progress, QUIET);
			p.setAlignmentX(LEFT);
			words.add(p);
		}
		card.addWide(top);
		card.gap();
		String left = event.end() != null ? "Ends in " + ClanView.countdown(in.getNow(), event.end()) : "Live";
		card.addWide(PanelParts.timeBar(share(event, in.getNow()), colour, left, dayOf(event, in.getNow())));
		boolean board = isBoard(view);
		String go = board ? "Open board ›" : "Details ›";
		Runnable open = () ->
		{
			if (board)
			{
				uiActions.open(event.getId());
			}
			else
			{
				uiActions.details(event.getId());
			}
		};
		card.add(Box.createVerticalStrut(3));
		card.addWide(PanelParts.spread(event.getCodeword() != null ? PanelParts.plain("Codeword: " + event.getCodeword(), HIGHLIGHT) : null,
			PanelParts.plain(go, HIGHLIGHT)));
		card.onClick(open, board ? "Open the board" : "See the details");
		return card;
	}

	/** An event coming up: picture, when it starts, and a tick when the member is in or a Sign up button. */
	private static Card upcomingCard(ClanView.EventView view, Input in, Actions actions, UiActions uiActions)
	{
		ClanData.Event event = view.getEvent();
		Color colour = typeColour(event);
		Card card = new Card(null).stripe(colour);
		JPanel top = new JPanel(new java.awt.BorderLayout(8, 0));
		top.setOpaque(false);
		top.add(PanelParts.eventBox(event, 36), java.awt.BorderLayout.WEST);
		JPanel words = PanelParts.column();
		JPanel title = new JPanel(new java.awt.BorderLayout(4, 0));
		title.setOpaque(false);
		title.add(PanelParts.bold(shortName(event), TEXT), java.awt.BorderLayout.CENTER);
		title.add(PanelParts.tag(typeTag(event), colour), java.awt.BorderLayout.EAST);
		title.setAlignmentX(LEFT);
		words.add(title);
		JLabel when = PanelParts.cut(startsLine(event, in.getNow()), QUIET);
		when.setAlignmentX(LEFT);
		words.add(when);
		String status = enrolment(view);
		ClanData.Signup s = event.getSignup();
		if (status != null)
		{
			words.add(statusRow(status, GOOD));
		}
		else if (s != null && s.isOpen() && !in.getBusy().contains(event.getId()))
		{
			JPanel row = new JPanel(new java.awt.BorderLayout(4, 0));
			row.setOpaque(false);
			row.add(PanelParts.plain("Sign-ups open", QUIET), java.awt.BorderLayout.CENTER);
			javax.swing.JButton join = PanelParts.button(full(s) ? "Waitlist" : "Sign up", true, () -> actions.signUp(event));
			join.setBorder(new javax.swing.border.EmptyBorder(1, 6, 1, 6));
			row.add(join, java.awt.BorderLayout.EAST);
			row.setAlignmentX(LEFT);
			words.add(row);
		}
		else
		{
			JLabel note = PanelParts.cut(in.getBusy().contains(event.getId()) ? "Sending…" : s != null && !s.isOpen() ? "Sign-ups closed" : " ", QUIET);
			note.setAlignmentX(LEFT);
			words.add(note);
		}
		top.add(words, java.awt.BorderLayout.CENTER);
		card.addWide(top);
		card.onClick(() -> uiActions.details(event.getId()), "See the details");
		return card;
	}

	private static boolean full(ClanData.Signup s)
	{
		return s.getCap() != null && s.getCount() != null && s.getCount() >= s.getCap();
	}

	/** A running event with a board opens on the Bingo page. */
	static boolean isBoard(ClanView.EventView view)
	{
		return view.getEvent().isLive() && (view.getEvent().isHasBoard() || view.getBoard() != null || !view.getTeamBoards().isEmpty());
	}

	/** Only https links on Wise Old Man are opened. */
	static boolean womLink(String url)
	{
		okhttp3.HttpUrl link = url != null ? okhttp3.HttpUrl.parse(url) : null;
		return link != null && link.isHttps() && (link.host().equals("wiseoldman.net") || link.host().endsWith(".wiseoldman.net"));
	}

	/**
	 * An event's details: picture, type, dates and whether the member is in; their progress and place; the standings;
	 * prizes; sign-ups; and buttons for Wise Old Man, the website, and the board for bingo.
	 */
	static void eventDetails(JPanel column, Input in, Actions actions, UiActions uiActions, String eventId)
	{
		column.removeAll();
		ClanView.EventView view = null;
		for (ClanView.EventView v : in.getView().getEvents())
		{
			if (v.getEvent().getId() != null && v.getEvent().getId().equals(eventId))
			{
				view = v;
			}
		}
		if (view == null)
		{
			Card card = new Card(null);
			card.add(small(in.getNotConnected() != null ? in.getNotConnected() : "This event has finished or isn't on the website any more.", QUIET));
			addCard(column, card);
			return;
		}
		ClanData.Event event = view.getEvent();
		Color colour = typeColour(event);
		Card head = new Card(null).outlined(colour, PanelParts.CARD_WARM);
		JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(PanelParts.eventBox(event, 46), java.awt.BorderLayout.WEST);
		JPanel words = PanelParts.column();
		JPanel tagRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		tagRow.setOpaque(false);
		tagRow.add(PanelParts.tag(typeTag(event), colour));
		tagRow.setAlignmentX(LEFT);
		words.add(tagRow);
		JLabel name = PanelParts.bold(shortName(event), TEXT);
		name.setAlignmentX(LEFT);
		words.add(name);
		JLabel when = PanelParts.cut(event.isLive() ? "Live · ends in " + ClanView.countdown(in.getNow(), event.end())
			: startsLine(event, in.getNow()), event.isLive() ? GOOD : QUIET);
		when.setAlignmentX(LEFT);
		words.add(when);
		if (event.start() != null && event.end() != null)
		{
			JLabel dates = PanelParts.cut(ClanView.shortDate(event.start(), in.getZone()) + " – " + ClanView.localTime(event.end(), in.getZone()), QUIET);
			dates.setAlignmentX(LEFT);
			words.add(dates);
		}
		row.add(words, java.awt.BorderLayout.CENTER);
		head.addWide(row);
		addCard(column, head);

		String status = enrolment(view);
		Card enrolled = new Card(null);
		enrolled.setBorder(new javax.swing.border.EmptyBorder(6, 8, 6, 8));
		if (status != null)
		{
			String text = "Enrolled via WOM".equals(status) ? "Enrolled via Wise Old Man" : status;
			enrolled.addWide(statusRow(text, GOOD));
		}
		else
		{
			enrolled.add(PanelParts.plain(event.getSignup() != null && event.getSignup().isOpen() ? "Not signed up yet" : "You're not in this one", QUIET));
		}
		addCard(column, enrolled);

		if (event.getSummary() != null || event.getCodeword() != null)
		{
			Card about = new Card(null);
			if (event.getSummary() != null)
			{
				about.add(small(event.getSummary(), TEXT));
			}
			if (event.getCodeword() != null)
			{
				about.add(small("Codeword: " + event.getCodeword(), HIGHLIGHT));
			}
			addCard(column, about);
		}

		if (event.getMyProgress() != null)
		{
			addCard(column, PanelParts.section("Your progress"));
			Card mine = new Card(null);
			JPanel line = new JPanel(new java.awt.BorderLayout(6, 0));
			line.setOpaque(false);
			line.add(PanelParts.bold(event.getMyProgress(), TEXT), java.awt.BorderLayout.CENTER);
			if (event.getMyRank() != null)
			{
				line.add(PanelParts.bold("#" + event.getMyRank() + (event.getParticipants() != null ? " of " + event.getParticipants() : ""),
					HIGHLIGHT), java.awt.BorderLayout.EAST);
			}
			mine.addWide(line);
			if (event.getMyRank() != null && event.getParticipants() != null && event.getParticipants() > 0)
			{
				mine.gap();
				int n = event.getParticipants();
				mine.addWide(PanelParts.progress(n - event.getMyRank() + 1, n, null));
			}
			column.add(Box.createVerticalStrut(4));
			addLeft(column, mine);
		}

		List<ClanData.LeaderboardRow> standings = ClanView.orEmpty(event.getStandings());
		if (!standings.isEmpty())
		{
			addCard(column, PanelParts.section("Standings"));
			for (ClanData.LeaderboardRow r : standings)
			{
				if (r == null)
				{
					continue;
				}
				column.add(Box.createVerticalStrut(4));
				addLeft(column, rankCard(r, r.getRank() != null && r.getRank().equals(event.getMyRank())));
			}
		}

		if (event.getPrizes() != null)
		{
			addCard(column, PanelParts.section("Prizes"));
			column.add(Box.createVerticalStrut(3));
			addLeft(column, small(event.getPrizes(), TEXT));
		}

		if (event.getSignup() != null)
		{
			Card signup = new Card("Sign-ups");
			signups(signup, event, in, actions);
			addCard(column, signup);
		}

		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 0, 6, 0));
		buttons.setOpaque(false);
		if (isBoard(view))
		{
			buttons.add(PanelParts.button("Open board", true, () -> uiActions.open(event.getId())));
		}
		if (womLink(event.getWomUrl()))
		{
			buttons.add(PanelParts.button("Wise Old Man", () -> LinkBrowser.browse(event.getWomUrl())));
		}
		if (safeLink(event.getUrl(), in.getAddress()))
		{
			buttons.add(PanelParts.button("Website", () -> LinkBrowser.browse(event.getUrl())));
		}
		if (buttons.getComponentCount() > 0)
		{
			buttons.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
			addCard(column, buttons);
		}
	}

	private static void addLeft(JPanel column, javax.swing.JComponent c)
	{
		c.setAlignmentX(LEFT);
		column.add(c);
	}


	// ---- Bingo page ----

	/** Bingo boards bigger than this show icons only, without progress strips. */
	static final int COMPACT_FROM = 8;

	/** The side of a square board, or 0 when the tiles don't make one (then the tasks show as a list). */
	static int boardSide(int tiles)
	{
		int n = (int) Math.round(Math.sqrt(tiles));
		return n >= 2 && n <= 12 && n * n == tiles ? n : 0;
	}

	/**
	 * How many tiles across to draw: the website's {@code columns} when it sends one, a square board's side, else a
	 * width that fits the tiles evenly (7, 6, 5, 8 or 4 across), else up to 7 across with a shorter last row. 0 when
	 * there are too few tiles for a board.
	 */
	static int boardColumns(Integer websiteColumns, int tiles)
	{
		if (tiles < 4)
		{
			return 0;
		}
		if (websiteColumns != null && websiteColumns >= 2 && websiteColumns <= 12)
		{
			return websiteColumns;
		}
		int side = boardSide(tiles);
		if (side > 0)
		{
			return side;
		}
		for (int c : new int[]{7, 6, 5, 8, 4})
		{
			if (tiles % c == 0 && tiles / c >= 2 && tiles / c <= 12)
			{
				return c;
			}
		}
		return Math.min(7, (int) Math.ceil(Math.sqrt(tiles)));
	}

	/** A square board's finished lines (rows, columns and both diagonals). */
	static int finishedLines(List<ClanData.Tile> tiles, int n, java.util.Set<Integer> inLine)
	{
		return finishedLines(tiles, n, n, inLine);
	}

	/**
	 * Which tiles sit in a finished line, and how many lines are finished: full rows, columns of two or more tiles, and
	 * on square boards the two diagonals.
	 */
	static int finishedLines(List<ClanData.Tile> tiles, int columns, int rows, java.util.Set<Integer> inLine)
	{
		int count = tiles.size();
		List<List<Integer>> lines = new ArrayList<>();
		for (int r = 0; r < rows; r++)
		{
			if ((r + 1) * columns <= count)
			{
				List<Integer> row = new ArrayList<>();
				for (int c = 0; c < columns; c++)
				{
					row.add(r * columns + c);
				}
				lines.add(row);
			}
		}
		for (int c = 0; c < columns; c++)
		{
			List<Integer> col = new ArrayList<>();
			for (int i = c; i < count; i += columns)
			{
				col.add(i);
			}
			if (col.size() >= 2)
			{
				lines.add(col);
			}
		}
		if (columns == rows && count == columns * rows)
		{
			List<Integer> down = new ArrayList<>();
			List<Integer> up = new ArrayList<>();
			for (int i = 0; i < columns; i++)
			{
				down.add(i * columns + i);
				up.add(i * columns + (columns - 1 - i));
			}
			lines.add(down);
			lines.add(up);
		}
		int done = 0;
		for (List<Integer> line : lines)
		{
			boolean all = true;
			for (int i : line)
			{
				all &= tiles.get(i).isComplete();
			}
			if (all)
			{
				done++;
				inLine.addAll(line);
			}
		}
		return done;
	}

	/** The running event the Bingo page shows: the picked one, else one the member is in with a board, else the first. */
	static ClanView.EventView bingoEvent(ClanView view, String picked)
	{
		List<ClanView.EventView> current = view.current();
		if (current.isEmpty())
		{
			return null;
		}
		for (ClanView.EventView v : current)
		{
			if (v.getEvent().getId() != null && v.getEvent().getId().equals(picked))
			{
				return v;
			}
		}
		for (ClanView.EventView v : current)
		{
			if (v.getEvent().isIn() && (v.getBoard() != null || v.getEvent().isHasBoard()))
			{
				return v;
			}
		}
		return pick(current, picked);
	}

	/**
	 * The Bingo page. A running event shows the member's team, then tabs: Board (tiles with item pictures and progress),
	 * List (each task as a card), Scores (every team) and Team (who's online). Between events it shows what's next.
	 */
	static void bingo(JPanel column, Input in, Actions actions, Ui ui, UiActions uiActions)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Bingo");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		ClanView.EventView view = bingoEvent(in.getView(), ui.getEventId());
		if (view == null)
		{
			noBingo(column, in, actions, uiActions);
			return;
		}
		List<ClanView.EventView> current = in.getView().current();
		if (current.size() > 1)
		{
			List<String> names = new ArrayList<>();
			for (ClanView.EventView v : current)
			{
				names.add(v.getEvent().getName() != null ? v.getEvent().getName() : "Event");
			}
			addCard(column, PanelParts.dropdowns(PanelParts.dropdown(names, current.indexOf(view),
				i -> uiActions.open(current.get(i).getEvent().getId())), null));
		}
		ClanData.Event event = view.getEvent();
		List<ClanData.Team> teams = view.teams();
		ClanData.Team open = teams.isEmpty() ? null : openTeam(teams, ui.getTeams().get(event.getId()), ownTeam(view));
		// The website may send every team's board (filed by team) or just the member's own.
		ClanData.Board board = open != null ? view.boardOf(open.getId()) : null;
		if (board == null)
		{
			board = view.getBoard();
		}
		addCard(column, teamSummary(view, teams, board, in));

		List<ClanData.Drop> choices = view.choices();
		if (!choices.isEmpty())
		{
			addCard(column, requiresAction(event, choices, in, actions));
		}

		List<ClanData.Tile> tiles = board != null ? ClanView.orEmpty(board.getTiles()) : List.of();
		List<String> views = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		if (!tiles.isEmpty() && boardColumns(board.getColumns(), tiles.size()) > 0)
		{
			views.add(BOARD);
			labels.add("Board");
		}
		if (!tiles.isEmpty())
		{
			views.add(LIST);
			labels.add(views.size() == 1 ? "Tasks" : "List");
		}
		if (teams.size() > 1)
		{
			views.add(SCORES);
			labels.add("Scores");
		}
		if (open != null)
		{
			views.add(TEAM);
			labels.add("Team");
		}
		if (views.isEmpty())
		{
			addRecentDrops(column, view, teams, in, ui, uiActions);
			return;
		}
		String shown = views.contains(ui.getBingoView()) ? ui.getBingoView() : views.get(0);
		boolean own = open != null && ownTeam(view) != null && open.getId() != null && open.getId().equals(ownTeam(view).getId());
		if (open != null && !own && (shown.equals(BOARD) || shown.equals(LIST)) && open.getName() != null)
		{
			column.add(Box.createVerticalStrut(4));
			addLeft(column, small("Showing " + open.getName() + "'s board. Pick a team in the Team tab.", QUIET));
		}
		column.add(Box.createVerticalStrut(PanelParts.GAP));
		addLeft(column, PanelParts.tabs(labels, views.indexOf(shown), i -> uiActions.bingoView(views.get(i))));
		switch (shown)
		{
			case BOARD:
				boardView(column, tiles, boardColumns(board.getColumns(), tiles.size()), ui, uiActions, view, in);
				break;
			case LIST:
				listView(column, tiles, ui, uiActions);
				break;
			case SCORES:
				scores(column, teams, view.tags(), ownTeam(view));
				break;
			default:
				teamView(column, teams, open, view, in, uiActions);
		}
		addRecentDrops(column, view, teams, in, ui, uiActions);
	}

	/** The team card at the top: name and place, points, tiles done, and when the event ends. */
	private static Card teamSummary(ClanView.EventView view, List<ClanData.Team> teams, ClanData.Board board, Input in)
	{
		ClanData.Event event = view.getEvent();
		ClanData.Team team = ownTeam(view);
		ClanData.Team standing = null;
		for (ClanData.Team t : teams)
		{
			if (team != null && t.getId() != null && t.getId().equals(team.getId()))
			{
				standing = t;
			}
		}
		Card card = new Card(null);
		JPanel top = new JPanel(new java.awt.BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(PanelParts.bold(team != null && team.getName() != null ? team.getName() : event.getName(), TEXT), java.awt.BorderLayout.CENTER);
		Integer rank = standing != null ? standing.getRank() : team != null ? team.getRank() : null;
		if (rank != null)
		{
			top.add(PanelParts.bold("#" + rank + (teams.size() > 1 ? " of " + teams.size() : ""), HIGHLIGHT), java.awt.BorderLayout.EAST);
		}
		card.addWide(top);
		Double points = standing != null ? standing.getPoints() : team != null ? team.getPoints() : null;
		double best = 0;
		for (ClanData.Team t : teams)
		{
			best = Math.max(best, t.getPoints() != null ? t.getPoints() : 0);
		}
		Integer done = board != null ? board.getDone() : null;
		Integer total = board != null ? board.getTotal() : null;
		if (points != null || (done != null && total != null))
		{
			card.gap();
			String left = points != null ? trim(points) + " " + (standing != null && standing.getPointsLabel() != null
				? standing.getPointsLabel() : "pts") : null;
			String right = done != null && total != null ? done + " / " + total + " tiles" : null;
			double have = points != null ? points : done != null ? done : 0;
			double need = points != null ? Math.max(best, points) : total != null ? total : 1;
			card.addWide(PanelParts.progress(have, need, left != null && right != null ? left + "  ·  " + right
				: left != null ? left : right));
		}
		card.add(small(when(event, in), GOOD));
		if (team == null)
		{
			card.add(small(event.isIn() ? "You're in this event." : "You're not in this event.", QUIET));
		}
		if (event.getCodeword() != null)
		{
			card.add(small("Codeword: " + event.getCodeword(), HIGHLIGHT));
		}
		return card;
	}

	/** The board as a grid of tiles; a picked tile's task shows underneath. */
	private static void boardView(JPanel column, List<ClanData.Tile> tiles, int n, Ui ui, UiActions uiActions, ClanView.EventView view,
		Input in)
	{
		int rows = (tiles.size() + n - 1) / n;
		java.util.Set<Integer> inLine = new java.util.HashSet<>();
		int lines = finishedLines(tiles, n, rows, inLine);
		boolean compact = n >= COMPACT_FROM;
		int gap = n >= 7 ? 2 : 3;
		JPanel grid = new JPanel(new java.awt.GridLayout(rows, n, gap, gap))
		{
			@Override
			public java.awt.Dimension getPreferredSize()
			{
				int w = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 220;
				int cell = (w - gap * (n - 1)) / n;
				return new java.awt.Dimension(w, cell * rows + gap * (rows - 1));
			}

			@Override
			public java.awt.Dimension getMaximumSize()
			{
				return getPreferredSize();
			}
		};
		grid.setOpaque(false);
		ClanData.Tile picked = null;
		for (int i = 0; i < tiles.size(); i++)
		{
			ClanData.Tile tile = tiles.get(i);
			boolean selected = tile.getId() != null && tile.getId().equals(ui.getTileId());
			if (selected)
			{
				picked = tile;
			}
			grid.add(new BingoTile(tile, selected, inLine.contains(i), compact,
				() -> uiActions.tile(selected ? null : tile.getId())));
		}
		// A shorter last row is padded with empty space.
		for (int i = tiles.size(); i < rows * n; i++)
		{
			JPanel empty = new JPanel();
			empty.setOpaque(false);
			grid.add(empty);
		}
		column.add(Box.createVerticalStrut(6));
		addLeft(column, grid);
		column.add(Box.createVerticalStrut(4));
		JPanel info = PanelParts.spread(PanelParts.plain(lines + (lines == 1 ? " line" : " lines") + " done", lines > 0 ? PanelParts.GOLD : QUIET),
			PanelParts.plain(n + "×" + rows, QUIET));
		info.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, info.getPreferredSize().height));
		addLeft(column, info);
		if (picked != null)
		{
			selectedTile(column, picked, view, in, uiActions);
		}
		else
		{
			addLeft(column, small(compact ? "Big board: pictures only. Click a tile to see its task." : "Click a tile to see its task.", QUIET));
		}
	}

	/**
	 * The picked tile, set apart from everything under it: a "Selected tile" heading with Close, its task card
	 * outlined in white (gold once done), and the drops sent for it inside the card.
	 */
	private static void selectedTile(JPanel column, ClanData.Tile tile, ClanView.EventView view, Input in, UiActions uiActions)
	{
		column.add(Box.createVerticalStrut(PanelParts.GAP + 2));
		JPanel head = PanelParts.spread(PanelParts.section("Selected tile"), PanelParts.link("Close ✕", () -> uiActions.tile(null)));
		head.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
		addLeft(column, head);
		column.add(Box.createVerticalStrut(4));
		Card card = taskCard(tile, true);
		card.outlined(tile.isComplete() ? PanelParts.GOLD : TEXT, tile.isComplete() ? PanelParts.CARD_WARM : PanelParts.CARD_HOVER);
		List<ClanData.Drop> drops = dropsFor(tile, view.getDrops());
		card.gap();
		JPanel divider = new JPanel();
		divider.setBackground(PanelParts.LINE);
		divider.setPreferredSize(new java.awt.Dimension(10, 1));
		divider.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 1));
		card.addWide(divider);
		card.add(Box.createVerticalStrut(4));
		card.add(PanelParts.plain(drops.isEmpty() ? "No drops sent for this tile yet." : "Drops for this tile", QUIET));
		for (ClanData.Drop drop : drops.subList(0, Math.min(5, drops.size())))
		{
			card.add(Box.createVerticalStrut(3));
			JPanel row = new JPanel(new java.awt.BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(PanelParts.itemBox(drop.getItemId(), 22), java.awt.BorderLayout.WEST);
			row.add(PanelParts.cut((drop.getRsn() != null ? drop.getRsn() : "Someone") + " · "
				+ ClanView.ago(drop.time(), in.getNow()).replace(" ago", ""), TEXT), java.awt.BorderLayout.CENTER);
			JPanel tagBox = new JPanel(new java.awt.GridBagLayout());
			tagBox.setOpaque(false);
			tagBox.add(PanelParts.tag(dropState(drop), dropColour(drop)));
			row.add(tagBox, java.awt.BorderLayout.EAST);
			card.addWide(row);
		}
		addLeft(column, card);
	}

	/** The event's drops that went on this tile, newest first. */
	static List<ClanData.Drop> dropsFor(ClanData.Tile tile, List<ClanData.Drop> drops)
	{
		List<ClanData.Drop> out = new ArrayList<>();
		for (ClanData.Drop d : ClanView.orEmpty(drops))
		{
			if (d == null)
			{
				continue;
			}
			boolean byId = tile.getId() != null && tile.getId().equals(d.getTileId());
			boolean byName = d.getTileId() == null && tile.getName() != null && tile.getName().equalsIgnoreCase(d.getTileName());
			if (byId || byName)
			{
				out.add(d);
			}
		}
		return out;
	}

	/** Every task as a card, open ones first. */
	private static void listView(JPanel column, List<ClanData.Tile> tiles, Ui ui, UiActions uiActions)
	{
		List<ClanData.Tile> sorted = new ArrayList<>(tiles);
		sorted.sort((a, b) -> Boolean.compare(a.isComplete(), b.isComplete()));
		long left = sorted.stream().filter(t -> !t.isComplete()).count();
		column.add(Box.createVerticalStrut(6));
		JPanel bar = PanelParts.spread(PanelParts.checkbox("Hide completed", ui.isHideDone(), () -> uiActions.hideDone(!ui.isHideDone())),
			PanelParts.plain(left + " left", QUIET));
		bar.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
		addLeft(column, bar);
		int hidden = 0;
		String group = null;
		for (ClanData.Tile tile : sorted)
		{
			if (ui.isHideDone() && tile.isComplete())
			{
				hidden++;
				continue;
			}
			column.add(Box.createVerticalStrut(5));
			addLeft(column, taskCard(tile, false));
		}
		if (hidden > 0)
		{
			column.add(Box.createVerticalStrut(4));
			addLeft(column, small(hidden + " completed " + (hidden == 1 ? "task" : "tasks") + " hidden", QUIET));
		}
	}

	/**
	 * A task: its item's picture, name and points, what kind it is, a progress bar for counts, and the latest item sent.
	 * The board's picked tile also shows its description.
	 */
	static Card taskCard(ClanData.Tile tile, boolean full)
	{
		boolean done = tile.isComplete();
		Card card = new Card(null);
		if (done)
		{
			card.outlined(null, PanelParts.CARD_WARM);
		}
		JPanel top = new JPanel(new java.awt.BorderLayout(6, 0));
		top.setOpaque(false);
		Integer itemId = firstItem(tile);
		top.add(PanelParts.itemBox(itemId, 26), java.awt.BorderLayout.WEST);
		JPanel words = new JPanel();
		words.setLayout(new javax.swing.BoxLayout(words, javax.swing.BoxLayout.Y_AXIS));
		words.setOpaque(false);
		JPanel title = new JPanel(new java.awt.BorderLayout(4, 0));
		title.setOpaque(false);
		title.add(PanelParts.bold((done ? "✔ " : "") + (tile.getName() != null ? tile.getName() : "Tile"), done ? QUIET : TEXT),
			java.awt.BorderLayout.CENTER);
		if (tile.getPoints() != null)
		{
			title.add(PanelParts.plain(trim(tile.getPoints()) + " pts", HIGHLIGHT), java.awt.BorderLayout.EAST);
		}
		title.setAlignmentX(LEFT);
		words.add(title);
		JPanel tags = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		tags.setOpaque(false);
		String kind = tile.getGroup() != null ? tile.getGroup() : kind(tile);
		if (kind != null)
		{
			tags.add(PanelParts.tag(kind, QUIET));
			tags.add(Box.createHorizontalStrut(3));
		}
		if (done)
		{
			tags.add(PanelParts.tag("Done", GOOD));
		}
		else if ("pending".equals(tile.getState()) || (tile.getPending() != null && tile.getPending() > 0))
		{
			tags.add(PanelParts.tag("In review", TODO));
		}
		else if ("declined".equals(tile.getState()))
		{
			tags.add(PanelParts.tag("Declined", BAD));
		}
		tags.setAlignmentX(LEFT);
		words.add(Box.createVerticalStrut(2));
		words.add(tags);
		top.add(words, java.awt.BorderLayout.CENTER);
		card.addWide(top);
		Integer have = tile.accepted();
		Integer need = tile.needed();
		if (!done && need != null && need > 1)
		{
			card.gap();
			card.addWide(PanelParts.progress(have != null ? have : 0, need, (have != null ? have : 0) + " / " + need));
		}
		if (tile.getLatest() != null && tile.getLatest().getItem() != null)
		{
			card.add(PanelParts.cut("Latest: " + tile.getLatest().getItem()
				+ (tile.getLatest().getRsn() != null ? " · " + tile.getLatest().getRsn() : ""), QUIET));
		}
		if (full && tile.getDescription() != null)
		{
			card.gap();
			card.add(small(tile.getDescription(), TEXT));
		}
		if (full && tile.getItems() != null && tile.getItems().size() > 1)
		{
			card.gap();
			card.add(small(("any".equalsIgnoreCase(tile.getMode()) ? "Any one of: " : "All of: ") + itemNames(tile), QUIET));
		}
		return card;
	}

	/** A task counted towards a number (kills, XP, items), so it has a progress bar; done-or-not tasks such as Boss time don't. */
	static boolean countsUp(ClanData.Tile tile)
	{
		Integer need = tile.needed();
		return need != null && need > 1;
	}

	static boolean inReview(ClanData.Tile tile)
	{
		return "pending".equals(tile.getState()) || (tile.getPending() != null && tile.getPending() > 0);
	}

	/** "Any of 3 items", "Kill count" and so on, when the website doesn't name a group. */
	private static String kind(ClanData.Tile tile)
	{
		List<ClanData.TileNeed> items = tile.getItems();
		if (items == null || items.isEmpty())
		{
			return null;
		}
		if (items.size() == 1)
		{
			return "Drop";
		}
		return "any".equalsIgnoreCase(tile.getMode()) ? "Any of " + items.size() : "Set of " + items.size();
	}

	private static String itemNames(ClanData.Tile tile)
	{
		List<String> names = new ArrayList<>();
		for (ClanData.TileNeed item : tile.getItems())
		{
			if (item != null && item.getName() != null)
			{
				names.add(item.getName() + (item.getNeed() != null && item.getNeed() > 1
					? " (" + (item.getHave() != null ? item.getHave() : 0) + "/" + item.getNeed() + ")" : ""));
			}
		}
		return String.join(", ", names);
	}

	static Integer firstItem(ClanData.Tile tile)
	{
		if (tile.getItems() != null)
		{
			for (ClanData.TileNeed item : tile.getItems())
			{
				if (item != null && item.getItemId() != null && item.getItemId() > 0)
				{
					return item.getItemId();
				}
			}
		}
		return null;
	}

	/** Every team, best first, with the member's own highlighted. */
	private static void scores(JPanel column, List<ClanData.Team> teams, Map<String, String> tags, ClanData.Team own)
	{
		List<ClanData.Team> sorted = new ArrayList<>(teams);
		sorted.sort((a, b) -> Integer.compare(a.getRank() != null ? a.getRank() : 999, b.getRank() != null ? b.getRank() : 999));
		for (ClanData.Team t : sorted)
		{
			boolean mine = own != null && t.getId() != null && t.getId().equals(own.getId());
			Card card = new Card(null);
			if (mine)
			{
				card.outlined(HIGHLIGHT, PanelParts.CARD_WARM);
			}
			JPanel line = new JPanel(new java.awt.BorderLayout(6, 0));
			line.setOpaque(false);
			Integer rank = t.getRank();
			line.add(PanelParts.bold(rank != null ? "#" + rank : "–", rank != null && rank == 1 ? PanelParts.GOLD : QUIET),
				java.awt.BorderLayout.WEST);
			line.add(PanelParts.bold(teamLabel(t, tags) + (mine ? " (you)" : ""), mine ? HIGHLIGHT : TEXT), java.awt.BorderLayout.CENTER);
			String points = points(t);
			if (points != null)
			{
				line.add(PanelParts.plain(points, TEXT), java.awt.BorderLayout.EAST);
			}
			card.addWide(line);
			List<String> members = ClanView.names(t.getMembers());
			int count = !members.isEmpty() ? members.size() : t.getMemberCount() != null ? t.getMemberCount() : 0;
			if (count > 0)
			{
				card.add(PanelParts.plain(count + " members", QUIET));
			}
			column.add(Box.createVerticalStrut(5));
			addLeft(column, card);
		}
	}

	/** A team's members, who's online and on which world, and its clan chat badge. */
	private static void teamView(JPanel column, List<ClanData.Team> teams, ClanData.Team team, ClanView.EventView view, Input in,
		UiActions uiActions)
	{
		Map<String, String> tags = view.tags();
		if (teams.size() > 1)
		{
			List<String> labels = new ArrayList<>();
			for (ClanData.Team t : teams)
			{
				labels.add(teamLabel(t, tags));
			}
			column.add(Box.createVerticalStrut(6));
			addLeft(column, PanelParts.dropdowns(PanelParts.dropdown(labels, teams.indexOf(team),
				i -> uiActions.team(view.getEvent().getId(), teams.get(i).getId())), null));
		}
		boolean mine = ownTeam(view) != null && team.getId() != null && team.getId().equals(ownTeam(view).getId());
		Card card = new Card(null);
		JPanel top = new JPanel(new java.awt.BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(PanelParts.bold(team.getName() + (mine ? " (your team)" : ""), TEXT), java.awt.BorderLayout.CENTER);
		String tag = tags.get(team.getId());
		if (tag != null)
		{
			top.add(badge(" " + tag + " ", TeamBadges.colour(team.getColour(), TeamBadges.fallback(teams.indexOf(team)))),
				java.awt.BorderLayout.EAST);
		}
		card.addWide(top);
		String standing = standing(team);
		if (!standing.isEmpty())
		{
			card.add(small(standing, QUIET));
		}
		List<String> members = ClanView.names(team.getMembers());
		card.gap();
		if (members.isEmpty())
		{
			card.add(small("The website hasn't sent this team's members.", QUIET));
		}
		for (String member : members)
		{
			Integer world = in.getOnline().get(member.toLowerCase(Locale.UK));
			String name = member + (member.equalsIgnoreCase(team.getCaptain()) ? " ★" : "");
			card.addWide(dotRow(world != null ? GOOD : QUIET, name, world != null ? "W" + world : "offline",
				world != null ? "Online on world " + world : "Offline"));
		}
		column.add(Box.createVerticalStrut(6));
		addLeft(column, card);
	}

	/** Between events: what's next (with sign-ups) and a pointer to Events. */
	private static void noBingo(JPanel column, Input in, Actions actions, UiActions uiActions)
	{
		Card card = new Card(null);
		JPanel top = new JPanel(new java.awt.BorderLayout(8, 0));
		top.setOpaque(false);
		JLabel grid = new JLabel(new javax.swing.Icon()
		{
			@Override
			public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				PanelChrome.paintIcon(g2, PanelChrome.Icon.BINGO, x + 10, y + 10);
				g2.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return 22;
			}

			@Override
			public int getIconHeight()
			{
				return 22;
			}
		});
		top.add(grid, java.awt.BorderLayout.WEST);
		JPanel words = PanelParts.column();
		words.add(PanelParts.bold("No bingo right now", TEXT));
		words.add(small(in.getView().getFetchedAt() == null ? "Loading…"
			: "Your progress isn't being tracked until the next one starts.", QUIET));
		top.add(words, java.awt.BorderLayout.CENTER);
		card.addWide(top);
		addCard(column, card);
		List<ClanView.EventView> next = new ArrayList<>();
		for (ClanView.EventView v : in.getView().upcoming())
		{
			if ("bingo".equals(ClanView.group(v.getEvent())) || v.getEvent().isHasBoard())
			{
				next.add(v);
			}
		}
		if (!next.isEmpty())
		{
			addCard(column, PanelParts.section("Next bingo"));
			for (ClanView.EventView v : next)
			{
				column.add(Box.createVerticalStrut(4));
				addLeft(column, upcomingCard(v, in, actions, uiActions));
			}
		}
	}

	/** One square of the board: the item's picture, a progress strip, and a tick once it's done. */
	static class BingoTile extends javax.swing.JComponent
	{
		private final ClanData.Tile tile;
		private final boolean selected;
		private final boolean inLine;
		private final boolean compact;
		private java.awt.image.BufferedImage picture;
		private boolean hover;

		BingoTile(ClanData.Tile tile, boolean selected, boolean inLine, boolean compact, Runnable click)
		{
			this.tile = tile;
			this.selected = selected;
			this.inLine = inLine;
			this.compact = compact;
			String tip = (tile.getName() != null ? tile.getName() : "Tile") + " · " + stateLabel(tile)
				+ (tile.progress() != null ? " · " + tile.progress() : "")
				+ (tile.getPoints() != null ? " · " + trim(tile.getPoints()) + " pts" : "");
			setToolTipText(tip);
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			PanelParts.itemImage(firstItem(tile), img ->
			{
				picture = img;
				repaint();
			});
			addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent e)
				{
					click.run();
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			boolean done = tile.isComplete();
			Color edge = selected ? TEXT : hover ? HIGHLIGHT : inLine ? PanelParts.GOLD : null;
			PanelParts.roundBox(g, getWidth(), getHeight(), done ? PanelParts.CARD_WARM : hover ? PanelParts.CARD_HOVER : PanelParts.CARD, edge);
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			int w = getWidth(), h = getHeight();
			int pad = compact ? 2 : Math.max(3, w / 8);
			int room = h - pad * 2 - (compact ? 0 : 3);
			if (picture != null)
			{
				double s = Math.min((w - pad * 2) / (double) picture.getWidth(), room / (double) picture.getHeight());
				int iw = (int) (picture.getWidth() * s), ih = (int) (picture.getHeight() * s);
				if (done)
				{
					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.5f));
				}
				g2.drawImage(picture, (w - iw) / 2, pad + (room - ih) / 2, iw, ih, null);
				g2.setComposite(java.awt.AlphaComposite.SrcOver);
			}
			else
			{
				// No picture: the tile's initials.
				g2.setFont(FontManager.getRunescapeSmallFont());
				String initials = initials(tile.getName());
				java.awt.FontMetrics fm = g2.getFontMetrics();
				g2.setColor(done ? QUIET : TEXT);
				g2.drawString(initials, (w - fm.stringWidth(initials)) / 2, pad + (room + fm.getAscent()) / 2 - 1);
			}
			if (done)
			{
				int d = compact ? 7 : Math.max(8, Math.min(11, w / 4));
				int x = w - d - 2, y = 2;
				g2.setColor(GOOD);
				g2.fillOval(x, y, d, d);
				g2.setColor(PanelParts.BACKGROUND);
				g2.setStroke(new java.awt.BasicStroke(d < 10 ? 1.2f : 1.6f));
				g2.drawPolyline(new int[]{x + d * 3 / 11, x + d * 5 / 11, x + d * 8 / 11}, new int[]{y + d * 6 / 11, y + d * 8 / 11, y + d * 3 / 11}, 3);
			}
			else if (!compact && (countsUp(tile) || inReview(tile)))
			{
				Integer have = tile.accepted();
				Integer need = tile.needed();
				double share = have != null && need != null && need > 0 ? Math.min(1, have / (double) need) : 0;
				boolean review = inReview(tile);
				g2.setColor(PanelParts.LINE);
				g2.fillRect(3, h - 5, w - 6, 2);
				g2.setColor(review ? TODO : HIGHLIGHT);
				g2.fillRect(3, h - 5, (int) Math.round((w - 6) * (review ? Math.max(share, 0.15) : share)), 2);
			}
			g2.dispose();
		}

		private static String initials(String name)
		{
			if (name == null || name.isEmpty())
			{
				return "?";
			}
			StringBuilder out = new StringBuilder();
			for (String word : name.split("\\s+"))
			{
				if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0)) && out.length() < 3)
				{
					out.append(Character.toUpperCase(word.charAt(0)));
				}
			}
			return out.length() > 0 ? out.toString() : name.substring(0, 1);
		}
	}

	// ---- current event ----
	/** Recent drops go last, under the standings and teams. Hidden when the website doesn't send them. */
	private static void addRecentDrops(JPanel column, ClanView.EventView view, List<ClanData.Team> teams, Input in, Ui ui,
		UiActions uiActions)
	{
		if (view.getDrops() != null)
		{
			column.add(Box.createVerticalStrut(PanelParts.GAP));
			addLeft(column, recentDrops(view, teams, in, ui, uiActions));
		}
	}

	/** Drops that fit several tiles, with a button for each tile so the member who got it can pick. */
	private static Card requiresAction(ClanData.Event event, List<ClanData.Drop> choices, Input in, Actions actions)
	{
		Card card = new Card("Requires action");
		boolean first = true;
		for (ClanData.Drop drop : choices)
		{
			if (!first)
			{
				card.gap();
			}
			first = false;
			card.add(PanelParts.status(TODO, dropName(drop) + " · " + ClanView.ago(drop.time(), in.getNow())));
			if (in.getBusy().contains(dropBusyKey(drop)))
			{
				card.add(small("Sending your choice…", QUIET));
				continue;
			}
			card.add(small("It fits more than one tile. Pick the one it should count for:", TEXT));
			for (ClanData.TileOption option : drop.getOptions())
			{
				String name = option.getName() != null ? option.getName() : "Tile " + option.getTileId();
				card.add(Box.createVerticalStrut(3));
				card.addWide(button(name, () -> actions.chooseTile(event, drop, option)));
			}
		}
		return card;
	}

	/**
	 * The event's latest drops, folding away to its title: each a card with the item's picture, who got it and for
	 * which team, the tile it went on, and whether it was accepted.
	 */
	private static JPanel recentDrops(ClanView.EventView view, List<ClanData.Team> teams, Input in, Ui ui, UiActions uiActions)
	{
		String foldKey = dropsFoldKey(view.getEvent().getId());
		boolean open = !ui.getFolded().contains(foldKey);
		List<ClanData.Drop> drops = view.getDrops();
		JPanel out = PanelParts.column();
		out.add(Box.createVerticalStrut(PanelParts.GAP));
		JPanel head = PanelParts.foldHeader("Recent drops", open, drops.isEmpty() ? null : drops.size() + " sent",
			() -> uiActions.fold(foldKey));
		head.setBorder(new javax.swing.border.CompoundBorder(
			new javax.swing.border.MatteBorder(1, 0, 0, 0, PanelParts.LINE), new javax.swing.border.EmptyBorder(8, 2, 2, 0)));
		head.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
		head.setAlignmentX(LEFT);
		out.add(head);
		if (!open)
		{
			return out;
		}
		if (drops.isEmpty())
		{
			out.add(Box.createVerticalStrut(4));
			JLabel none = small("No drops for this event yet.", QUIET);
			none.setAlignmentX(LEFT);
			out.add(none);
			return out;
		}
		Map<String, String> teamNames = new HashMap<>();
		for (ClanData.Team t : teams)
		{
			teamNames.put(t.getId(), t.getName());
		}
		for (ClanData.Drop drop : drops.subList(0, Math.min(drops.size(), RECENT_DROPS)))
		{
			out.add(Box.createVerticalStrut(5));
			out.add(dropCard(drop, teamNames, in));
		}
		return out;
	}

	private static Card dropCard(ClanData.Drop drop, Map<String, String> teamNames, Input in)
	{
		Card card = new Card(null);
		card.setBorder(new javax.swing.border.EmptyBorder(6, 6, 6, 8));
		JPanel row = new JPanel(new java.awt.BorderLayout(7, 0));
		row.setOpaque(false);
		row.add(PanelParts.itemBox(drop.getItemId(), 34), java.awt.BorderLayout.WEST);
		JPanel words = PanelParts.column();
		JPanel top = new JPanel(new java.awt.BorderLayout(4, 0));
		top.setOpaque(false);
		top.add(PanelParts.bold(dropName(drop), TEXT), java.awt.BorderLayout.CENTER);
		top.add(PanelParts.plain(ClanView.ago(drop.time(), in.getNow()).replace(" ago", ""), QUIET), java.awt.BorderLayout.EAST);
		top.setAlignmentX(LEFT);
		words.add(top);
		String team = drop.getTeamId() != null ? teamNames.get(drop.getTeamId()) : null;
		JLabel who = PanelParts.cut((drop.getRsn() != null ? drop.getRsn() : "Someone") + (team != null ? " · " + team : ""), HIGHLIGHT);
		who.setAlignmentX(LEFT);
		words.add(who);
		JPanel tags = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		tags.setOpaque(false);
		tags.setBorder(new javax.swing.border.EmptyBorder(2, 0, 0, 0));
		tags.add(PanelParts.tag(dropState(drop), dropColour(drop)));
		tags.setAlignmentX(LEFT);
		if (drop.getTileName() != null)
		{
			JLabel tile = PanelParts.cut("Tile: " + drop.getTileName(), QUIET);
			tile.setAlignmentX(LEFT);
			words.add(tile);
		}
		words.add(tags);
		row.add(words, java.awt.BorderLayout.CENTER);
		card.addWide(row);
		String tip = dropName(drop) + " · " + (drop.getRsn() != null ? drop.getRsn() : "Someone")
			+ (drop.getTileName() != null ? " · " + drop.getTileName() : "") + " · " + dropState(drop);
		card.setToolTipText(tip);
		card.setAlignmentX(LEFT);
		return card;
	}

	private static String dropName(ClanData.Drop drop)
	{
		String name = drop.getItem() != null ? drop.getItem() : "A drop";
		return drop.getQuantity() != null && drop.getQuantity() > 1 ? drop.getQuantity() + " x " + name : name;
	}

	private static Color dropColour(ClanData.Drop drop)
	{
		String state = drop.getState() != null ? drop.getState() : "";
		switch (state)
		{
			case "accepted":
			case "completed":
				return GOOD;
			case "declined":
				return BAD;
			case "pending":
			case "choose":
				return TODO;
			default:
				return QUIET;
		}
	}

	private static String dropState(ClanData.Drop drop)
	{
		String state = drop.getState() != null ? drop.getState() : "";
		switch (state)
		{
			case "accepted":
			case "completed":
				return "accepted";
			case "declined":
				return "declined";
			case "pending":
				return "waiting for review";
			case "choose":
				return "tile not picked yet";
			case "unmatched":
				return "not on the board";
			default:
				return state.isEmpty() ? "sent" : state;
		}
	}

	/** "A · Asgardians", or just "Team 2" when the name already ends with its badge. */
	private static String teamLabel(ClanData.Team team, Map<String, String> tags)
	{
		String tag = tags.get(team.getId());
		String name = team.getName() != null ? team.getName() : "Team";
		return tag == null || name.endsWith(tag) ? name : tag + " · " + name;
	}

	private static ClanView.EventView pick(List<ClanView.EventView> current, String eventId)
	{
		for (ClanView.EventView v : current)
		{
			if (v.getEvent().getId().equals(eventId))
			{
				return v;
			}
		}
		for (ClanView.EventView v : current)
		{
			if (v.getEvent().isIn())
			{
				return v;
			}
		}
		return current.get(0);
	}

	/** The team the member picked, else their own, else the first. */
	private static ClanData.Team openTeam(List<ClanData.Team> teams, String picked, ClanData.Team own)
	{
		ClanData.Team fallback = teams.get(0);
		for (ClanData.Team t : teams)
		{
			if (t.getId() != null && t.getId().equals(picked))
			{
				return t;
			}
			if (own != null && t.getId() != null && t.getId().equals(own.getId()))
			{
				fallback = t;
			}
		}
		return fallback;
	}
	static void leaderboards(JPanel column, Input in, Runnable refresh, Ui ui, UiActions uiActions)
	{
		leaderboards(column, in, refresh, ui, uiActions, new String[3]);
	}

	/** The Leaderboard page; {@code kc} is the Boss kills pick: category, group and boss (any null). */
	static void leaderboards(JPanel column, Input in, Runnable refresh, Ui ui, UiActions uiActions, String[] kc)
	{
		leaderboards(column, in, refresh, ui, uiActions, kc, null);
	}

	/** The Leaderboard page; {@code kc} is the Boss kills pick and {@code skill} the XP board's skill (null for all). */
	static void leaderboards(JPanel column, Input in, Runnable refresh, Ui ui, UiActions uiActions, String[] kc, String skill)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Leaderboards");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		int selected = PERIODS.indexOf(period(ui.getPeriod()));
		List<ClanData.Leaderboard> boards = new ArrayList<>();
		for (ClanData.Leaderboard b : in.getLeaderboards().getOrDefault(PERIODS.get(selected), List.of()))
		{
			if (b != null)
			{
				boards.add(b);
			}
		}
		List<String> titles = new ArrayList<>();
		for (ClanData.Leaderboard b : boards)
		{
			titles.add(b.getTitle() != null ? b.getTitle() : "Board");
		}
		int pickedBoard = Math.max(0, titles.indexOf(ui.getBoard()));
		javax.swing.JComponent boardBox = titles.isEmpty() ? PanelParts.dropdown(List.of("No boards"), 0, i -> { })
			: PanelParts.dropdown(titles, pickedBoard, i -> uiActions.board(titles.get(i)));
		addCard(column, PanelParts.dropdowns(boardBox,
			PanelParts.dropdown(PERIOD_LABELS, selected, i -> uiActions.period(PERIODS.get(i)))));
		if (boards.isEmpty())
		{
			if (in.getView().getFetchedAt() != null)
			{
				Card card = new Card("Leaderboards");
				card.add(small("No leaderboards to show for " + PERIOD_LABELS.get(selected).toLowerCase(java.util.Locale.UK) + " right now.", QUIET));
				addCard(column, card);
			}
		}
		else if (boards.get(pickedBoard).getSkills() != null && !boards.get(pickedBoard).getSkills().isEmpty())
		{
			skillXp(column, boards.get(pickedBoard), in, skill, uiActions);
		}
		else if (boards.get(pickedBoard).getBosses() != null && !boards.get(pickedBoard).getBosses().isEmpty())
		{
			bossKills(column, boards.get(pickedBoard), in, kc, uiActions);
		}
		else
		{
			board(column, boards.get(pickedBoard), in);
		}
		addCard(column, footer(in.getView(), in, refresh));
	}

	/** XP: "All skills" shows total XP; picking a skill shows that skill's own board, in the game's skill order. */
	private static void skillXp(JPanel column, ClanData.Leaderboard all, Input in, String skill, UiActions uiActions)
	{
		java.util.Map<String, ClanData.SkillBoard> bySkill = new java.util.LinkedHashMap<>();
		for (ClanData.SkillBoard b : all.getSkills())
		{
			if (b != null && b.getSkill() != null)
			{
				bySkill.put(b.getSkill(), b);
			}
		}
		List<String> names = new ArrayList<>(bySkill.keySet());
		names.sort(java.util.Comparator.comparingInt(ClanTabs::skillOrder).thenComparing(String.CASE_INSENSITIVE_ORDER));
		List<String> labels = new ArrayList<>();
		labels.add("All skills");
		labels.addAll(names);
		String picked = skill != null && bySkill.containsKey(skill) ? skill : null;
		column.add(Box.createVerticalStrut(6));
		addLeft(column, PanelParts.dropdowns(PanelParts.dropdown(labels, picked == null ? 0 : labels.indexOf(picked),
			i -> uiActions.xpPick(i == 0 ? null : labels.get(i))), null));
		if (picked == null)
		{
			board(column, all, in);
			return;
		}
		ClanData.SkillBoard board = bySkill.get(picked);
		if (board.getTitle() == null)
		{
			board.setTitle(picked + " XP");
		}
		board(column, board, in);
	}

	/** Where a skill sits in the game's skill list; unknown names go last. */
	static int skillOrder(String name)
	{
		for (net.runelite.api.Skill s : net.runelite.api.Skill.values())
		{
			if (s.getName().equalsIgnoreCase(name))
			{
				return s.ordinal();
			}
		}
		return Integer.MAX_VALUE;
	}

	/**
	 * Boss kills, like the PBs page: "All bosses" shows total kills; picking a category (Raids, Bosses, Skilling,
	 * Minigames), a group and a boss shows that boss's own board.
	 */
	private static void bossKills(JPanel column, ClanData.Leaderboard all, Input in, String[] kc, UiActions uiActions)
	{
		java.util.Map<String, java.util.Map<String, java.util.Map<String, ClanData.BossBoard>>> tree = new java.util.LinkedHashMap<>();
		for (ClanData.BossBoard b : all.getBosses())
		{
			if (b == null || b.getBoss() == null)
			{
				continue;
			}
			String[] where = BossGroups.place(b.getBoss(), b.getCategory(), b.getGroup());
			tree.computeIfAbsent(where[0], c -> new java.util.LinkedHashMap<>())
				.computeIfAbsent(where[1] != null ? where[1] : "", g -> new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER))
				.put(b.getBoss(), b);
		}
		for (java.util.Map<String, java.util.Map<String, ClanData.BossBoard>> groups : tree.values())
		{
			if (groups.size() > 1 && groups.containsKey(""))
			{
				java.util.Map<String, ClanData.BossBoard> loose = groups.remove("");
				groups.computeIfAbsent(BossGroups.OTHER, g -> new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER)).putAll(loose);
			}
		}
		List<String> cats = new ArrayList<>(tree.keySet());
		List<String> seen = new ArrayList<>(cats);
		cats.sort(java.util.Comparator.comparingInt((String c) -> BossGroups.categoryOrder(c)).thenComparingInt(seen::indexOf));
		List<String> catLabels = new ArrayList<>();
		catLabels.add("All bosses");
		catLabels.addAll(cats);
		String cat = kc[0] != null && cats.contains(kc[0]) ? kc[0] : null;
		javax.swing.JComponent catBox = PanelParts.dropdown(catLabels, cat == null ? 0 : catLabels.indexOf(cat),
			i -> uiActions.kcPick(i == 0 ? null : catLabels.get(i), null, null));
		if (cat == null)
		{
			column.add(Box.createVerticalStrut(6));
			addLeft(column, PanelParts.dropdowns(catBox, null));
			board(column, all, in);
			return;
		}
		java.util.Map<String, java.util.Map<String, ClanData.BossBoard>> groups = tree.get(cat);
		List<String> groupNames = new ArrayList<>(groups.keySet());
		List<String> groupSeen = new ArrayList<>(groupNames);
		groupNames.sort(java.util.Comparator.comparingInt((String g) -> BossGroups.groupOrder(g)).thenComparingInt(groupSeen::indexOf));
		String group = kc[1] != null && groupNames.contains(kc[1]) ? kc[1] : groupNames.get(0);
		String groupPick = group.isEmpty() ? null : group;
		javax.swing.JComponent groupBox = !group.isEmpty()
			? PanelParts.dropdown(groupNames, groupNames.indexOf(group), i -> uiActions.kcPick(cat, groupNames.get(i), null)) : null;
		column.add(Box.createVerticalStrut(6));
		addLeft(column, PanelParts.dropdowns(catBox, groupBox));
		java.util.Map<String, ClanData.BossBoard> bosses = groups.get(group);
		List<String> bossNames = new ArrayList<>(bosses.keySet());
		String boss = kc[2] != null && bossNames.contains(kc[2]) ? kc[2] : bossNames.get(0);
		column.add(Box.createVerticalStrut(6));
		addLeft(column, PanelParts.dropdowns(PanelParts.dropdown(bossNames, bossNames.indexOf(boss),
			i -> uiActions.kcPick(cat, groupPick, bossNames.get(i))), null));
		ClanData.BossBoard picked = bosses.get(boss);
		if (picked.getTitle() == null)
		{
			picked.setTitle(boss + " kills");
		}
		board(column, picked, in);
	}

	/** One board: a podium for the top three, then the rest, with the member's own row standing out. */
	private static void board(JPanel column, ClanData.Leaderboard board, Input in)
	{
		if (board.getSubtitle() != null || board.ends() != null)
		{
			column.add(Box.createVerticalStrut(4));
			String sub = board.getSubtitle() != null ? board.getSubtitle() : "";
			if (board.ends() != null)
			{
				sub += (sub.isEmpty() ? "" : " · ") + "Ends in " + ClanView.countdown(in.getNow(), board.ends());
			}
			addLeft(column, small(sub, QUIET));
		}
		List<ClanData.LeaderboardRow> rows = new ArrayList<>();
		for (ClanData.LeaderboardRow r : ClanView.orEmpty(board.getRows()))
		{
			if (r != null)
			{
				rows.add(r);
			}
		}
		String myName = board.getMe() != null ? board.getMe().getRsn() : null;
		if (rows.isEmpty())
		{
			Card card = new Card(null);
			card.add(small("No entries yet.", QUIET));
			addCard(column, card);
			return;
		}
		// First place in gold, as on the PBs page; then the rest, silver and bronze for second and third.
		ClanData.LeaderboardRow first = rows.get(0);
		boolean firstMine = DropRecorder.sameRsn(first.getRsn(), myName);
		column.add(Box.createVerticalStrut(PanelParts.GAP));
		javax.swing.JLabel picture = board instanceof ClanData.BossBoard
			? PanelParts.bossBox(((ClanData.BossBoard) board).getBoss(), 38)
			: board instanceof ClanData.SkillBoard
			? PanelParts.skillBox(((ClanData.SkillBoard) board).getSkill(), 38)
			: PanelParts.placeBox(first.getRank() != null ? first.getRank() : 1, 38);
		addLeft(column, PanelParts.firstPlace(picture,
			(board.getTitle() != null ? board.getTitle() : "Leaderboard").toUpperCase(Locale.UK),
			first.getValue() != null ? first.getValue() : "",
			(first.getRsn() != null ? first.getRsn() : "?") + (firstMine ? "  (you)" : "")));
		if (rows.size() > 1)
		{
			column.add(Box.createVerticalStrut(PanelParts.GAP));
			addLeft(column, PanelParts.section("Top " + rows.size()));
		}
		for (ClanData.LeaderboardRow row : rows.subList(1, rows.size()))
		{
			column.add(Box.createVerticalStrut(4));
			addLeft(column, rankCard(row, DropRecorder.sameRsn(row.getRsn(), myName)));
		}
		ClanData.LeaderboardRow mine = board.rowBelowTop();
		if (mine != null)
		{
			column.add(Box.createVerticalStrut(8));
			addLeft(column, rankCard(mine, true));
		}
	}

	private static Card rankCard(ClanData.LeaderboardRow row, boolean mine)
	{
		return PanelParts.placeCard(row.getRank(), row.getRsn(), row.getValue(), TEXT, mine);
	}

	static void news(JPanel column, Input in, Runnable refresh)
	{
		news(column, in, refresh, java.util.Set.of(), id -> { });
	}

	/**
	 * The News page: each announcement as a short summary card (title, when and who, and a line of its text). Clicking a
	 * card opens it to the full text and its "Read more" link; clicking again closes it.
	 */
	static void news(JPanel column, Input in, Runnable refresh, java.util.Set<Long> open, java.util.function.LongConsumer toggle)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("News");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		List<ClanData.Announcement> items = new ArrayList<>(in.getView().getAnnouncements());
		items.sort((a, b) -> Long.compare(b.getId(), a.getId()));
		for (ClanData.Announcement a : items)
		{
			boolean fresh = a.getId() > in.getLastReadAnnouncementId();
			boolean expanded = open.contains(a.getId());
			Card card = new Card(null);
			card.setBorder(new javax.swing.border.EmptyBorder(6, 8, 6, 8));
			if (fresh)
			{
				card.outlined(HIGHLIGHT, PanelParts.CARD);
			}
			else if (expanded)
			{
				card.outlined(PanelParts.LINE, PanelParts.CARD_HOVER);
			}
			JPanel top = new JPanel(new java.awt.BorderLayout(5, 0));
			top.setOpaque(false);
			top.add(PanelParts.dotIcon(fresh ? HIGHLIGHT : PanelParts.LINE), java.awt.BorderLayout.WEST);
			top.add(PanelParts.bold(a.getTitle() != null ? a.getTitle() : "Announcement", TEXT), java.awt.BorderLayout.CENTER);
			top.add(PanelParts.plain(expanded ? "–" : "+", QUIET), java.awt.BorderLayout.EAST);
			card.addWide(top);
			String by = a.getPostedBy() != null ? " · " + a.getPostedBy() : "";
			JLabel when = PanelParts.cut((fresh ? "New · " : "") + ClanView.ago(a.posted(), in.getNow()) + by, fresh ? HIGHLIGHT : QUIET);
			when.setBorder(new javax.swing.border.EmptyBorder(0, 11, 0, 0));
			card.addWide(when);
			String body = a.getBody() != null ? a.getBody().trim() : "";
			if (expanded)
			{
				card.gap();
				card.add(small(body, TEXT));
				javax.swing.JLabel more = websiteLink("Read more ›", a.getUrl(), in);
				if (more != null)
				{
					card.gap();
					card.addWide(PanelParts.spread(null, more));
				}
			}
			else if (!body.isEmpty())
			{
				JLabel preview = PanelParts.cut(preview(body), QUIET);
				preview.setBorder(new javax.swing.border.EmptyBorder(2, 11, 0, 0));
				card.addWide(preview);
			}
			long id = a.getId();
			card.onClick(() -> toggle.accept(id), expanded ? "Close" : "Read it");
			addCard(column, card);
		}
		if (in.getView().getFetchedAt() != null && items.isEmpty())
		{
			Card card = new Card("News");
			card.add(small("No announcements yet.", QUIET));
			addCard(column, card);
		}
		addCard(column, footer(in.getView(), in, refresh));
	}

	/** The first line of an announcement, for its summary card. */
	static String preview(String body)
	{
		String line = body.split("\\R", 2)[0].trim();
		return line.length() > 80 ? line.substring(0, 79) + "…" : line;
	}
	/** "Live, ends in 1d 7h (Tue 3 Nov 00:00)" or "Starts in 4d 7h (Fri 6 Nov 00:00)". */
	private static String when(ClanData.Event event, Input in)
	{
		return event.isLive()
			? "Live, ends in " + ClanView.countdown(in.getNow(), event.end()) + " (" + ClanView.localTime(event.end(), in.getZone()) + ")"
			: "Starts in " + ClanView.countdown(in.getNow(), event.start()) + " (" + ClanView.localTime(event.start(), in.getZone()) + ")";
	}
	/** Sign-up state, the member's own status, Sign up / Withdraw, and the list of who's signed up. */
	private static void signups(Card card, ClanData.Event event, Input in, Actions actions)
	{
		ClanData.Signup s = event.getSignup();
		if (s == null)
		{
			return;
		}
		card.gap();
		boolean full = s.getCap() != null && s.getCount() != null && s.getCount() >= s.getCap();
		String state = !s.isOpen() ? "Sign-ups closed"
			: s.closes() != null ? "Sign-ups close in " + ClanView.countdown(in.getNow(), s.closes()) : "Sign-ups open";
		card.add(PanelParts.status(s.isOpen() ? GOOD : QUIET, state));
		if (s.getCount() != null)
		{
			card.add(small(s.getCount() + (s.getCap() != null ? " of " + s.getCap() : "") + " signed up", QUIET));
		}
		if (s.getNote() != null)
		{
			card.add(small(s.getNote(), TEXT));
		}

		if (s.isSignedUp())
		{
			String status = "waitlist".equals(s.getMyStatus()) ? "You're on the waitlist"
				: "confirmed".equals(s.getMyStatus()) ? "You're confirmed" : "You're signed up";
			card.add(small("✔ " + status, GOOD));
			if (s.getPaid() != null)
			{
				card.add(small(s.getPaid() ? "Buy-in paid" : "Buy-in not paid yet", s.getPaid() ? GOOD : TODO));
			}
		}

		if (in.getBusy().contains(event.getId()))
		{
			card.add(small("Sending…", QUIET));
		}
		else if (s.isOpen())
		{
			card.gap();
			if (s.isSignedUp())
			{
				card.addWide(button("Withdraw", () -> actions.withdraw(event)));
			}
			else
			{
				card.addWide(button(full ? "Join the waitlist" : "Sign up", () -> actions.signUp(event)));
			}
		}

		boolean open = in.getExpanded().contains(event.getId());
		String count = s.getCount() != null ? " (" + s.getCount() + ")" : "";
		card.gap();
		card.addWide(PanelParts.spread(
			PanelParts.link((open ? "Hide sign-ups" : "See who's signed up") + count, () -> actions.toggleSignups(event)),
			websiteLink("Website", event.getUrl(), in)));
		if (open)
		{
			ClanData.SignupList list = in.getSignupLists().get(event.getId());
			if (list == null)
			{
				card.add(small("Loading…", QUIET));
			}
			else if (ClanView.orEmpty(list.getSignups()).isEmpty())
			{
				card.add(small("Nobody yet.", QUIET));
			}
			else
			{
				for (ClanData.SignupEntry entry : list.getSignups())
				{
					Color dot = entry.getPaid() == null ? QUIET : entry.getPaid() ? GOOD : TODO;
					String name = entry.getRsn() + (entry.getTeam() != null ? " · " + entry.getTeam() : "");
					String tip = entry.getPaid() == null ? null : entry.getPaid() ? "Buy-in paid" : "Buy-in not paid yet";
					card.addWide(dotRow(dot, name, statusLabel(entry.getStatus()), tip));
				}
			}
		}
	}

	private static String statusLabel(String status)
	{
		if ("waitlist".equals(status))
		{
			return "waitlist";
		}
		return "confirmed".equals(status) ? "confirmed" : "";
	}

	/** One quiet line under the cards: when it was last loaded and a Refresh link, plus any problem loading. */
	private static JPanel footer(ClanView view, Input in, Runnable refresh)
	{
		JPanel footer = PanelParts.column();
		if (view.getError() != null)
		{
			footer.add(small(view.getError(), BAD));
		}
		String updated = view.getFetchedAt() != null
			? "Updated " + ClanView.clock(view.getFetchedAt(), in.getZone())
			: "Loading…";
		JPanel row = PanelParts.spread(PanelParts.plain(updated, QUIET), PanelParts.link("Refresh", refresh));
		row.setAlignmentX(LEFT);
		row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		footer.add(row);
		return footer;
	}

	private static String standing(ClanData.Team team)
	{
		StringBuilder out = new StringBuilder();
		if (team.getRank() != null)
		{
			out.append("Rank #").append(team.getRank());
		}
		String points = points(team);
		if (points != null)
		{
			out.append(out.length() > 0 ? " · " : "").append(points);
		}
		return out.toString();
	}

	private static String points(ClanData.Team team)
	{
		if (team.getPoints() == null)
		{
			return null;
		}
		String value = team.getPoints() >= 100_000
			? QuantityFormatter.quantityToStackSize(Math.round(team.getPoints()))
			: trim(team.getPoints());
		return value + (team.getPointsLabel() != null ? " " + team.getPointsLabel() : "");
	}

	private static String trim(double value)
	{
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
	private static String stateLabel(ClanData.Tile tile)
	{
		if (tile.isComplete())
		{
			return "completed";
		}
		if ("pending".equals(tile.getState()))
		{
			return "submitted, waiting for review";
		}
		return tile.getState() != null ? tile.getState() : "needed";
	}

	private static Color colour(String hex, Color fallback)
	{
		try
		{
			return hex != null ? Color.decode(hex) : fallback;
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}
