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
		ClanData.Team team;
		List<ClanData.Team> standings;
		ClanData.Board board;
	}

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

	/** Tiles of the member's boards whose state changed to accepted or declined since the previous view. */
	List<String> tileChanges(ClanView previous)
	{
		if (previous == null)
		{
			return List.of();
		}
		Map<String, String> before = new HashMap<>();
		for (EventView view : previous.getEvents())
		{
			if (view.getBoard() != null && view.getBoard().getTiles() != null)
			{
				for (ClanData.Tile tile : view.getBoard().getTiles())
				{
					before.put(view.getEvent().getId() + "/" + tile.getId(), tile.getState());
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (EventView view : events)
		{
			if (view.getBoard() == null || view.getBoard().getTiles() == null)
			{
				continue;
			}
			for (ClanData.Tile tile : view.getBoard().getTiles())
			{
				String old = before.get(view.getEvent().getId() + "/" + tile.getId());
				String now = tile.getState();
				if (old != null && !old.equals(now) && ("accepted".equals(now) || "declined".equals(now)))
				{
					out.add(view.getEvent().getName() + ": tile \"" + tile.getName() + "\" was " + now + ".");
				}
			}
		}
		return out;
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

	static <T> List<T> orEmpty(List<T> list)
	{
		return list == null ? Collections.emptyList() : list;
	}
}
