package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.runelite.client.util.Text;
import org.junit.Test;

public class AsgCommandsTest
{
	private static final Gson GSON = new Gson();
	private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

	private static String plain(String s)
	{
		return s == null ? null : Text.removeTags(s);
	}

	private static List<ClanData.Leaderboard> boards(String json)
	{
		return GSON.fromJson("{\"boards\":" + json + "}", ClanData.LeaderboardList.class).getBoards();
	}

	private static AsgCommands.Clan clan(ClanData.GroupList groups, ClanView view, Map<String, List<ClanData.Leaderboard>> boards,
		ClanData.StatsFeed stats)
	{
		return new AsgCommands.Clan(groups, List.of(), view, boards, stats, NOW);
	}

	private static ClanView view(String... events)
	{
		List<ClanView.EventView> list = new java.util.ArrayList<>();
		for (String e : events)
		{
			list.add(new ClanView.EventView(GSON.fromJson(e, ClanData.Event.class), null, List.of(), null, null, null));
		}
		return new ClanView(list, List.of(), NOW, null);
	}

	@Test
	public void unknownCommandsAreLeftAlone()
	{
		AsgCommands.Clan c = clan(null, null, Map.of(), null);
		assertNull(AsgCommands.answer("dance", "Rozzle", c));
		assertNull(AsgCommands.answer("groups", "Rozzle", c));
		assertEquals("Asgardians: !asg groups, events, bingo, sotw, botw, news, drops [week/month/year/all], cabbage, pb [boss], credit, rank, ehb, ehp, profile, eggs",
			plain(AsgCommands.answer("", "Rozzle", c)));
	}

	@Test
	public void groups()
	{
		ClanData.GroupList list = GroupsTest.list("{\"groups\":[{\"id\":\"g1\",\"name\":\"Theatre of Blood\",\"mode\":\"Hard mode\",\"size\":4,"
			+ "\"world\":416,\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"},{\"rsn\":\"Rozzle\"}]},"
			+ "{\"id\":\"g2\",\"name\":\"Nex\",\"size\":6,\"world\":302,\"host\":\"pricey\",\"members\":[{\"rsn\":\"pricey\"}]}]}");
		assertEquals("Open groups (2): Theatre of Blood (Hard mode) 2/4 W416 (Clo) · Nex 1/6 W302 (pricey)",
			plain(AsgCommands.answer("Groups", "Rozzle", clan(list, null, Map.of(), null))));
		assertEquals("Open groups (0): none right now. Start one in the Asgardians panel.",
			plain(AsgCommands.answer("groups", "Rozzle", clan(GroupsTest.list("{\"groups\":[]}"), null, Map.of(), null))));
	}

	@Test
	public void eventsBingoAndCompetitions()
	{
		ClanView v = view("{\"name\":\"SOTW Slayer\",\"type\":\"sotw\",\"status\":\"live\",\"endsAt\":\"2026-11-13T16:00:00Z\","
				+ "\"standings\":[{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"3.2M\"},{\"rank\":2,\"rsn\":\"Odin Jr\",\"value\":\"2.4M\"}]}",
			"{\"name\":\"Bingo #4\",\"type\":\"bingo\",\"status\":\"upcoming\",\"startsAt\":\"2026-11-14T19:00:00Z\"}");
		AsgCommands.Clan c = clan(null, v, Map.of(), null);
		assertEquals("Events: Live: SOTW Slayer (2d 4h left) · Next: Bingo #4 in 3d 7h", plain(AsgCommands.answer("events", "Rozzle", c)));
		assertEquals("SOTW Slayer (2d 4h left): 1. Rozzle 3.2M · 2. Odin Jr 2.4M", plain(AsgCommands.answer("sotw", "Rozzle", c)));
		assertEquals("Boss of the Week: none running right now.", plain(AsgCommands.answer("botw", "Rozzle", c)));
		assertEquals("Bingo: no bingo on right now.", plain(AsgCommands.answer("bingo", "Rozzle", c)));

		ClanData.Event bingo = GSON.fromJson("{\"name\":\"Bingo #4\",\"type\":\"bingo\",\"status\":\"live\"}", ClanData.Event.class);
		List<ClanData.Team> teams = GSON.fromJson("{\"teams\":[{\"name\":\"Team Thor\",\"points\":38,\"rank\":2},"
			+ "{\"name\":\"Team Odin\",\"points\":42,\"rank\":1},{\"name\":\"Team Loki\",\"points\":30,\"rank\":3}]}", ClanData.TeamList.class).getTeams();
		ClanView live = new ClanView(List.of(new ClanView.EventView(bingo, null, teams, null, null, null)), List.of(), NOW, null);
		assertEquals("Bingo #4: 1. Team Odin 42 pts · 2. Team Thor 38 pts · 3. Team Loki 30 pts",
			plain(AsgCommands.answer("bingo", "Rozzle", clan(null, live, Map.of(), null))));
	}

