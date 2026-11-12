package com.asgardians;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.util.QuantityFormatter;

/**
 * The "!asg" chat commands. Like the Chat Commands plugin, whoever types one sends it as normal chat, and every clanmate
 * running the plugin sees the line replaced with the answer on their own screen. Clan-wide answers come from what the
 * reader's plugin has already loaded; answers about the player who typed it come from their clan profile.
 * Pure, so it can be tested.
 */
final class AsgCommands
{
	static final String PREFIX = "!asg";

	/** Answers about the player who typed the command, from their clan profile. */
	static final List<String> PERSONAL = List.of("credit", "clancredit", "rank", "ehb", "ehp", "profile", "eggs");
	static final List<String> PERIODS = List.of("week", "month", "year", "all");
	private static final int TOP = 3;

	private AsgCommands()
	{
	}

	/** What the reader's plugin has loaded. Any part may be null or empty. */
	@Value
	static class Clan
	{
		ClanData.GroupList groups;
		List<ClanData.GroupActivity> activities;
		ClanView view;
		/** Leaderboards by period: week, month, year, all. */
		Map<String, List<ClanData.Leaderboard>> boards;
		ClanData.StatsFeed stats;
		Instant now;
	}

	/** The command word, lower case: "groups" for "!asg Groups week". Empty for "!asg" alone. */
	static String word(String args)
	{
		String[] parts = args.trim().split("\\s+", 2);
		return parts[0].toLowerCase(Locale.UK);
	}

	/** What follows the command word: "week" for "!asg drops week". */
	static String rest(String args)
	{
		String[] parts = args.trim().split("\\s+", 2);
		return parts.length > 1 ? parts[1].trim() : "";
	}

	static boolean personal(String word)
	{
		return PERSONAL.contains(word);
	}

	/**
	 * The answer to a clan-wide command, or null to leave the line as typed (an unknown command, or nothing loaded).
	 *
	 * @param args   what followed "!asg"
	 * @param sender the RSN of whoever typed it
	 */
	static String answer(String args, String sender, Clan clan)
	{
		String word = word(args);
		String rest = rest(args);
		switch (word)
		{
			case "":
			case "help":
				return help();
			case "groups":
			case "group":
				return groups(clan);
			case "events":
			case "event":
				return events(clan);
			case "bingo":
				return bingo(clan);
			case "sotw":
				return competition(clan, "sotw", "Skill of the Week");
			case "botw":
				return competition(clan, "botw", "Boss of the Week");
			case "news":
				return news(clan);
			case "drops":
				return drops(clan, rest);
			case "cabbage":
			case "cabbages":
				return cabbages(clan, rest);
			case "pb":
				return pb(clan, rest, sender);
			default:
				return null;
		}
	}

	static String help()
	{
		return new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("Asgardians: ").append(ChatColorType.NORMAL)
			.append("!asg groups, events, bingo, sotw, botw, news, drops [week/month/year/all], cabbage, pb [boss], credit, rank, ehb, ehp, profile, eggs")
			.build();
	}

	static String groups(Clan clan)
	{
		ClanData.GroupList list = clan.getGroups();
		if (list == null)
		{
			return null;
		}
		List<ClanData.Group> open = new ArrayList<>();
		for (ClanData.Group g : ClanView.orEmpty(list.getGroups()))
		{
			if (g != null && g.getId() != null)
			{
				open.add(g);
			}
		}
		ChatMessageBuilder b = start("Open groups (" + open.size() + ")");
		if (open.isEmpty())
		{
			return b.append("none right now. Start one in the Asgardians panel.").build();
		}
		for (int i = 0; i < Math.min(open.size(), 4); i++)
		{
			ClanData.Group g = open.get(i);
			b.append(i > 0 ? " · " : "").append(ChatColorType.HIGHLIGHT)
				.append(Groups.shortTitle(g, GroupTabs.activity(g, clan.getActivities())))
				.append(ChatColorType.NORMAL).append(" " + g.seats() + (g.getWorld() != null ? " W" + g.getWorld() : "")
					+ (g.getHost() != null ? " (" + g.getHost() + ")" : ""));
		}
		if (open.size() > 4)
		{
			b.append(" · +" + (open.size() - 4) + " more");
		}
		return b.build();
	}

