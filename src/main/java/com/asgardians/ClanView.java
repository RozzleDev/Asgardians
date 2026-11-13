package com.asgardians;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Everything the Events and News tabs show, plus the logic that decides which chat notifications to send. Built from
 * the latest fetch; the plugin swaps in a new one each time. Pure, so it can be tested.
 */
@Value
class ClanView
{
	/** How many unread titles to list in chat at login. */
	private static final int LOGIN_NEWS_TITLES = 3;

	/** An event with whatever was fetched for it. */
	@Value
	static class EventView
	{
		ClanData.Event event;
		/** The member's own team, with its members. */
		ClanData.Team team;
		List<ClanData.Team> standings;
		/** The member's own team's board. */
		ClanData.Board board;
		/** Every team's board by team ID, when the website sends them all. */
		Map<String, ClanData.Board> teamBoards;
		/** The event's recent drops, newest first. Null when the website doesn't send them. */
		List<ClanData.Drop> drops;

		EventView(ClanData.Event event, ClanData.Team team, List<ClanData.Team> standings, ClanData.Board board)
		{
			this(event, team, standings, board, Map.of());
		}

		EventView(ClanData.Event event, ClanData.Team team, List<ClanData.Team> standings, ClanData.Board board,
			Map<String, ClanData.Board> teamBoards)
		{
			this(event, team, standings, board, teamBoards, null);
		}

		EventView(ClanData.Event event, ClanData.Team team, List<ClanData.Team> standings, ClanData.Board board,
			Map<String, ClanData.Board> teamBoards, List<ClanData.Drop> drops)
		{
			this.event = event;
			this.team = team;
			this.standings = standings;
			this.board = board;
			this.teamBoards = teamBoards;
			this.drops = drops;
		}

		/** Drops this member must pick a tile for. */
		List<ClanData.Drop> choices()
		{
			List<ClanData.Drop> out = new ArrayList<>();
			for (ClanData.Drop drop : orEmpty(drops))
			{
				if (drop.getId() != null && drop.needsChoice())
				{
					out.add(drop);
				}
			}
			return out;
		}

		/**
		 * Every team, in a fixed order (by ID, so badges don't swap when the standings change). The member's own team
		 * comes from /team, which always has its members.
		 */
		List<ClanData.Team> teams()
		{
			List<ClanData.Team> out = new ArrayList<>();
			boolean ownListed = false;
			for (ClanData.Team t : standings)
			{
				if (team != null && t.getId() != null && t.getId().equals(team.getId()))
				{
					out.add(merge(t, team));
					ownListed = true;
				}
				else
				{
					out.add(t);
				}
			}
			if (team != null && !ownListed)
			{
				out.add(team);
			}
			out.sort((a, b) -> String.valueOf(a.getId()).compareTo(String.valueOf(b.getId())));
			return out;
		}

		/** The member's own team as {@link #boardTeams()} lists it, or null when they aren't on one. */
		String ownBoardTeamId()
		{
			String own = team != null && team.getId() != null ? team.getId() : event.getTeamId();
			for (ClanData.Team t : boardTeams())
			{
				if (OWN_BOARD.equals(t.getId()) || (own != null && own.equals(t.getId())))
				{
					return t.getId();
				}
			}
			return null;
		}

		/** The teams to check boards for. With no team data, the member's own board stands in as "Your team". */
		List<ClanData.Team> boardTeams()
		{
			List<ClanData.Team> teams = teams();
			if (teams.isEmpty() && board != null)
			{
				ClanData.Team own = new ClanData.Team();
				own.setId(OWN_BOARD);
				own.setName("Your team");
				return List.of(own);
			}
			return teams;
		}

		/** A team's board: from the all-teams list, or the member's own board for their own team. */
		ClanData.Board boardOf(String teamId)
		{
			if (OWN_BOARD.equals(teamId))
			{
				return board;
			}
			ClanData.Board found = teamId != null ? teamBoards.get(teamId) : null;
			if (found == null && team != null && teamId != null && teamId.equals(team.getId()))
			{
				return board;
			}
			return found;
		}

		/** Each team's badge text, by team ID. */
		Map<String, String> tags()
		{
			return ClanView.tags(teams());
		}