	@Test
	public void dropsByPeriod()
	{
		Map<String, List<ClanData.Leaderboard>> b = Map.of(
			"month", boards("[{\"title\":\"Drops\",\"rows\":[{\"rank\":1,\"rsn\":\"Clo\",\"value\":\"412M\"},{\"rank\":2,\"rsn\":\"Rozzle\",\"value\":\"210M\"}]}]"),
			"week", boards("[{\"title\":\"Drops\",\"rows\":[]}]"),
			"all", boards("[{\"title\":\"Drops\",\"rows\":[{\"rank\":1,\"rsn\":\"pricey\",\"value\":\"9.1B\"}]}]"));
		AsgCommands.Clan c = clan(null, null, b, null);
		assertEquals("Drops this month: 1. Clo 412M · 2. Rozzle 210M", plain(AsgCommands.answer("drops", "Rozzle", c)));
		assertEquals("Drops this week: no entries yet.", plain(AsgCommands.answer("drops week", "Rozzle", c)));
		assertEquals("Drops all time: 1. pricey 9.1B", plain(AsgCommands.answer("drops all", "Rozzle", c)));
		assertNull(AsgCommands.answer("drops year", "Rozzle", c));

		Map<String, List<ClanData.Leaderboard>> cab = Map.of("month",
			boards("[{\"title\":\"Cabbages\",\"rows\":[{\"rank\":1,\"rsn\":\"Thor\",\"value\":\"7\"},{\"rank\":2,\"rsn\":\"Clo\",\"value\":\"3\"}]}]"));
		assertEquals("Cabbage high score this month: 1. Thor 7 · 2. Clo 3",
			plain(AsgCommands.answer("cabbage", "Rozzle", clan(null, null, cab, null))));
		assertNull(AsgCommands.answer("cabbage", "Rozzle", c));
	}

	@Test
	public void personalBests()
	{
		ClanData.StatsFeed stats = GSON.fromJson("{\"records\":["
			+ "{\"boss\":\"Theatre of Blood\",\"teamSize\":\"4\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":892.4},{\"rsn\":\"Thor\",\"seconds\":900},{\"rsn\":\"Rozzle\",\"seconds\":930}]},"
			+ "{\"boss\":\"Theatre of Blood\",\"teamSize\":\"5\",\"entries\":[{\"rsn\":\"Thor\",\"seconds\":820}]},"
			+ "{\"boss\":\"Chambers of Xeric: Challenge Mode\",\"teamSize\":\"3\",\"entries\":[{\"rsn\":\"pricey\",\"seconds\":1800}]},"
			+ "{\"boss\":\"Vorkath\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Rozzle\",\"seconds\":61.2}]}]}", ClanData.StatsFeed.class);
		AsgCommands.Clan c = clan(null, null, Map.of(), stats);
		assertEquals("Theatre of Blood PBs: 4-man 14:52.40 Clo · 5-man 13:40 Thor | Rozzle: 15:30 (4-man, #3)",
			plain(AsgCommands.answer("pb tob", "Rozzle", c)));
		assertEquals("Theatre of Blood PBs: 5-man 13:40 Thor", plain(AsgCommands.answer("pb tob 5", "Rozzle", c)));
		assertEquals("Vorkath PBs: Solo 1:01.20 Rozzle | Rozzle: 1:01.20 (Solo, #1)", plain(AsgCommands.answer("pb vork", "Rozzle", c)));
		assertEquals("Chambers of Xeric: Challenge Mode PBs: 3-man 30:00 pricey", plain(AsgCommands.answer("pb cm", "Rozzle", c)));
		assertEquals("PBs: no clan PBs for \"zulrah\".", plain(AsgCommands.answer("pb zulrah", "Rozzle", c)));
		assertEquals("PBs: try !asg pb [boss], e.g. !asg pb tob", plain(AsgCommands.answer("pb", "Rozzle", c)));
	}

	@Test
	public void answersAboutWhoeverTypedIt()
	{
		ClanData.Me p = GSON.fromJson("{\"rsn\":\"Rozzle\",\"rank\":\"deputy_owner\",\"daysInClan\":456,\"splitCredit\":1240000,"
			+ "\"ehb\":812.4,\"ehp\":1940,\"eggsThrown\":42,\"eggsReceived\":17,\"clogObtained\":812,\"clogTotal\":1568,"
			+ "\"rankings\":[{\"board\":\"Clan credit\",\"rank\":5,\"value\":\"1.2M\"}]}", ClanData.Me.class);
		assertEquals("Clan credit: 1.24M (#5 in the clan)", plain(AsgCommands.personal("clancredit", "Rozzle", p)));
		assertEquals("Rank: Deputy Owner · in the clan 1y 3m", plain(AsgCommands.personal("rank", "Rozzle", p)));
		assertEquals("EHB: 812.4", plain(AsgCommands.personal("ehb", "Rozzle", p)));
		assertEquals("EHP: 1,940", plain(AsgCommands.personal("ehp", "Rozzle", p)));
		assertEquals("Handeggs: 42 thrown · hit 17 times", plain(AsgCommands.personal("eggs", "Rozzle", p)));
		assertEquals("Profile: Deputy Owner · 1y 3m · Credit 1.24M · EHB 812.4 · EHP 1,940 · Log 812/1568",
			plain(AsgCommands.personal("profile", "Rozzle", p)));
		assertNull(AsgCommands.personal("credit", "Stranger", null));
	}
}