	static String events(Clan clan)
	{
		ClanView view = clan.getView();
		if (view == null || view.getFetchedAt() == null)
		{
			return null;
		}
		List<ClanView.EventView> live = view.live();
		List<ClanView.EventView> next = view.upcoming();
		ChatMessageBuilder b = start("Events");
		if (live.isEmpty() && next.isEmpty())
		{
			return b.append("nothing on right now.").build();
		}
		boolean first = true;
		if (!live.isEmpty())
		{
			b.append("Live: ");
			for (int i = 0; i < Math.min(live.size(), 2); i++)
			{
				ClanData.Event e = live.get(i).getEvent();
				b.append(i > 0 ? ", " : "").append(ChatColorType.HIGHLIGHT).append(name(e)).append(ChatColorType.NORMAL)
					.append(e.end() != null ? " (" + ClanView.countdown(clan.getNow(), e.end()) + " left)" : "");
			}
			first = false;
		}
		if (!next.isEmpty())
		{
			b.append(first ? "Next: " : " · Next: ");
			for (int i = 0; i < Math.min(next.size(), 2); i++)
			{
				ClanData.Event e = next.get(i).getEvent();
				b.append(i > 0 ? ", " : "").append(ChatColorType.HIGHLIGHT).append(name(e)).append(ChatColorType.NORMAL)
					.append(e.start() != null ? " in " + ClanView.countdown(clan.getNow(), e.start()) : "");
			}
		}
		return b.build();
	}

	static String bingo(Clan clan)
	{
		ClanView view = clan.getView();
		if (view == null || view.getFetchedAt() == null)
		{
			return null;
		}
		for (ClanView.EventView v : view.live())
		{
			if (!"bingo".equalsIgnoreCase(v.getEvent().getType()))
			{
				continue;
			}
			ChatMessageBuilder b = start(name(v.getEvent()));
			List<ClanData.Team> teams = new ArrayList<>(ClanView.orEmpty(v.getStandings()));
			teams.removeIf(t -> t == null || t.getName() == null);
			teams.sort(java.util.Comparator.comparing((ClanData.Team t) -> t.getRank() != null ? t.getRank() : Integer.MAX_VALUE)
				.thenComparing(t -> -(t.getPoints() != null ? t.getPoints() : 0)));
			if (teams.isEmpty())
			{
				return b.append("no scores yet.").build();
			}
			for (int i = 0; i < Math.min(teams.size(), TOP); i++)
			{
				ClanData.Team t = teams.get(i);
				String label = t.getPointsLabel() != null ? t.getPointsLabel() : "pts";
				b.append(i > 0 ? " · " : "").append((i + 1) + ". ").append(ChatColorType.HIGHLIGHT).append(t.getName())
					.append(ChatColorType.NORMAL).append(t.getPoints() != null ? " " + number(t.getPoints()) + " " + label : "");
			}
			return b.build();
		}
		return start("Bingo").append("no bingo on right now.").build();
	}

	/** The current SOTW or BOTW: its live event's standings, else the leaderboard named after it. */
	static String competition(Clan clan, String type, String title)
	{
		ClanView view = clan.getView();
		for (ClanView.EventView v : view != null ? view.live() : List.<ClanView.EventView>of())
		{
			ClanData.Event e = v.getEvent();
			if (type.equalsIgnoreCase(e.getType()))
			{
				ChatMessageBuilder b = start(name(e) + (e.end() != null ? " (" + ClanView.countdown(clan.getNow(), e.end()) + " left)" : ""));
				return rows(b, e.getStandings()).build();
			}
		}
		for (ClanData.Leaderboard board : boards(clan, "week"))
		{
			if (board.getTitle() != null && (board.getTitle().toLowerCase(Locale.UK).startsWith(type)
				|| board.getTitle().toLowerCase(Locale.UK).startsWith(title.toLowerCase(Locale.UK))))
			{
				ChatMessageBuilder b = start(board.getTitle() + (board.ends() != null ? " (" + ClanView.countdown(clan.getNow(), board.ends()) + " left)" : ""));
				return rows(b, board.getRows()).build();
			}
		}
		if (view == null || view.getFetchedAt() == null)
		{
			return null;
		}
		return start(title).append("none running right now.").build();
	}

