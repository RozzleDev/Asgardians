package com.asgardians;

import java.time.Instant;
import java.util.List;
import lombok.Data;

/** What the read-only endpoints return: events, teams, boards and announcements. Shapes match PLUGIN_API.md. */
final class ClanData
{
	private ClanData()
	{
	}

	/** GET /plugin/events */
	@Data
	static class EventList
	{
		private List<Event> events;
	}

	@Data
	static class LeaderboardList
	{
		private List<Leaderboard> boards;
	}

	@Data
	static class Leaderboard
	{
		private String title;
		/** e.g. "November so far". */
		private String subtitle;
		/** When the competition ends; only sent for the current BOTW or SOTW. */
		private String endsAt;
		private List<LeaderboardRow> rows;
		/** The member's own row; null when they aren't on this board. */
		private LeaderboardRow me;

		Instant ends()
		{
			return parse(endsAt);
		}

		/** The member's row when the top rows don't already include it. */
		LeaderboardRow rowBelowTop()
		{
			if (me == null)
			{
				return null;
			}
			for (LeaderboardRow row : ClanView.orEmpty(rows))
			{
				if (DropRecorder.sameRsn(row.getRsn(), me.getRsn()))
				{
					return null;
				}
			}
			return me;
		}
	}

	@Data
	static class LeaderboardRow
	{
		private Integer rank;
		private String rsn;
		/** Already formatted by the website, e.g. "412M" or "1.2M xp". */
		private String value;
	}

	@Data
	static class Event
	{
		private String id;
		private String name;
		/** bingo, botw, sotw, competition or adhoc. */
		private String type;
		/** live, upcoming or finished. */
		private String status;
		private String startsAt;
		private String endsAt;
		/** Only while live. */
		private String codeword;
		private boolean signedUp;
		/** The member's team in this event, if any. */
		private String teamId;
		private boolean hasBoard;
		private String summary;
		private String url;
		/** calendar, bingo, r2r, twisted-rainbow, toz or wom; picks the event's view. */
		private String kind;
		/** Present when the event takes sign-ups. */
		private Signup signup;

		boolean isLive()
		{
			return "live".equals(status);
		}

		/** The member is taking part: signed up, on a team, or listed in the event's sign-ups. */
		boolean isIn()
		{
			return signedUp || teamId != null || (signup != null && signup.isSignedUp());
		}

		boolean isUpcoming()
		{
			return "upcoming".equals(status);
		}

		Instant start()
		{
			return parse(startsAt);
		}

		Instant end()
		{
			return parse(endsAt);
		}
	}

	/** GET /plugin/me: the logged-in member's clan profile. The totals are optional. */
	@Data
	static class Me
	{
		private String rsn;
		/** e.g. deputy_owner, opal, og_gamer. */
		private String rank;
		private Integer daysInClan;
		private Long splitCredit;
		private boolean discordLinked;
		/** How to link Discord, replacing the plugin's own wording when the website sends it. */
		private String discordHelp;
		private boolean held;
		private NextRank nextRank;
		/** How many drops are on record, and their total GE value. */
		private Long drops;
		private Long dropValue;
		private Integer clogObtained;
		private Integer pbs;
		private Integer deaths;
	}

	@Data
	static class NextRank
	{
		private String name;
		private Integer daysRemaining;
		private Long creditRemaining;
	}

	/** An event's sign-up state, from GET /plugin/events. */
	@Data
	static class Signup
	{
		private boolean open;
		private String closesAt;
		private Integer count;
		private Integer cap;
		/** none, signedUp, waitlist or confirmed. */
		private String myStatus;
		/** Buy-in paid; null when there's no buy-in. */
		private Boolean paid;
		private String note;
		private List<Field> form;

		boolean isSignedUp()
		{
			return myStatus != null && !"none".equals(myStatus);
		}

		Instant closes()
		{
			return parse(closesAt);
		}
	}

	/** One question on an event's sign-up form. */
	@Data
	static class Field
	{
		private String key;
		private String label;
		/** text, number, choice, multi, timezone or caTier. */
		private String type;
		private boolean required;
		private Double min;
		private Double max;
		private List<String> options;
	}

	/** GET /plugin/events/{id}/signups */
	@Data
	static class SignupList
	{
		private Integer count;
		private Integer cap;
		private List<SignupEntry> signups;
	}

	@Data
	static class SignupEntry
	{
		private String rsn;
		private String team;
		private String status;
		private Boolean paid;
	}

	/** Reply to POST or DELETE /plugin/events/{id}/signup. */
	@Data
	static class SignupResult
	{
		private String myStatus;
	}

	/** GET /plugin/events/{id}/team, and each entry of /teams. */
	@Data
	static class Team
	{
		private String id;
		private String name;
		/** "#rrggbb", optional. */
		private String colour;
		private String captain;
		/** Short badge text such as "A" or "2", optional; the plugin picks one when it's missing. */
		private String tag;
		/** RSNs. On /team, and on each team of /teams so the plugin can show every team and badge its members. */
		private List<String> members;
		private Integer memberCount;
		private Double points;
		/** What points are called for this event, e.g. "notches", "gp", "tiles". */
		private String pointsLabel;
		private Integer rank;
	}