		private static ClanData.Team merge(ClanData.Team standing, ClanData.Team own)
		{
			ClanData.Team out = new ClanData.Team();
			out.setId(standing.getId());
			out.setName(standing.getName() != null ? standing.getName() : own.getName());
			out.setColour(standing.getColour() != null ? standing.getColour() : own.getColour());
			out.setCaptain(own.getCaptain() != null ? own.getCaptain() : standing.getCaptain());
			out.setTag(standing.getTag() != null ? standing.getTag() : own.getTag());
			out.setMembers(own.getMembers() != null ? own.getMembers() : standing.getMembers());
			out.setMemberCount(standing.getMemberCount());
			out.setPoints(standing.getPoints());
			out.setPointsLabel(standing.getPointsLabel());
			out.setRank(standing.getRank());
			return out;
		}
	}

	/** Stands for the member's own board when the website sent no team details. */
	private static final String OWN_BOARD = "";

	List<EventView> events;
	List<ClanData.Announcement> announcements;
	/** When this was fetched; null before the first fetch. */
	Instant fetchedAt;
	/** Why the last fetch failed, if it did. */
	String error;

	static ClanView empty()
	{
		return new ClanView(List.of(), List.of(), null, null);
	}

	List<EventView> live()
	{
		List<EventView> out = new ArrayList<>();
		for (EventView view : events)
		{
			if (view.getEvent().isLive())
			{
				out.add(view);
			}
		}
		return out;
	}

	List<EventView> upcoming()
	{
		List<EventView> out = new ArrayList<>();
		for (EventView view : events)
		{
			if (view.getEvent().isUpcoming())
			{
				out.add(view);
			}
		}
		out.sort((a, b) ->
		{
			Instant x = a.getEvent().start();
			Instant y = b.getEvent().start();
			return x == null || y == null ? 0 : x.compareTo(y);
		});
		return out;
	}

	long latestAnnouncementId()
	{
		long latest = 0;
		for (ClanData.Announcement a : announcements)
		{
			latest = Math.max(latest, a.getId());
		}
		return latest;
	}

	int unread(long lastReadId)
	{
		int count = 0;
		for (ClanData.Announcement a : announcements)
		{
			if (a.getId() > lastReadId)
			{
				count++;
			}
		}
		return count;
	}

	/** Live events that aren't Boss or Skill of the Week: the Events tab's Current event page. */
	List<EventView> current()
	{
		List<EventView> out = new ArrayList<>();
		for (EventView view : live())
		{
			String group = group(view.getEvent());
			if (!"botw".equals(group) && !"sotw".equals(group))
			{
				out.add(view);
			}
		}
		return out;
	}

	/** The live event whose teams get badges in clan chat: the first one the member is in, else the first with teams. */
	EventView badgeEvent()
	{
		EventView first = null;
		for (EventView view : current())
		{
			if (view.teams().isEmpty())
			{
				continue;
			}
			if (view.getEvent().isIn())
			{
				return view;
			}
			if (first == null)
			{
				first = view;
			}
		}
		return first;
	}

	/**
	 * Badge text for each team. The website's own tag wins. Otherwise teams named with numbers ("Team 1", "Team 2") get
	 * their number, and anything else gets A, B, C in a fixed order.
	 */
	static Map<String, String> tags(List<ClanData.Team> teams)
	{
		Map<String, String> out = new java.util.LinkedHashMap<>();
		Set<String> numbers = new HashSet<>();
		boolean numbered = true;
		for (ClanData.Team t : teams)
		{
			java.util.regex.Matcher m = TRAILING_NUMBER.matcher(t.getName() != null ? t.getName() : "");
			numbered &= m.find() && numbers.add(m.group(1));
		}
		int index = 0;
		for (ClanData.Team t : teams)
		{
			String tag;
			if (t.getTag() != null && !t.getTag().trim().isEmpty())
			{
				tag = t.getTag().trim();
			}
			else if (numbered)
			{
				java.util.regex.Matcher m = TRAILING_NUMBER.matcher(t.getName());
				m.find();
				tag = m.group(1);
			}
			else
			{
				tag = index < 26 ? String.valueOf((char) ('A' + index)) : String.valueOf(index + 1);
			}
			out.put(t.getId(), tag.length() > 2 ? tag.substring(0, 2) : tag);
			index++;
		}
		return out;
	}

