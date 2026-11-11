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
		/**
		 * Boss kills only: each boss's own board, so the page can drill from all bosses into one. Optional. Each has
		 * the boss, its category and group (as on PB records), and its own rows and {@code me}.
		 */
		private List<BossBoard> bosses;
		/** XP only: each skill's own board, so the page can drill from all skills into one. Optional. */
		private List<SkillBoard> skills;

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

	/** One skill's XP, inside the XP board. */
	@Data
	@lombok.EqualsAndHashCode(callSuper = true)
	static class SkillBoard extends Leaderboard
	{
		/** e.g. "Slayer", as the game names it. */
		private String skill;
	}

	/** One boss's kills, inside the Boss kills board. */
	@Data
	@lombok.EqualsAndHashCode(callSuper = true)
	static class BossBoard extends Leaderboard
	{
		private String boss;
		private String category;
		private String group;
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
		/** BOTW, SOTW and competitions: the member's own result so far, e.g. "14 kills" or "3.2M XP". Optional. */
		private String myProgress;
		private Integer myRank;
		/** How many are taking part. Optional. */
		private Integer participants;
		/** The top of the competition, for the details page. Optional. */
		private List<LeaderboardRow> standings;
		/** e.g. "1st 50M · 2nd 25M · 3rd 10M". Optional. */
		private String prizes;
		/** The competition on Wise Old Man. Optional; only wiseoldman.net links are opened. */
		private String womUrl;

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
		/** Efficient hours bossed and played, from Wise Old Man via the website. Optional. */
		private Double ehb;
		private Double ehp;
		/** How many collection log slots there are, for the progress bar. Optional. */
		private Integer clogTotal;
		/** The member's pets, newest first. Optional. */
		private List<ProfileItem> pets;
		/** Events the member took part in, newest first, e.g. "Autumn Bingo" with "Team Freya · 1st". Optional. */
		private List<EventResult> events;
		/** The member's best times. Optional. */
		private List<StatEntry> personalBests;
		/** What killed the member last, and when (ISO-8601). Optional. */
		private String lastDeathBy;
		private String lastDeathAt;
		/** What has killed the member most. Optional. */
		private String topKiller;
		/** Handeggs thrown at and received from clanmates. Optional. */
		private Integer eggsThrown;
		private Integer eggsReceived;
		/** Leaderboard places this month, e.g. "Drop value" #8 "612M". Optional; the plugin uses its own boards for you. */
		private List<ProfileRank> rankings;

		Instant lastDeath()
		{
			return parse(lastDeathAt);
		}
	}

	@Data
	static class ProfileItem
	{
		private Integer itemId;
		private String name;
	}

	@Data
	static class EventResult
	{
		private String name;
		/** e.g. "Team Freya · 1st" or "4th · 3.2M XP". */
		private String result;
		/** The member's team won or placed first. */
		private boolean won;
	}

	@Data
	static class ProfileRank
	{
		private String board;
		private Integer rank;
		private String value;
	}

	/** GET /plugin/members/activity: what online clanmates are doing, for the Members page. */
	@Data
	static class MemberActivityList
	{
		private List<MemberActivity> members;
	}

	@Data
	static class MemberActivity
	{
		private String rsn;
		/** e.g. "Theatre of Blood", "Mining", "Grand Exchange"; null when they share nothing. */
		private String activity;
		private Integer world;
		/** When it was last reported (ISO-8601). */
		private String at;
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
		/** How many tiles across, e.g. 7 for a 7-wide board. Optional: a square count of tiles needs none. */
		private Integer columns;
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

	// ---- Groups (group finder) ----

	/** GET /plugin/groups/activities: what groups can be started for. */
	@Data
	static class GroupActivityList
	{
		private List<GroupActivity> activities;
	}

	@Data
	static class GroupActivity
	{
		private String id;
		private String name;
		/** Optional modes, e.g. "Normal" and "Hard mode". */
		private List<String> modes;
		private Integer minSize;
		private Integer maxSize;
		private Integer defaultSize;
		/** An item whose picture stands for the activity on group cards, e.g. Lil' Zik for ToB. Optional. */
		private Integer iconItemId;
		/** Offers 8+ even when maxSize is 8 or less (Nex, God Wars, Corp). */
		private boolean mass;
		/** Whether Split or FFA can be picked. */
		private boolean loot;
		/** RuneLite's kill count name for this content, e.g. "theatre of blood", so the member's KC can be shown. */
		private String kcName;
		/**
		 * New-group chat messages: "on" or "off" by default with the member's bell to change it, or "always" for every
		 * member. Missing means "on".
		 */
		private String alerts;
		/** A short name for chat messages and the on-screen alert, e.g. "ToB". Optional. */
		@com.google.gson.annotations.SerializedName("short")
		private String shortName;
		/** Short names for modes, e.g. {"Hard mode": "HM"}. Optional; a mode without one is shown in full. */
		private java.util.Map<String, String> modeShorts;
	}

	/** GET /plugin/groups, POST /plugin/groups/checkin, and the reply to every group action. */
	@Data
	static class GroupList
	{
		private List<Group> groups;
		/** The ID of the group this member is in, or null. */
		private String mine;
		/** Why this member is no longer in a group, once: away, kicked, closed or removed; or declined for a request. */
		private String reason;
		/** The ID of the group this member has asked to join and is waiting on the host for, or null. */
		private String waiting;

		Group mineGroup()
		{
			return find(mine);
		}

		Group waitingGroup()
		{
			return find(waiting);
		}

		Group find(String id)
		{
			if (id == null || groups == null)
			{
				return null;
			}
			for (Group g : groups)
			{
				if (id.equals(g.getId()))
				{
					return g;
				}
			}
			return null;
		}
	}

	@Data
	static class Group
	{
		private String id;
		private String activity;
		/** Display name, e.g. "Theatre of Blood". */
		private String name;
		private String mode;
		/** Seats; null for 8+ (with mass) or Any (without). */
		private Integer size;
		private boolean mass;
		/** split or ffa; null when the activity has no choice. */
		private String loot;
		private String note;
		private Integer world;
		/** RSN of the host. */
		private String host;
		private List<GroupMember> members;
		/** The RuneLite party passphrase. Only sent to members. */
		private String party;
		/** The group's Discord voice channel link, when one was made. Only sent to members. */
		private String voice;
		/** True when this member may close the group: its host, or a clan moderator. */
		private Boolean canClose;
		private String createdAt;
		/** The host picks who joins: Join sends a request, which the host accepts or declines. */
		private boolean approval;
		/** Players waiting for the host to accept them. Sent to the group's members. */
		private List<GroupMember> requests;

		/** "Theatre of Blood (Hard mode)". */
		String getTitle()
		{
			String base = name != null ? name : activity != null ? activity : "Group";
			return mode != null && !mode.trim().isEmpty() ? base + " (" + mode + ")" : base;
		}

		int count()
		{
			return members != null ? members.size() : 0;
		}

		boolean isFull()
		{
			return !mass && size != null && count() >= size;
		}

		/** "3/5", "3/8+" for a group of more than eight, or "3/any" when the host has no preference. */
		String seats()
		{
			return mass ? count() + "/8+" : size == null ? count() + "/any" : count() + "/" + size;
		}

		/** Whether a player is waiting for the host to accept them. */
		boolean asked(String rsn)
		{
			for (GroupMember m : requests != null ? requests : List.<GroupMember>of())
			{
				if (m != null && DropRecorder.sameRsn(m.getRsn(), rsn))
				{
					return true;
				}
			}
			return false;
		}

		boolean has(String rsn)
		{
			for (GroupMember m : members != null ? members : List.<GroupMember>of())
			{
				if (m != null && DropRecorder.sameRsn(m.getRsn(), rsn))
				{
					return true;
				}
			}
			return false;
		}
	}

	@Data
	static class GroupMember
	{
		private String rsn;
		/** Their kill count for the group's content, when their plugin knows it. */
		private Integer kc;
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

	/**
	 * GET /plugin/eggs?after=cursor: eggings since the cursor, oldest first. Without a cursor the website sends only
	 * the current cursor, so a member who logs in isn't shown old ones.
	 */
	@Data
	static class EggFeed
	{
		private String cursor;
		private List<EggAlert> eggs;
	}

	@Data
	static class EggAlert
	{
		private String id;
		private String thrower;
		private String target;
		private String at;
	}

	/**
	 * GET /plugin/stats/recent: the clan's latest drops, new collection log slots, live PBs and pets, newest first and
	 * at most 25 in each list. A list the website doesn't send is null.
	 */
	@Data
	static class StatsFeed
	{
		/** Every drop. */
		private List<StatEntry> drops;
		/** Drops worth 1m or more. */
		private List<StatEntry> drops1m;
		/** Drops worth 10m or more. */
		private List<StatEntry> drops10m;
		/** Drops worth 100m or more. */
		private List<StatEntry> drops100m;
		/** New collection log slots, as they're obtained (not log syncs). */
		private List<StatEntry> clogs;
		/** New PBs set in game (not chat commands or syncs). */
		private List<StatEntry> pbs;
		private List<StatEntry> pets;
		/** The clan's best times for each boss and team size, for the PBs page. Optional. */
		private List<PbRecord> records;

		/** True when the website sent at least one list, so it knows about clan stats. */
		boolean known()
		{
			return drops != null || drops1m != null || drops10m != null || drops100m != null || clogs != null || pbs != null || pets != null;
		}
	}

	/** The clan's top times for one boss and team size. */
	@Data
	static class PbRecord
	{
		/** e.g. "Theatre of Blood". */
		private String boss;
		/**
		 * The PBs page's first drop-down: Raids, Bosses, Skilling, Minigames, or any other name staff choose. When set,
		 * it replaces the plugin's own sorting for this boss.
		 */
		private String category;
		/** The second drop-down within the category, e.g. "Slayer" or "God Wars". Optional. */
		private String group;
		/** "1" for solo, or the team size. */
		private String teamSize;
		/** Fastest first. Each entry has rsn, seconds and at. */
		private List<StatEntry> entries;
	}

	/** One line of a clan stats list. Drops, clogs and pets use the item fields; PBs use boss, teamSize and seconds. */
	@Data
	static class StatEntry
	{
		private String id;
		private String rsn;
		/** Item or pet name. */
		private String item;
		private Integer itemId;
		private Integer quantity;
		/** Total GE value in coins. */
		private Long value;
		/** What it came from: "Vorkath", "Chambers of Xeric". */
		private String source;
		private Integer kc;
		/** PBs: the boss, "1" for solo or the team size ("3", "11-15"), and the time in seconds. */
		private String boss;
		private String teamSize;
		private Double seconds;
		/** Pets: already owned (a duplicate). */
		private Boolean duplicate;
		private String at;

		Instant time()
		{
			return parse(at);
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