	static String news(Clan clan)
	{
		ClanView view = clan.getView();
		if (view == null || view.getFetchedAt() == null)
		{
			return null;
		}
		List<ClanData.Announcement> news = ClanView.orEmpty(view.getAnnouncements());
		if (news.isEmpty() || news.get(0) == null || news.get(0).getTitle() == null)
		{
			return start("News").append("nothing new.").build();
		}
		ClanData.Announcement a = news.get(0);
		String ago = ClanView.ago(a.posted(), clan.getNow());
		return start("Latest news").append(ChatColorType.HIGHLIGHT).append(a.getTitle()).append(ChatColorType.NORMAL)
			.append(ago.isEmpty() ? "" : " (" + ago + ")").build();
	}

	/** The top of the Drops board for a period: this month unless week, year or all is asked for. */
	static String drops(Clan clan, String rest)
	{
		String period = period(rest);
		for (ClanData.Leaderboard board : boards(clan, period))
		{
			if (board.getTitle() != null && board.getTitle().toLowerCase(Locale.UK).startsWith("drop"))
			{
				return rows(start("Drops " + periodLabel(period)), board.getRows()).build();
			}
		}
		return null;
	}

	/**
	 * The Cabbage high score: who's had the most consolation cabbages from the Fractured Archive's chest, from the
	 * board titled "Cabbages". This month unless week, year or all is asked for.
	 */
	static String cabbages(Clan clan, String rest)
	{
		String period = period(rest);
		for (ClanData.Leaderboard board : boards(clan, period))
		{
			if (board.getTitle() != null && board.getTitle().toLowerCase(Locale.UK).startsWith("cabbage"))
			{
				return rows(start("Cabbage high score " + periodLabel(period)), board.getRows()).build();
			}
		}
		return null;
	}