	private static final java.util.regex.Pattern TRAILING_NUMBER = java.util.regex.Pattern.compile("(\\d{1,2})\\s*$");

	// ---- groups ----

	/** The groups the Events tab shows, in order. */
	static final List<String> GROUPS = List.of("bingo", "botw", "sotw", "other");

	static String groupLabel(String group)
	{
		switch (group)
		{
			case "bingo":
				return "Bingo events";
			case "botw":
				return "Boss of the Week";
			case "sotw":
				return "Skill of the Week";
			default:
				return "Other events";
		}
	}

	/** Which group an event belongs to. R2R, Twisted Rainbow and Tug of Zaros run as bingo events. */
	static String group(ClanData.Event event)
	{
		String kind = event.getKind() != null ? event.getKind() : "";
		String type = event.getType() != null ? event.getType() : "";
		if ("botw".equals(type) || "sotw".equals(type))
		{
			return type;
		}
		if ("bingo".equals(type) || "bingo".equals(kind) || "r2r".equals(kind) || "twisted-rainbow".equals(kind) || "toz".equals(kind))
		{
			return "bingo";
		}
		return "other";
	}

	/** One group's events: live first, then upcoming, soonest first. */
	List<EventView> inGroup(String group)
	{
		List<EventView> out = new ArrayList<>();
		for (EventView view : live())
		{
			if (group.equals(group(view.getEvent())))
			{
				out.add(view);
			}
		}
		for (EventView view : upcoming())
		{
			if (group.equals(group(view.getEvent())))
			{
				out.add(view);
			}
		}
		return out;
	}

	// ---- notifications ----

	/** Announcements newer than the last one the member was told about, oldest first. */
	List<ClanData.Announcement> newAnnouncements(long lastNotifiedId)
	{
		List<ClanData.Announcement> out = new ArrayList<>();
		for (ClanData.Announcement a : announcements)
		{
			if (a.getId() > lastNotifiedId)
			{
				out.add(a);
			}
		}
		out.sort((a, b) -> Long.compare(a.getId(), b.getId()));
		return out;
	}

	/** A chat line for each bingo-style event the member is in whose buy-in isn't marked as paid. Waitlisted members owe nothing yet. */
	List<String> buyInReminders()
	{
		List<String> lines = new ArrayList<>();
		for (EventView view : inGroup("bingo"))
		{
			ClanData.Event event = view.getEvent();
			ClanData.Signup signup = event.getSignup();
			if (signup != null && signup.isSignedUp() && !"waitlist".equals(signup.getMyStatus())
				&& Boolean.FALSE.equals(signup.getPaid()))
			{
				lines.add("Your buy-in for " + event.getName() + " isn't marked as paid."
					+ (signup.getNote() != null ? " " + signup.getNote() : ""));
			}
		}
		return lines;
	}

	/** The chat lines for unread announcements at login, newest first; empty when everything has been read. */
	List<String> loginNews(long lastReadId)
	{
		List<ClanData.Announcement> unread = newAnnouncements(lastReadId);
		Collections.reverse(unread);
		if (unread.isEmpty())
		{
			return List.of();
		}
		if (unread.size() == 1)
		{
			return List.of("Unread news: " + unread.get(0).getTitle() + ". Open the News tab to read it.");
		}
		List<String> lines = new ArrayList<>();
		lines.add("You have " + unread.size() + " unread announcements. Open the News tab to read them.");
		for (ClanData.Announcement a : unread.subList(0, Math.min(unread.size(), LOGIN_NEWS_TITLES)))
		{
			lines.add("- " + a.getTitle());
		}
		if (unread.size() > LOGIN_NEWS_TITLES)
		{
			lines.add("...and " + (unread.size() - LOGIN_NEWS_TITLES) + " more.");
		}
		return lines;
	}

