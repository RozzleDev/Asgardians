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
		/** RSNs. Only on /team. */
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

	@Data
	static class Tile
	{
		private String id;
		private String name;
		private String description;
		/** Heading the tile sits under, e.g. "Ring 2" or "Chambers of Xeric". Optional. */
		private String group;
		/** needed, pending, accepted or declined. */
		private String state;
		private Double points;
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