	/**
	 * The clan's best time for a boss at each team size, and the sender's own best. "!asg pb tob", "!asg pb tob 4",
	 * "!asg pb vorkath".
	 */
	static String pb(Clan clan, String rest, String sender)
	{
		ClanData.StatsFeed stats = clan.getStats();
		if (stats == null || stats.getRecords() == null || rest.isEmpty())
		{
			return rest.isEmpty() ? start("PBs").append("try !asg pb [boss], e.g. !asg pb tob").build() : null;
		}
		String size = null;
		String bossText = rest;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)\\s+(solo|\\d+(?:-\\d+)?)(?:\\s*-?man)?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rest);
		if (m.matches())
		{
			bossText = m.group(1);
			size = m.group(2).equalsIgnoreCase("solo") ? "1" : m.group(2);
		}
		String boss = boss(bossText, stats.getRecords());
		if (boss == null)
		{
			return start("PBs").append("no clan PBs for \"" + rest + "\".").build();
		}
		ChatMessageBuilder b = start(boss + " PBs");
		int shown = 0;
		String mine = null;
		for (ClanData.PbRecord r : stats.getRecords())
		{
			if (r == null || !boss.equals(r.getBoss()) || (size != null && !size.equals(r.getTeamSize())))
			{
				continue;
			}
			List<ClanData.StatEntry> entries = ClanView.orEmpty(r.getEntries());
			if (!entries.isEmpty() && entries.get(0).getSeconds() != null && shown < 4)
			{
				ClanData.StatEntry best = entries.get(0);
				b.append(shown > 0 ? " · " : "").append(team(r.getTeamSize()) + " ").append(ChatColorType.HIGHLIGHT)
					.append(StatsTabs.time(best.getSeconds())).append(ChatColorType.NORMAL).append(" " + best.getRsn());
				shown++;
			}
			for (int i = 0; i < entries.size(); i++)
			{
				ClanData.StatEntry e = entries.get(i);
				if (mine == null && e.getSeconds() != null && DropRecorder.sameRsn(e.getRsn(), sender))
				{
					mine = sender + ": " + StatsTabs.time(e.getSeconds()) + " (" + team(r.getTeamSize()) + ", #" + (i + 1) + ")";
				}
			}
		}
		if (shown == 0)
		{
			return b.append("none yet" + (size != null ? " for " + team(size) : "") + ".").build();
		}
		if (mine != null)
		{
			b.append(" | " + mine);
		}
		return b.build();
	}

	/**
	 * The answer to a command about the player who typed it, from their clan profile; null when there's nothing to say.
	 */
	static String personal(String word, String sender, ClanData.Me p)
	{
		if (p == null)
		{
			return null;
		}
		switch (word)
		{
			case "credit":
			case "clancredit":
			{
				ChatMessageBuilder b = start("Clan credit");
				if (p.getSplitCredit() == null)
				{
					return b.append("none on record.").build();
				}
				b.append(ChatColorType.HIGHLIGHT).append(ProfileText.amount(p.getSplitCredit())).append(ChatColorType.NORMAL);
				ClanData.ProfileRank place = ranking(p, "credit");
				return b.append(place != null && place.getRank() != null ? " (#" + place.getRank() + " in the clan)" : "").build();
			}
			case "rank":
				return start("Rank").append(ChatColorType.HIGHLIGHT).append(ProfileText.rank(p.getRank())).append(ChatColorType.NORMAL)
					.append(p.getDaysInClan() != null ? " · in the clan " + ProfileText.time(p.getDaysInClan()) : "").build();
			case "ehb":
				return hours("EHB", p.getEhb(), ranking(p, "ehb"));
			case "ehp":
				return hours("EHP", p.getEhp(), ranking(p, "ehp"));
			case "eggs":
				if (p.getEggsThrown() == null && p.getEggsReceived() == null)
				{
					return start("Handeggs").append("none on record.").build();
				}
				return start("Handeggs").append(ChatColorType.HIGHLIGHT)
					.append((p.getEggsThrown() != null ? p.getEggsThrown() : 0) + " thrown").append(ChatColorType.NORMAL)
					.append(" · hit " + (p.getEggsReceived() != null ? p.getEggsReceived() : 0) + " times").build();
			case "profile":
			{
				List<String> parts = new ArrayList<>();
				parts.add(ProfileText.rank(p.getRank()));
				if (p.getDaysInClan() != null)
				{
					parts.add(ProfileText.time(p.getDaysInClan()));
				}
				if (p.getSplitCredit() != null)
				{
					parts.add("Credit " + ProfileText.amount(p.getSplitCredit()));
				}
				if (p.getEhb() != null)
				{
					parts.add("EHB " + number(p.getEhb()));
				}
				if (p.getEhp() != null)
				{
					parts.add("EHP " + number(p.getEhp()));
				}
				if (p.getClogObtained() != null)
				{
					parts.add("Log " + p.getClogObtained() + (p.getClogTotal() != null ? "/" + p.getClogTotal() : ""));
				}
				return start("Profile").append(ChatColorType.HIGHLIGHT).append(String.join(" · ", parts)).build();
			}
			default:
				return null;
		}
	}

	private static String hours(String label, Double value, ClanData.ProfileRank place)
	{
		ChatMessageBuilder b = start(label);
		if (value == null)
		{
			return b.append("not on record.").build();
		}
		return b.append(ChatColorType.HIGHLIGHT).append(number(value)).append(ChatColorType.NORMAL)
			.append(place != null && place.getRank() != null ? " (#" + place.getRank() + " in the clan)" : "").build();
	}

	/** The profile's place on a board whose name contains the word, e.g. "Clan credit" for "credit". */
	private static ClanData.ProfileRank ranking(ClanData.Me p, String word)
	{
		for (ClanData.ProfileRank r : ClanView.orEmpty(p.getRankings()))
		{
			if (r != null && r.getBoard() != null && r.getBoard().toLowerCase(Locale.UK).contains(word))
			{
				return r;
			}
		}
		return null;
	}

	/**
	 * The boss a member means: a full name, its initials ("tob", "cox", "toa", "cg"), or the start of a name. Only
	 * bosses the clan has PBs for count.
	 */
	static String boss(String text, List<ClanData.PbRecord> records)
	{
		String want = text.trim().toLowerCase(Locale.UK).replace("'", "");
		List<String> names = new ArrayList<>();
		for (ClanData.PbRecord r : ClanView.orEmpty(records))
		{
			if (r != null && r.getBoss() != null && !names.contains(r.getBoss()))
			{
				names.add(r.getBoss());
			}
		}
		for (String n : names)
		{
			if (n.toLowerCase(Locale.UK).replace("'", "").equals(want))
			{
				return n;
			}
		}
		for (String n : names)
		{
			if (initials(n).equals(want))
			{
				return n;
			}
		}
		String alias = ALIASES.get(want);
		for (String n : names)
		{
			if (alias != null && n.toLowerCase(Locale.UK).contains(alias))
			{
				return n;
			}
		}
		for (String n : names)
		{
			if (want.length() >= 3 && n.toLowerCase(Locale.UK).replace("'", "").startsWith(want))
			{
				return n;
			}
		}
		for (String n : names)
		{
			if (want.length() >= 4 && n.toLowerCase(Locale.UK).replace("'", "").contains(want))
			{
				return n;
			}
		}
		return null;
	}

	/** "Theatre of Blood" becomes "tob", "The Corrupted Gauntlet" "cg", "Chambers of Xeric: Challenge Mode" "coxcm". */
	private static String initials(String name)
	{
		StringBuilder out = new StringBuilder();
		for (String w : name.toLowerCase(Locale.UK).replace(":", " ").split("[\\s-]+"))
		{
			if (!w.isEmpty() && !w.equals("the"))
			{
				out.append(w.charAt(0));
			}
		}
		return out.toString();
	}

	/** Usual short names that aren't a boss's initials, to a word in its name. */
	private static final Map<String, String> ALIASES = Map.of(
		"cm", "challenge mode",
		"colo", "colosseum",
		"zuk", "inferno",
		"jad", "fight caves",
		"nm", "nightmare",
		"hmt", "hard mode",
		"sire", "abyssal sire",
		"hydra", "hydra",
		"muspah", "muspah",
		"gauntlet", "gauntlet");

	private static String period(String rest)
	{
		String p = rest.trim().toLowerCase(Locale.UK);
		if (p.equals("alltime") || p.equals("all-time") || p.equals("all time"))
		{
			return "all";
		}
		return PERIODS.contains(p) ? p : "month";
	}

	private static String periodLabel(String period)
	{
		switch (period)
		{
			case "week":
				return "this week";
			case "year":
				return "this year";
			case "all":
				return "all time";
			default:
				return "this month";
		}
	}

	private static List<ClanData.Leaderboard> boards(Clan clan, String period)
	{
		Map<String, List<ClanData.Leaderboard>> boards = clan.getBoards();
		List<ClanData.Leaderboard> list = boards != null ? boards.get(period) : null;
		List<ClanData.Leaderboard> out = new ArrayList<>();
		for (ClanData.Leaderboard b : ClanView.orEmpty(list))
		{
			if (b != null)
			{
				out.add(b);
			}
		}
		return out;
	}

	private static ChatMessageBuilder rows(ChatMessageBuilder b, List<ClanData.LeaderboardRow> rows)
	{
		List<ClanData.LeaderboardRow> list = ClanView.orEmpty(rows);
		if (list.isEmpty())
		{
			return b.append("no entries yet.");
		}
		for (int i = 0; i < Math.min(list.size(), TOP); i++)
		{
			ClanData.LeaderboardRow r = list.get(i);
			b.append(i > 0 ? " · " : "").append((r.getRank() != null ? r.getRank() : i + 1) + ". ").append(ChatColorType.HIGHLIGHT)
				.append(r.getRsn() != null ? r.getRsn() : "?").append(ChatColorType.NORMAL)
				.append(r.getValue() != null ? " " + r.getValue() : "");
		}
		return b;
	}

	private static ChatMessageBuilder start(String title)
	{
		return new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(title + ": ").append(ChatColorType.NORMAL);
	}

	private static String name(ClanData.Event e)
	{
		return e.getName() != null ? e.getName() : "Event";
	}

	private static String team(String size)
	{
		return size == null ? "" : "1".equals(size) ? "Solo" : size + "-man";
	}

	private static String number(double value)
	{
		return value == Math.rint(value) ? QuantityFormatter.formatNumber((long) value) : String.format(Locale.UK, "%,.1f", value);
	}
}