	/**
	 * Chat lines for tiles that changed since the previous view, on every team's board of the events the member is in:
	 * new submissions, items accepted on tiles that need several, completed tiles and declines. Each line names the team.
	 */
	List<String> tileChanges(ClanView previous)
	{
		return tileChanges(previous, (key, rsn) -> false);
	}

	/**
	 * As {@link #tileChanges(ClanView)}, leaving out a "submitted" line when {@code announced} says the member was
	 * already told about it when the drop happened. It's given the tile's key (see {@link #tileKey}) and the RSN
	 * that sent the latest item, if known.
	 */
	List<String> tileChanges(ClanView previous, java.util.function.BiPredicate<String, String> announced)
	{
		if (previous == null)
		{
			return List.of();
		}
		Map<String, ClanData.Tile> before = new HashMap<>();
		for (EventView view : previous.getEvents())
		{
			for (ClanData.Team t : view.boardTeams())
			{
				ClanData.Board board = view.boardOf(t.getId());
				if (board != null && board.getTiles() != null)
				{
					for (ClanData.Tile tile : board.getTiles())
					{
						before.put(tileKey(view.getEvent().getId(), t.getId(), tile.getId()), tile);
					}
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (EventView view : events)
		{
			if (!view.getEvent().isIn())
			{
				continue;
			}
			for (ClanData.Team t : view.boardTeams())
			{
				ClanData.Board board = view.boardOf(t.getId());
				if (board == null || board.getTiles() == null)
				{
					continue;
				}
				String team = t.getName() != null ? t.getName() : "A team";
				for (ClanData.Tile tile : board.getTiles())
				{
					String key = tileKey(view.getEvent().getId(), t.getId(), tile.getId());
					ClanData.Tile old = before.get(key);
					if (old != null)
					{
						tileChange(view.getEvent().getName(), team, old, tile, out,
							rsn -> announced.test(key, rsn));
					}
				}
			}
		}
		return out;
	}

	/** A tile completed since the last load: whether it was the member's own team, and whether the member sent the last item. */
	@lombok.Value
	static class Completed
	{
		boolean myTeam;
		boolean byMe;
	}

	/** Tiles completed since the previous view on the boards of events the member is in, one entry per tile. */
	List<Completed> completedTiles(ClanView previous, String me)
	{
		List<Completed> out = new ArrayList<>();
		if (previous == null)
		{
			return out;
		}
		Map<String, ClanData.Tile> before = new HashMap<>();
		for (EventView view : previous.getEvents())
		{
			for (ClanData.Team t : view.boardTeams())
			{
				ClanData.Board board = view.boardOf(t.getId());
				for (ClanData.Tile tile : board != null && board.getTiles() != null ? board.getTiles() : List.<ClanData.Tile>of())
				{
					before.put(tileKey(view.getEvent().getId(), t.getId(), tile.getId()), tile);
				}
			}
		}
		for (EventView view : events)
		{
			if (!view.getEvent().isIn())
			{
				continue;
			}
			for (ClanData.Team t : view.boardTeams())
			{
				ClanData.Board board = view.boardOf(t.getId());
				for (ClanData.Tile tile : board != null && board.getTiles() != null ? board.getTiles() : List.<ClanData.Tile>of())
				{
					ClanData.Tile old = before.get(tileKey(view.getEvent().getId(), t.getId(), tile.getId()));
					if (old != null && tile.isComplete() && !old.isComplete())
					{
						boolean myTeam = OWN_BOARD.equals(t.getId()) || (t.getId() != null && t.getId().equals(view.getEvent().getTeamId()));
						ClanData.TileItem latest = tile.getLatest();
						boolean byMe = myTeam && me != null && latest != null && latest.getRsn() != null && DropRecorder.sameRsn(latest.getRsn(), me);
						out.add(new Completed(myTeam, byMe));
					}
				}
			}
		}
		return out;
	}

	/** Identifies one tile on one team's board. */
	static String tileKey(String eventId, String teamId, String tileId)
	{
		return eventId + "/" + teamId + "/" + tileId;
	}

	/** What changed on one tile, as chat lines. */
	static void tileChange(String event, String team, ClanData.Tile old, ClanData.Tile now, List<String> out)
	{
		tileChange(event, team, old, now, out, rsn -> false);
	}

	private static void tileChange(String event, String team, ClanData.Tile old, ClanData.Tile now, List<String> out,
		java.util.function.Predicate<String> announced)
	{
		String tile = "\"" + now.getName() + "\"";
		boolean newSubmission = now.getPending() != null && old.getPending() != null
			? now.getPending() > old.getPending()
			: "pending".equals(now.getState()) && !"pending".equals(old.getState());
		ClanData.TileItem latest = now.getLatest();
		if (newSubmission && !now.isComplete() && !announced.test(latest != null ? latest.getRsn() : null))
		{
			String who = latest != null && latest.getRsn() != null ? latest.getRsn() + " (" + team + ")" : team;
			String what = latest != null && latest.getItem() != null ? latest.getItem() : "an item";
			String soFar = now.progress() != null
				? " " + (now.accepted() != null ? now.accepted() : 0) + " of " + now.needed() + " accepted so far." : "";
			out.add(event + ": " + who + " submitted " + what + " for " + tile + "." + soFar);
		}
		if (now.isComplete() && !old.isComplete())
		{
			out.add(event + ": " + team + " completed " + tile + ".");
		}
		else if (!now.isComplete() && now.accepted() != null && old.accepted() != null && now.accepted() > old.accepted())
		{
			out.add(event + ": " + team + " had an item accepted for " + tile
				+ (now.progress() != null ? " (" + now.progress() + ")" : "") + ".");
		}
		if ("declined".equals(now.getState()) && !"declined".equals(old.getState()))
		{
			out.add(event + ": " + team + "'s submission for " + tile + " was declined.");
		}
	}

	/** Drops, across live events, that this member must pick a tile for, as {event name, drop}. */
	List<Map.Entry<String, ClanData.Drop>> choices()
	{
		List<Map.Entry<String, ClanData.Drop>> out = new ArrayList<>();
		for (EventView view : events)
		{
			if (view.getEvent().isLive())
			{
				for (ClanData.Drop drop : view.choices())
				{
					out.add(Map.entry(view.getEvent().getName(), drop));
				}
			}
		}
		return out;
	}

	/** "just now", "5m ago", "3h ago" or "2d ago". */
	static String ago(Instant at, Instant now)
	{
		if (at == null || now == null)
		{
			return "";
		}
		long minutes = Math.max(Duration.between(at, now).toMinutes(), 0);
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		return hours < 24 ? hours + "h ago" : hours / 24 + "d ago";
	}

	/** A dropped item that counts on the member's own board: the tiles it fits and the chat line. */
	@Value
	static class DropMatch
	{
		/** Every tile it fits, by tile key. */
		List<String> keys;
		String message;
	}

	/**
	 * Tiles on the member's own team's board, in live events they're in, that these items count towards: tiles not
	 * yet complete with an item of the same ID still short of its count. One line per tile.
	 */
	List<DropMatch> dropMatches(List<Record.Item> items)
	{
		List<DropMatch> out = new ArrayList<>();
		if (items == null || items.isEmpty())
		{
			return out;
		}
		for (EventView view : events)
		{
			if (!view.getEvent().isLive() || !view.getEvent().isIn())
			{
				continue;
			}
			String teamId = view.ownBoardTeamId();
			ClanData.Board board = teamId != null ? view.boardOf(teamId) : view.getBoard();
			if (board == null || board.getTiles() == null)
			{
				continue;
			}
			// Group by item, so a drop that fits several tiles gets one line naming them all.
			Map<String, List<ClanData.Tile>> byItem = new java.util.LinkedHashMap<>();
			for (ClanData.Tile tile : board.getTiles())
			{
				String item = matchingItem(tile, items);
				if (item != null)
				{
					byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(tile);
				}
			}
			for (Map.Entry<String, List<ClanData.Tile>> entry : byItem.entrySet())
			{
				List<String> keys = new ArrayList<>();
				List<String> names = new ArrayList<>();
				for (ClanData.Tile tile : entry.getValue())
				{
					keys.add(tileKey(view.getEvent().getId(), teamId, tile.getId()));
					names.add("\"" + tile.getName() + "\"");
				}
				String where = names.size() == 1 ? "counts towards " + names.get(0)
					: "fits " + String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
				out.add(new DropMatch(keys, view.getEvent().getName() + ": " + entry.getKey() + " " + where + ". Drop submitted."));
			}
		}
		return out;
	}

	/** The name of the first dropped item that still counts on this tile, or null. */
	private static String matchingItem(ClanData.Tile tile, List<Record.Item> items)
	{
		if (tile.isComplete() || tile.getItems() == null)
		{
			return null;
		}
		for (Record.Item drop : items)
		{
			for (ClanData.TileNeed need : tile.getItems())
			{
				boolean stillNeeded = need.getHave() == null || need.getNeed() == null || need.getHave() < need.getNeed();
				if (need.getItemId() != null && need.getItemId() == drop.getId() && stillNeeded)
				{
					return drop.getName() != null ? drop.getName() : need.getName();
				}
			}
		}
		return null;
	}

	/** Events that went live since the previous view (not on the very first fetch, so a login doesn't repeat them). */
	List<ClanData.Event> newlyLive(ClanView previous)
	{
		if (previous == null || previous.getFetchedAt() == null)
		{
			return List.of();
		}
		Set<String> wasLive = new HashSet<>();
		for (EventView view : previous.live())
		{
			wasLive.add(view.getEvent().getId());
		}
		List<ClanData.Event> out = new ArrayList<>();
		for (EventView view : live())
		{
			if (!wasLive.contains(view.getEvent().getId()))
			{
				out.add(view.getEvent());
			}
		}
		return out;
	}

	/** Signed-up events starting within the reminder window that haven't been reminded yet. */
	List<ClanData.Event> dueReminders(Instant now, Duration window, Set<String> alreadyReminded)
	{
		List<ClanData.Event> out = new ArrayList<>();
		for (EventView view : upcoming())
		{
			ClanData.Event event = view.getEvent();
			Instant start = event.start();
			if (event.isIn() && start != null && !alreadyReminded.contains(event.getId())
				&& start.isAfter(now) && !start.isAfter(now.plus(window)))
			{
				out.add(event);
			}
		}
		return out;
	}

	// ---- formatting ----

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.UK);

	/** "Mon 26 Oct 14:00" in the member's own time zone. */
	static String localTime(Instant instant, ZoneId zone)
	{
		return instant == null ? "?" : WHEN.format(instant.atZone(zone));
	}

	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

	/** "26 Oct", for schedule rows. */
	static String shortDate(Instant instant, ZoneId zone)
	{
		return instant == null ? "?" : SHORT_DATE.format(instant.atZone(zone));
	}

	/** "16:42". */
	static String clock(Instant instant, ZoneId zone)
	{
		return instant == null ? "?" : CLOCK.format(instant.atZone(zone));
	}

	/** "2d 4h", "3h 10m", "12m", "now". */
	static String countdown(Instant from, Instant to)
	{
		if (from == null || to == null)
		{
			return "?";
		}
		long minutes = Duration.between(from, to).toMinutes();
		if (minutes <= 0)
		{
			return "now";
		}
		long days = minutes / (60 * 24);
		long hours = minutes / 60 % 24;
		long mins = minutes % 60;
		if (days > 0)
		{
			return days + "d " + hours + "h";
		}
		if (hours > 0)
		{
			return hours + "h " + mins + "m";
		}
		return mins + "m";
	}

	static String typeLabel(String type)
	{
		if (type == null)
		{
			return "Event";
		}
		switch (type)
		{
			case "botw":
				return "Boss of the week";
			case "sotw":
				return "Skill of the week";
			case "bingo":
				return "Bingo";
			case "competition":
				return "Competition";
			default:
				return "Event";
		}
	}


	/** RSNs from the website without empty entries, so a stray null or blank name can't break the panel. */
	static List<String> names(List<String> names)
	{
		List<String> out = new ArrayList<>();
		for (String name : orEmpty(names))
		{
			if (name != null && !name.trim().isEmpty())
			{
				out.add(name);
			}
		}
		return out;
	}
	static <T> List<T> orEmpty(List<T> list)
	{
		return list == null ? Collections.emptyList() : list;
	}
}