	/** GET /plugin/events/{id}/teams */
	@Data
	static class TeamList
	{
		private List<Team> teams;
	}

	/** GET /plugin/events/{id}/board */
	@Data
	static class Board
	{
		private List<Tile> tiles;
		private Integer done;
		private Integer total;
	}

	/** GET /plugin/events/{id}/boards: every team's board. */
	@Data
	static class BoardList
	{
		private List<TeamBoard> boards;
	}

	@Data
	@lombok.EqualsAndHashCode(callSuper = true)
	static class TeamBoard extends Board
	{
		private String teamId;
	}

	@Data
	static class Tile
	{
		private String id;
		private String name;
		private String description;
		/** Heading the tile sits under, e.g. "Ring 2" or "Chambers of Xeric". Optional. */
		private String group;
		/** needed, pending, accepted (or completed) or declined. Accepted means the whole tile is done. */
		private String state;
		private Double points;
		/** Tiles that take several items: how many are accepted, how many are needed, and how many wait for review. */
		private Integer have;
		private Integer need;
		private Integer pending;
		/** The latest item sent for this tile. */
		private TileItem latest;
		/** "all": every listed item is needed. "any": one of them completes the tile. */
		private String mode;
		/** The items that count for this tile, each with its own count. */
		private List<TileNeed> items;

		/** Items accepted so far: the tile's own count, else worked out from its items. */
		Integer accepted()
		{
			if (have != null || need != null)
			{
				return have;
			}
			int[] counts = fromItems();
			return counts != null ? counts[0] : null;
		}

		/** Items needed: the tile's own count, else worked out from its items. */
		Integer needed()
		{
			if (have != null || need != null)
			{
				return need;
			}
			int[] counts = fromItems();
			return counts != null ? counts[1] : null;
		}

		/** {have, need} from the items: summed for "all", the closest item for "any". */
		private int[] fromItems()
		{
			if (items == null || items.isEmpty())
			{
				return null;
			}
			boolean any = "any".equalsIgnoreCase(mode);
			int haveSum = 0;
			int needSum = 0;
			int[] best = null;
			for (TileNeed item : items)
			{
				int n = Math.max(item.getNeed() != null ? item.getNeed() : 1, 1);
				int h = Math.min(Math.max(item.getHave() != null ? item.getHave() : 0, 0), n);
				haveSum += h;
				needSum += n;
				if (best == null || (double) h / n > (double) best[0] / best[1])
				{
					best = new int[]{h, n};
				}
			}
			return any ? best : new int[]{haveSum, needSum};
		}

		boolean isComplete()
		{
			Integer h = accepted();
			Integer n = needed();
			return "accepted".equals(state) || "completed".equals(state) || (h != null && n != null && h >= n);
		}

		/** "2/3" for tiles that take more than one item, otherwise null. */
		String progress()
		{
			Integer h = accepted();
			Integer n = needed();
			return n != null && n > 1 ? (h != null ? h : 0) + "/" + n : null;
		}
	}

	/** GET /plugin/events/{id}/drops: the event's recent drops, newest first. */
	@Data
	static class DropList
	{
		private List<Drop> drops;
	}

	/** A drop sent for an event, and what became of it. */
	@Data
	static class Drop
	{
		private String id;
		private String item;
		private Integer itemId;
		private Integer quantity;
		private String rsn;
		private String teamId;
		/** ISO-8601 time of the drop. */
		private String at;
		/** The tile it was put on, once one is chosen. */
		private String tileId;
		private String tileName;
		/** pending, accepted, declined, choose (it fits several tiles and its owner must pick one) or unmatched. */
		private String state;
		/** True when this member is the one who must pick the tile. */
		private Boolean canChoose;
		/** The tiles it could go on, when state is choose. */
		private List<TileOption> options;

		/** This member must pick which tile the drop counts for. */
		boolean needsChoice()
		{
			return "choose".equals(state) && Boolean.TRUE.equals(canChoose) && options != null && options.size() > 1;
		}

		Instant time()
		{
			return parse(at);
		}
	}

	@Data
	static class TileOption
	{
		private String tileId;
		private String name;
	}

	/** POST /plugin/events/{id}/drops/{dropId}/tile: the drop after the member picked its tile. */
	@Data
	static class TileChoice
	{
		private String tileId;
	}

	/** One item a tile asks for, and how many of it are accepted. */
	@Data
	static class TileNeed
	{
		private Integer itemId;
		private String name;
		private Integer have;
		private Integer need;
	}

	@Data
	static class TileItem
	{
		private String item;
		private String rsn;
	}

	/** GET /plugin/announcements */
	@Data
	static class AnnouncementList
	{
		private List<Announcement> announcements;
	}

	@Data
	static class Announcement
	{
		private long id;
		private String title;
		private String body;
		private String url;
		private String eventId;
		private String postedAt;
		private String postedBy;

		Instant posted()
		{
			return parse(postedAt);
		}
	}

	static Instant parse(String iso)
	{
		if (iso == null)
		{
			return null;
		}
		try
		{
			return Instant.parse(iso);
		}
		catch (java.time.format.DateTimeParseException e)
		{
			return null;
		}
	}
}
