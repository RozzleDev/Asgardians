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
		/** Leaderboards by period: MONTH, YEAR and ALL. */
		Map<String, List<ClanData.Leaderboard>> leaderboards;
	}

	/** Choices the member has made in the panel, kept by the panel between redraws. */
	@Value
	static class Ui
	{
		/** CURRENT or SCHEDULE; null until the member picks, which shows Current event while one is running. */
		String eventsPage;
		/** The event open on the Current event page, when several are running. */
		String eventId;
		/** The team open on the Current event page, by event ID. */
		Map<String, String> teams;
		/** MONTH, YEAR or ALL. */
		String period;
		/** Titles of the leaderboards the member has folded away. */
		java.util.Set<String> folded;
	}

	static final String CURRENT = "current";
	static final String SCHEDULE = "schedule";
	static final String MONTH = "month";
	static final String YEAR = "year";
	static final String ALL = "all";
	/** The leaderboard periods, in the order of their buttons. */
	static final List<String> PERIODS = List.of(MONTH, YEAR, ALL);
	private static final List<String> PERIOD_LABELS = List.of("This month", "This year", "All time");

	/**
	 * A known period, or MONTH. Null-safe: List.of(...).contains(null) throws, and a member who never picked a
	 * period has none saved.
	 */
	static String period(String saved)
	{
		return saved != null && PERIODS.contains(saved) ? saved : MONTH;
	}

	/** What the panel's own controls do: page, event, team and period choices, and folding boards. Swing thread. */
	interface UiActions
	{
		void eventsPage(String page);

		void event(String eventId);

		void team(String eventId, String teamId);

		void period(String period);

		void fold(String title);
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
		List<ClanView.EventView> current = in.getView().current();
		String page = ui.getEventsPage() != null ? ui.getEventsPage() : current.isEmpty() ? SCHEDULE : CURRENT;
		addCard(column, PanelParts.choice(List.of("Current event", "Schedule"), CURRENT.equals(page) ? 0 : 1, 2,
			i -> uiActions.eventsPage(i == 0 ? CURRENT : SCHEDULE)));
		if (CURRENT.equals(page))
		{
			currentPage(column, current, in, actions, ui, uiActions);
		}
		else
		{
			schedulePage(column, in, actions, uiActions);
		}
		addCard(column, footer(in.getView(), in, refresh));
	}

	/** Every live and upcoming event by group. Running events get a short card pointing to the Current event page. */
	private static void schedulePage(JPanel column, Input in, Actions actions, UiActions uiActions)
	{
		ClanView view = in.getView();
		List<ClanView.EventView> current = view.current();
		for (String group : ClanView.GROUPS)
		{
			List<ClanView.EventView> events = view.inGroup(group);
			if (events.isEmpty())
			{
				continue;
			}
			addCard(column, PanelParts.section(ClanView.groupLabel(group)));
			// Anything with something to do or see gets its own card; the rest is a schedule.
			List<ClanView.EventView> schedule = new ArrayList<>();
			for (ClanView.EventView event : events)
			{
				if (current.contains(event))
				{
					addCard(column, runningCard(event, in, uiActions));
				}
				else if (event.getEvent().getSignup() != null)
				{
					addCard(column, eventCard(event, in, actions));
				}
				else
				{
					schedule.add(event);
				}
			}
			if (!schedule.isEmpty())
			{
				addCard(column, schedule(schedule, in));
			}
		}
		if (view.getFetchedAt() != null && view.getEvents().isEmpty())
		{
			Card card = new Card("Events");
			card.add(small("No events running or coming up right now.", QUIET));
			addCard(column, card);
		}
	}

	/** A running event on the Schedule page: when it ends, and a link to its full view. */
	private static Card runningCard(ClanView.EventView view, Input in, UiActions uiActions)
	{
		ClanData.Event event = view.getEvent();
		Card card = new Card(event.getName());
		addWhen(card, event, in);
		card.gap();
		card.addWide(PanelParts.spread(PanelParts.link("Teams, board and standings", () ->
		{
			uiActions.event(event.getId());
			uiActions.eventsPage(CURRENT);
		}), null));
		return card;
	}

	// ---- current event ----

	/** The running event, broken down by team: who's online, each team's board, and the standings. */
	private static void currentPage(JPanel column, List<ClanView.EventView> current, Input in, Actions actions, Ui ui,
		UiActions uiActions)
	{
		if (current.isEmpty())
		{
			Card card = new Card("Current event");
			card.add(small(in.getView().getFetchedAt() == null ? "Loading…"
				: "No event is running right now. Upcoming events are on the Schedule page.", QUIET));
			addCard(column, card);
			return;
		}
		ClanView.EventView view = pick(current, ui.getEventId());
		if (current.size() > 1)
		{
			List<String> names = new ArrayList<>();
			for (ClanView.EventView v : current)
			{
				names.add(v.getEvent().getName());
			}
			addCard(column, PanelParts.choice(names, current.indexOf(view), 1, i -> uiActions.event(current.get(i).getEvent().getId())));
		}
		ClanData.Event event = view.getEvent();
		List<ClanData.Team> teams = view.teams();
		Map<String, String> tags = view.tags();

		Card about = new Card(event.getName());
		addWhen(about, event, in);
		String note = event.getSignup() != null ? event.getSignup().getNote() : null;
		if (event.getSummary() != null && !event.getSummary().equals(note))
		{
			about.add(small(event.getSummary(), TEXT));
		}
		if (event.getCodeword() != null)
		{
			about.gap();
			about.add(small("Codeword: " + event.getCodeword(), HIGHLIGHT));
		}
		about.gap();
		if (view.getTeam() != null)
		{
			about.add(PanelParts.status(GOOD, "You're on " + view.getTeam().getName()));
		}
		else
		{
			about.add(small(event.isIn() ? "You're in this event." : "You're not in this event.", QUIET));
		}
		signups(about, event, in, actions);
		if (event.getSignup() == null)
		{
			javax.swing.JLabel site = websiteLink("Open on website", event.getUrl(), in);
			if (site != null)
			{
				about.gap();
				about.addWide(PanelParts.spread(null, site));
			}
		}
		addCard(column, about);

		List<ClanData.Drop> choices = view.choices();
		if (!choices.isEmpty())
		{
			addCard(column, requiresAction(event, choices, in, actions));
		}
		if (teams.size() > 1)
		{
			addCard(column, standings(teams, tags, view.getTeam()));
		}
		if (teams.isEmpty())
		{
			if (view.getBoard() != null)
			{
				Card card = new Card("Your board");
				addBoard(card, view.getBoard());
				addCard(column, card);
			}
			addRecentDrops(column, view, teams, in, ui, uiActions);
			return;
		}

		ClanData.Team open = openTeam(teams, ui.getTeams().get(event.getId()), view.getTeam());
		if (teams.size() > 1)
		{
			List<String> labels = new ArrayList<>();
			for (ClanData.Team t : teams)
			{
				labels.add(teamLabel(t, tags));
			}
			addCard(column, PanelParts.choice(labels, teams.indexOf(open), 2,
				i -> uiActions.team(event.getId(), teams.get(i).getId())));
		}
		addCard(column, teamCard(open, teams.indexOf(open), tags.get(open.getId()), view, in));
		addRecentDrops(column, view, teams, in, ui, uiActions);
	}

	/** Recent drops go last, under the standings and teams. Hidden when the website doesn't send them. */
	private static void addRecentDrops(JPanel column, ClanView.EventView view, List<ClanData.Team> teams, Input in, Ui ui,
		UiActions uiActions)
	{
		if (view.getDrops() != null)
		{
			addCard(column, recentDrops(view, teams, in, ui, uiActions));
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

	/** The event's latest drops, folding away to its title. */
	private static Card recentDrops(ClanView.EventView view, List<ClanData.Team> teams, Input in, Ui ui, UiActions uiActions)
	{
		String foldKey = dropsFoldKey(view.getEvent().getId());
		boolean open = !ui.getFolded().contains(foldKey);
		List<ClanData.Drop> drops = view.getDrops();
		Card card = new Card(null);
		card.addWide(PanelParts.foldHeader("Recent drops", open, drops.isEmpty() ? null : String.valueOf(drops.size()),
			() -> uiActions.fold(foldKey)));
		if (!open)
		{
			return card;
		}
		card.add(Box.createVerticalStrut(4));
		if (drops.isEmpty())
		{
			card.add(small("No drops for this event yet.", QUIET));
			return card;
		}
		Map<String, String> teamNames = new HashMap<>();
		for (ClanData.Team t : teams)
		{
			teamNames.put(t.getId(), t.getName());
		}
		for (ClanData.Drop drop : drops.subList(0, Math.min(drops.size(), RECENT_DROPS)))
		{
			card.addWide(dotRow(dropColour(drop), dropName(drop), ClanView.ago(drop.time(), in.getNow()), null));
			StringBuilder line = new StringBuilder(drop.getRsn() != null ? drop.getRsn() : "Someone");
			String team = drop.getTeamId() != null ? teamNames.get(drop.getTeamId()) : null;
			if (team != null)
			{
				line.append(" (").append(team).append(")");
			}
			if (drop.getTileName() != null)
			{
				line.append(" · ").append(drop.getTileName());
			}
			line.append(" · ").append(dropState(drop));
			JLabel detail = small(line.toString(), QUIET);
			detail.setBorder(new javax.swing.border.EmptyBorder(0, 10, 3, 0));
			card.add(detail);
		}
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

	private static Card standings(List<ClanData.Team> teams, Map<String, String> tags, ClanData.Team own)
	{
		Card card = new Card("Standings");
		List<ClanData.Team> sorted = new ArrayList<>(teams);
		sorted.sort((a, b) -> Integer.compare(a.getRank() != null ? a.getRank() : 999, b.getRank() != null ? b.getRank() : 999));
		for (ClanData.Team t : sorted)
		{
			boolean mine = own != null && t.getId() != null && t.getId().equals(own.getId());
			String rank = t.getRank() != null ? "#" + t.getRank() + " " : "";
			String name = rank + teamLabel(t, tags) + (mine ? " (you)" : "");
			card.addWide(dotRow(TeamBadges.colour(t.getColour(), TeamBadges.fallback(teams.indexOf(t))), name, points(t), null));
		}
		return card;
	}

	/** One team: its standing, members with who's online, and its board. */
	private static Card teamCard(ClanData.Team team, int slot, String tag, ClanView.EventView view, Input in)
	{
		boolean mine = view.getTeam() != null && team.getId() != null && team.getId().equals(view.getTeam().getId());
		Card card = new Card(team.getName() + (mine ? " (your team)" : ""));
		javax.swing.JLabel chatBadge = badge(" " + tag + " ", TeamBadges.colour(team.getColour(), TeamBadges.fallback(slot)));
		javax.swing.JLabel badgeNote = PanelParts.plain("in clan chat", QUIET);
		badgeNote.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 0, 0));
		javax.swing.JPanel badgeRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		badgeRow.setOpaque(false);
		badgeRow.add(chatBadge);
		badgeRow.add(badgeNote);
		card.addWide(badgeRow);
		String standing = standing(team);
		if (!standing.isEmpty())
		{
			card.add(small(standing, QUIET));
		}
		List<String> members = ClanView.names(team.getMembers());
		card.gap();
		if (members.isEmpty())
		{
			card.add(small(team.getMemberCount() != null ? team.getMemberCount() + " members. The website hasn't sent their names."
				: "The website hasn't sent this team's members.", QUIET));
		}
		else
		{
			int online = 0;
			for (String member : members)
			{
				online += in.getOnline().containsKey(member.toLowerCase(Locale.UK)) ? 1 : 0;
			}
			card.add(heading("Members · " + online + " online"));
			for (String member : members)
			{
				Integer world = in.getOnline().get(member.toLowerCase(Locale.UK));
				String name = member + (member.equalsIgnoreCase(team.getCaptain()) ? " ★" : "");
				card.addWide(dotRow(world != null ? GOOD : QUIET, name, world != null ? "W" + world : "offline",
					world != null ? "Online on world " + world : "Offline"));
			}
		}
		ClanData.Board board = view.boardOf(team.getId());
		if (board != null && board.getTiles() != null && !board.getTiles().isEmpty())
		{
			card.gap();
			addBoard(card, board);
		}
		else if (view.getEvent().isHasBoard())
		{
			card.gap();
			card.add(small("This team's board isn't available.", QUIET));
		}
		return card;
	}

	private static void addBoard(Card card, ClanData.Board board)
	{
		String progress = board.getDone() != null && board.getTotal() != null
			? " · " + board.getDone() + " / " + board.getTotal() : "";
		card.add(heading("Board" + progress));
		addTiles(card, ClanView.orEmpty(board.getTiles()));
		javax.swing.JLabel legend = new javax.swing.JLabel(PanelParts.wrap(PanelParts.dot(GOOD) + " completed &nbsp;"
			+ PanelParts.dot(TODO) + " submitted &nbsp;" + PanelParts.dot(BAD) + " declined &nbsp;"
			+ PanelParts.dot(QUIET) + " needed"));
		legend.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		legend.setForeground(QUIET);
		card.add(legend);
	}

	static void leaderboards(JPanel column, Input in, Runnable refresh, Ui ui, UiActions uiActions)
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
		addCard(column, PanelParts.choice(PERIOD_LABELS, selected, 3, i -> uiActions.period(PERIODS.get(i))));
		List<ClanData.Leaderboard> boards = in.getLeaderboards().getOrDefault(PERIODS.get(selected), List.of());
		for (ClanData.Leaderboard board : boards)
		{
			addCard(column, leaderboardCard(board, in, !ui.getFolded().contains(board.getTitle()),
				() -> uiActions.fold(board.getTitle())));
		}
		if (in.getView().getFetchedAt() != null && boards.isEmpty())
		{
			Card card = new Card("Leaderboards");
			card.add(small("No leaderboards to show for " + PERIOD_LABELS.get(selected).toLowerCase(java.util.Locale.UK) + " right now.", QUIET));
			addCard(column, card);
		}
		addCard(column, footer(in.getView(), in, refresh));
	}

	/** A board that folds away to its title when its heading is clicked. Folded, it still shows the member's place. */
	private static Card leaderboardCard(ClanData.Leaderboard board, Input in, boolean open, Runnable fold)
	{
		Card card = new Card(null);
		String place = !open && board.getMe() != null && board.getMe().getRank() != null ? "You: #" + board.getMe().getRank() : null;
		card.addWide(PanelParts.foldHeader(board.getTitle(), open, place, fold));
		if (!open)
		{
			return card;
		}
		card.add(javax.swing.Box.createVerticalStrut(4));
		if (board.getSubtitle() != null)
		{
			card.add(small(board.getSubtitle(), QUIET));
		}
		if (board.ends() != null)
		{
			card.add(small("Ends in " + ClanView.countdown(in.getNow(), board.ends()), QUIET));
		}
		List<ClanData.LeaderboardRow> rows = ClanView.orEmpty(board.getRows());
		if (rows.isEmpty())
		{
			card.add(small("No entries yet.", QUIET));
		}
		card.gap();
		String myName = board.getMe() != null ? board.getMe().getRsn() : null;
		for (ClanData.LeaderboardRow row : rows)
		{
			card.addWide(PanelParts.rankRow(row.getRank(), row.getRsn(), row.getValue(), DropRecorder.sameRsn(row.getRsn(), myName)));
		}
		ClanData.LeaderboardRow mine = board.rowBelowTop();
		if (mine != null)
		{
			card.gap();
			card.addWide(PanelParts.rankRow(mine.getRank(), mine.getRsn(), mine.getValue(), true));
		}
		return card;
	}

	static void news(JPanel column, Input in, Runnable refresh)
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
			Card card = new Card(a.getTitle());
			if (a.getId() > in.getLastReadAnnouncementId())
			{
				card.add(badge("NEW", HIGHLIGHT));
			}
			String by = a.getPostedBy() != null ? " by " + a.getPostedBy() : "";
			card.add(small(ClanView.localTime(a.posted(), in.getZone()) + by, QUIET));
			card.gap();
			card.add(small(a.getBody(), TEXT));
			javax.swing.JLabel more = websiteLink("Read more", a.getUrl(), in);
			if (more != null)
			{
				card.gap();
				card.addWide(PanelParts.spread(null, more));
			}
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

	// ---- events ----

	private static Card eventCard(ClanView.EventView live, Input in, Actions actions)
	{
		ClanData.Event event = live.getEvent();
		Card card = new Card(event.getName());
		addWhen(card, event, in);
		String note = event.getSignup() != null ? event.getSignup().getNote() : null;
		if (event.getSummary() != null && !event.getSummary().equals(note))
		{
			card.add(small(event.getSummary(), TEXT));
		}
		if (event.getCodeword() != null)
		{
			card.gap();
			card.add(small("Codeword: " + event.getCodeword(), HIGHLIGHT));
		}
		signups(card, event, in, actions);

		ClanData.Team team = live.getTeam();
		if (team != null)
		{
			card.gap();
			card.add(heading("Your team: " + team.getName()));
			String standing = standing(team);
			if (!standing.isEmpty())
			{
				card.add(small(standing, QUIET));
			}
			for (String member : ClanView.names(team.getMembers()))
			{
				Integer world = in.getOnline().get(member.toLowerCase(Locale.UK));
				String name = member + (member.equalsIgnoreCase(team.getCaptain()) ? " ★" : "");
				card.addWide(dotRow(world != null ? GOOD : QUIET, name, world != null ? "W" + world : "offline",
					world != null ? "Online on world " + world : "Offline"));
			}
		}
		else if (!event.isIn() && event.getSignup() == null)
		{
			card.gap();
			card.add(small("You're not signed up for this one.", QUIET));
		}

		if (live.getStandings().size() > 1)
		{
			card.gap();
			card.add(heading("Standings"));
			List<ClanData.Team> sorted = new ArrayList<>(live.getStandings());
			sorted.sort((a, b) -> Integer.compare(a.getRank() != null ? a.getRank() : 999, b.getRank() != null ? b.getRank() : 999));
			for (ClanData.Team t : sorted)
			{
				boolean mine = team != null && t.getId() != null && t.getId().equals(team.getId());
				String rank = t.getRank() != null ? "#" + t.getRank() + " " : "";
				card.addWide(dotRow(colour(t.getColour(), mine ? HIGHLIGHT : QUIET), rank + t.getName(), points(t), null));
			}
		}

		ClanData.Board board = live.getBoard();
		if (board != null && board.getTiles() != null && !board.getTiles().isEmpty())
		{
			card.gap();
			addBoard(card, board);
		}

		if (event.getSignup() == null)
		{
			card.gap();
			card.addWide(PanelParts.spread(null, websiteLink("Open on website", event.getUrl(), in)));
		}
		return card;
	}

	/** The countdown, with the exact time on its own line so it doesn't wrap mid-time. */
	private static void addWhen(Card card, ClanData.Event event, Input in)
	{
		card.add(PanelParts.status(event.isLive() ? GOOD : QUIET, event.isLive()
			? "Live, ends in " + ClanView.countdown(in.getNow(), event.end())
			: "Starts in " + ClanView.countdown(in.getNow(), event.start())));
		card.add(small(ClanView.localTime(event.isLive() ? event.end() : event.start(), in.getZone()), QUIET));
	}

	/** "Live, ends in 1d 7h (Tue 3 Nov 00:00)" or "Starts in 4d 7h (Fri 6 Nov 00:00)". */
	private static String when(ClanData.Event event, Input in)
	{
		return event.isLive()
			? "Live, ends in " + ClanView.countdown(in.getNow(), event.end()) + " (" + ClanView.localTime(event.end(), in.getZone()) + ")"
			: "Starts in " + ClanView.countdown(in.getNow(), event.start()) + " (" + ClanView.localTime(event.start(), in.getZone()) + ")";
	}

	private static void addTiles(Card card, List<ClanData.Tile> tiles)
	{
		Map<String, List<ClanData.Tile>> groups = new LinkedHashMap<>();
		for (ClanData.Tile tile : tiles)
		{
			groups.computeIfAbsent(tile.getGroup() != null ? tile.getGroup() : "", g -> new ArrayList<>()).add(tile);
		}
		groups.forEach((group, list) ->
		{
			if (!group.isEmpty())
			{
				card.add(small(group, TEXT));
			}
			for (ClanData.Tile tile : list)
			{
				String tip = tile.getName() + " — " + stateLabel(tile)
					+ (tile.progress() != null ? ", " + tile.progress() + " accepted" : "")
					+ (tile.getPending() != null && tile.getPending() > 0 ? ", " + tile.getPending() + " waiting for review" : "")
					+ (tile.getDescription() != null ? "\n" + tile.getDescription() : "");
				String right = tile.progress() != null ? tile.progress()
					: tile.getPoints() != null ? "+" + trim(tile.getPoints()) : null;
				card.addWide(dotRow(tileColour(tile), tile.getName(), right, tip));
			}
		});
	}

	/** A compact list: what's running now, then what's next, with dates on the right. Hover for the full time. */
	private static Card schedule(List<ClanView.EventView> events, Input in)
	{
		Card card = new Card(null);
		for (ClanView.EventView view : events)
		{
			ClanData.Event event = view.getEvent();
			String tip = when(event, in);
			if (event.isLive())
			{
				card.add(heading(event.getName()));
				addWhen(card, event, in);
				card.gap();
			}
			else
			{
				String name = event.getName() + (event.isIn() ? " ✔" : "");
				card.addWide(dotRow(event.isIn() ? GOOD : QUIET, name, ClanView.shortDate(event.start(), in.getZone()),
					tip + (event.isIn() ? ". You'll get a reminder 15 minutes before." : "")));
			}
		}
		return card;
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

	private static Color tileColour(ClanData.Tile tile)
	{
		if (tile.isComplete())
		{
			return GOOD;
		}
		if ("pending".equals(tile.getState()) || (tile.getPending() != null && tile.getPending() > 0))
		{
			return TODO;
		}
		return "declined".equals(tile.getState()) ? BAD : QUIET;
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
