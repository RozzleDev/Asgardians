package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import org.junit.Test;

public class NewPagesTest
{
	private static final Gson GSON = new Gson();
	private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

	private static ActivityTracker.Snapshot snap(boolean cox, boolean tob, String target, int targetTick, int slayer, String skill,
		int skillTick, boolean bank, int region)
	{
		return new ActivityTracker.Snapshot(100, cox, tob, target, targetTick, slayer, skill, skillTick, bank, region);
	}

	@Test
	public void activityPrefersRaidsThenBossesThenSkills()
	{
		assertEquals("Chambers of Xeric", ActivityTracker.describe(snap(true, false, "Vorkath", 99, 0, "Mining", 99, false, 0)));
		assertEquals("Theatre of Blood", ActivityTracker.describe(snap(false, true, null, -1, 0, null, -1, false, 0)));
		assertEquals("Vorkath", ActivityTracker.describe(snap(false, false, "Vorkath", 90, 0, "Mining", 95, false, 0)));
		assertEquals("Tombs of Amascut", ActivityTracker.describe(snap(false, false, "Kephri", 90, 0, null, -1, false, 0)));
		assertEquals("Slayer · Abyssal demon", ActivityTracker.describe(snap(false, false, "Abyssal demon", 90, 40, null, -1, false, 0)));
		assertEquals("Mining", ActivityTracker.describe(snap(false, false, "Abyssal demon", 90, 0, "Mining", 95, false, 0)));
		assertEquals("Grand Exchange", ActivityTracker.describe(snap(false, false, null, -1, 0, null, -1, true, ActivityTracker.GE_REGION)));
		assertEquals("Banking", ActivityTracker.describe(snap(false, false, null, -1, 0, null, -1, true, 1)));
		// A fight or skill from long ago doesn't count.
		assertNull(ActivityTracker.describe(snap(false, false, "Vorkath", 10, 0, "Mining", 10, false, 1)));
	}

	@Test
	public void activitiesFallIntoTheMembersFilters()
	{
		assertEquals(ActivityTracker.RAIDS, ActivityTracker.category("Theatre of Blood"));
		assertEquals(ActivityTracker.BOSSING, ActivityTracker.category("Vorkath"));
		assertEquals(ActivityTracker.BOSSING, ActivityTracker.category("Slayer · Abyssal demon"));
		assertEquals(ActivityTracker.SKILLING, ActivityTracker.category("Mining"));
		assertEquals(ActivityTracker.OTHER, ActivityTracker.category("Grand Exchange"));
		assertEquals(ActivityTracker.OTHER, ActivityTracker.category(null));
		assertTrue(ActivityTracker.icon("Slayer · Abyssal demon") != null);
		assertNull(ActivityTracker.icon("Something new"));
	}

	@Test
	public void boardsAreSquaresAndLinesAreCounted()
	{
		assertEquals(5, ClanTabs.boardSide(25));
		assertEquals(7, ClanTabs.boardSide(49));
		assertEquals(0, ClanTabs.boardSide(24));
		assertEquals(0, ClanTabs.boardSide(1));
		List<ClanData.Tile> tiles = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			ClanData.Tile t = new ClanData.Tile();
			t.setId("t" + i);
			// Top row and the left column done.
			t.setState(i < 3 || i % 3 == 0 ? "accepted" : "needed");
			tiles.add(t);
		}
		Set<Integer> inLine = new HashSet<>();
		assertEquals(2, ClanTabs.finishedLines(tiles, 3, inLine));
		assertEquals(Set.of(0, 1, 2, 3, 6), inLine);
	}

	@Test
	public void bingoPageDrawsBoardsListsAndNothingRunning()
	{
		List<ClanData.Event> events = GSON.fromJson("{\"events\":[{\"id\":\"b\",\"name\":\"Bingo\",\"type\":\"bingo\",\"status\":\"live\","
			+ "\"teamId\":\"t1\",\"hasBoard\":true,\"endsAt\":\"2026-11-14T00:00:00Z\"},{\"id\":\"n\",\"name\":\"Next\",\"type\":\"bingo\","
			+ "\"status\":\"upcoming\",\"startsAt\":\"2026-11-20T00:00:00Z\"}]}", ClanData.EventList.class).getEvents();
		StringBuilder tiles = new StringBuilder();
		for (int i = 0; i < 49; i++)
		{
			tiles.append(i > 0 ? "," : "").append("{\"id\":\"t").append(i).append("\",\"name\":\"Tile ").append(i).append("\"")
				.append(i % 4 == 0 ? ",\"state\":\"accepted\"" : "").append(i % 5 == 0 ? ",\"items\":[{\"itemId\":4151,\"have\":1,\"need\":3}]" : "")
				.append("}");
		}
		ClanData.Board board = GSON.fromJson("{\"tiles\":[" + tiles + ",null]}".replace(",null", ""), ClanData.Board.class);
		ClanData.Team team = GSON.fromJson("{\"id\":\"t1\",\"name\":\"Team 1\",\"members\":[\"Rozzle\"]}", ClanData.Team.class);
		List<ClanData.Team> standings = GSON.fromJson("{\"teams\":[{\"id\":\"t1\",\"name\":\"Team 1\",\"rank\":1,\"points\":10},{\"id\":\"t2\"}]}",
			ClanData.TeamList.class).getTeams();
		ClanView view = new ClanView(List.of(new ClanView.EventView(events.get(0), team, standings, board),
			new ClanView.EventView(events.get(1), null, List.of(), null)), List.of(), NOW, null);
		ClanTabs.Input in = new ClanTabs.Input(view, Map.of("rozzle", 416), 0, null, ZoneId.of("UTC"), NOW, "https://asgardiansclan.com",
			Map.of(), Set.of(), Set.of(), Map.of());
		for (String tab : new String[]{null, ClanTabs.BOARD, ClanTabs.LIST, ClanTabs.SCORES, ClanTabs.TEAM})
		{
			for (String tile : new String[]{null, "t5", "missing"})
			{
				JPanel column = PanelParts.column();
				ClanTabs.bingo(column, in, ClanTabsTest.ACTIONS, new ClanTabs.Ui(null, null, new HashMap<>(), null, new HashSet<>(), tab,
					tab == null, tile, null), new ClanTabs.UiActions()
				{
					public void eventsPage(String page) { }
					public void event(String eventId) { }
					public void team(String eventId, String teamId) { }
					public void period(String period) { }
					public void fold(String title) { }
				});
				assertTrue(column.getComponentCount() > 0);
			}
		}
		// Nothing running: the next bingo shows.
		ClanView between = new ClanView(List.of(new ClanView.EventView(events.get(1), null, List.of(), null)), List.of(), NOW, null);
		JPanel column = PanelParts.column();
		ClanTabs.bingo(column, new ClanTabs.Input(between, Map.of(), 0, null, ZoneId.of("UTC"), NOW, "https://asgardiansclan.com",
			Map.of(), Set.of(), Set.of(), Map.of()), ClanTabsTest.ACTIONS, new ClanTabs.Ui(null, null, new HashMap<>(), null, new HashSet<>()),
			new ClanTabs.UiActions()
			{
				public void eventsPage(String page) { }
				public void event(String eventId) { }
				public void team(String eventId, String teamId) { }
				public void period(String period) { }
				public void fold(String title) { }
			});
		assertTrue(column.getComponentCount() > 1);
		assertNull(ClanTabs.bingoEvent(between, null));
	}

	@Test
	public void membersFilterByWorldAndActivity()
	{
		MembersTabs.Member clo = new MembersTabs.Member("Clo", 416, "Theatre of Blood");
		MembersTabs.Member pricey = new MembersTabs.Member("pricey", 302, "Mining");
		MembersTabs.Member tyr = new MembersTabs.Member("Tyr", null, null);
		assertTrue(MembersTabs.matches(clo, "On my world", 416));
		assertFalse(MembersTabs.matches(pricey, "On my world", 416));
		assertTrue(MembersTabs.matches(clo, ActivityTracker.RAIDS, 416));
		assertTrue(MembersTabs.matches(pricey, ActivityTracker.SKILLING, 416));
		assertEquals("Everyone", MembersTabs.filter("nonsense"));
		assertEquals(List.of(clo, pricey, tyr), MembersTabs.sorted(List.of(tyr, pricey, clo)));
		MembersTabs.Input in = new MembersTabs.Input(List.of(clo, pricey, tyr), "Rozzle", 416, null, false, true, null);
		assertEquals(2, MembersTabs.online(in));
		for (String filter : MembersTabs.FILTERS)
		{
			JPanel column = PanelParts.column();
			MembersTabs.draw(column, in, new MembersTabs.Ui(filter, true), new MembersTabs.Actions()
			{
				public void filter(String f) { }
				public void showOffline(boolean show) { }
				public void toggleSharing() { }
				public void openProfile(String rsn) { }
			});
			assertTrue(column.getComponentCount() > 0);
		}
	}

	@Test
	public void pbRecordsPickCategoryBossAndSize()
	{
		ClanData.StatsFeed feed = GSON.fromJson("{\"records\":[{\"boss\":\"Theatre of Blood\",\"category\":\"raids\",\"teamSize\":\"5\","
			+ "\"entries\":[{\"rsn\":\"Clo\",\"seconds\":800}]},{\"boss\":\"Theatre of Blood\",\"category\":\"raids\",\"teamSize\":\"4\","
			+ "\"entries\":[{\"rsn\":\"Rozzle\",\"seconds\":900},{\"rsn\":\"Clo\",\"seconds\":850},{\"rsn\":\"x\"},null]},"
			+ "{\"boss\":\"Vorkath\",\"category\":\"bosses\",\"entries\":[]},{\"category\":\"odd\"},null]}", ClanData.StatsFeed.class);
		StatsTabs.Input in = new StatsTabs.Input(feed, false, null, null, NOW, NOW, ZoneId.of("UTC"));
		List<String> picked = new ArrayList<>();
		StatsTabs.PbActions actions = new StatsTabs.PbActions()
		{
			@Override
			public void pick(String category, String group, String boss, String teamSize)
			{
				picked.add(category + "/" + group + "/" + boss + "/" + teamSize);
			}

			@Override
			public void openProfile(String rsn)
			{
			}
		};
		for (StatsTabs.PbUi ui : new StatsTabs.PbUi[]{new StatsTabs.PbUi(null, null, null, null),
			new StatsTabs.PbUi("Raids", null, "Theatre of Blood", "5"), new StatsTabs.PbUi("Bosses", "Other", "Vorkath", null),
			new StatsTabs.PbUi("gone", "gone", "gone", "9")})
		{
			JPanel column = PanelParts.column();
			StatsTabs.pbs(column, in, ui, actions, null, StatsTabsTest.ACTIONS, "Rozzle");
			assertTrue(column.getComponentCount() > 2);
		}
		assertEquals("Solo", StatsTabs.sizeLabel("1"));
		assertEquals("4 players", StatsTabs.sizeLabel("4"));
		assertEquals(BossGroups.RAIDS, BossGroups.classify("Theatre of Blood: Hard Mode", null)[0]);
		assertEquals(BossGroups.SKILLING, BossGroups.classify("Tempoross", "bosses")[0]);
		assertEquals(BossGroups.SKILLING, BossGroups.classify("Wintertodt", null)[0]);
		assertEquals("Slayer", BossGroups.classify("Alchemical Hydra", null)[1]);
		assertEquals("God Wars", BossGroups.classify("Kree'arra", null)[1]);
		assertEquals("Desert Treasure II", BossGroups.classify("The Leviathan", null)[1]);
		assertEquals(BossGroups.MINIGAMES, BossGroups.classify("The Corrupted Gauntlet", null)[0]);
		assertEquals(BossGroups.MINIGAMES, BossGroups.classify("The Gauntlet", null)[0]);
		// Unknown: the website's category, else Bosses, Other.
		assertEquals(BossGroups.MINIGAMES, BossGroups.classify("Some new minigame", "minigames")[0]);
		assertEquals(List.of(BossGroups.BOSSES, BossGroups.OTHER), List.of(BossGroups.classify("New boss", null)));
		// No records: the newest PBs, as before.
		JPanel column = PanelParts.column();
		StatsTabs.pbs(column, StatsTabsTest.input(StatsTabsTest.feed()), new StatsTabs.PbUi(null, null, null, null), actions,
			new StatsTabs.UiActions()
			{
				public void page(String page) { }
				public void minimum(String minimum) { }
			}, StatsTabsTest.ACTIONS, null);
		assertTrue(column.getComponentCount() > 0);
	}

	@Test
	public void profilesDrawWithWhateverTheWebsiteSends()
	{
		ProfileTabs.Actions actions = new ProfileTabs.Actions()
		{
			@Override
			public void openBoard(String board)
			{
			}

			@Override
			public void bossIcon(javax.swing.JLabel label, String boss)
			{
			}
		};
		ClanData.Me full = GSON.fromJson("{\"rank\":\"thor\",\"daysInClan\":10,\"ehb\":1.5,\"pets\":[{\"itemId\":1},{}],"
			+ "\"events\":[{\"name\":\"A\",\"won\":true},{}],\"personalBests\":[{\"boss\":\"Zulrah\",\"seconds\":60},{}],"
			+ "\"deaths\":3,\"lastDeathBy\":\"Zulrah\",\"lastDeathAt\":\"bad\",\"eggsThrown\":1,\"rankings\":[{\"board\":\"Eggers\",\"rank\":1},{}]}",
			ClanData.Me.class);
		for (ClanData.Me me : new ClanData.Me[]{full, new ClanData.Me(), null})
		{
			for (boolean own : new boolean[]{true, false})
			{
				JPanel column = PanelParts.column();
				ProfileTabs.draw(column, new ProfileTabs.Input("Rozzle", me, own, me == null, false, own ? 416 : null, "Vorkath",
					null, List.of(), NOW), actions);
				assertTrue(column.getComponentCount() > 0);
			}
		}
	}

	@Test
	public void settingsReadSavedValuesOrDefaults()
	{
		Map<String, String> saved = new HashMap<>();
		AsgardiansPanel.Settings settings = new AsgardiansPanel.Settings()
		{
			@Override
			public String get(String key)
			{
				return saved.get(key);
			}

			@Override
			public void set(String key, String value)
			{
				saved.put(key, value);
			}
		};
		SettingsTabs.Setting shareData = SettingsTabs.GROUPS.get(0).getSettings().get(0);
		SettingsTabs.Setting activity = SettingsTabs.GROUPS.get(0).getSettings().get(1);
		assertFalse(SettingsTabs.value(settings, shareData));
		assertTrue(SettingsTabs.value(settings, activity));
		saved.put("shareActivity", "false");
		assertFalse(SettingsTabs.value(settings, activity));
		JPanel column = PanelParts.column();
		SettingsTabs.draw(column, settings, () -> { });
		assertTrue(column.getComponentCount() > 10);
	}

	@Test
	public void thePanelOpensEveryPage()
	{
		AsgardiansPanel panel = PanelPreview.panel();
		for (String page : new String[]{AsgardiansPanel.EVENTS, AsgardiansPanel.BINGO, AsgardiansPanel.GROUPS, AsgardiansPanel.MEMBERS,
			AsgardiansPanel.DROPS, AsgardiansPanel.LEADERBOARD, AsgardiansPanel.PROFILE, AsgardiansPanel.NEWS, AsgardiansPanel.PBS,
			AsgardiansPanel.SETTINGS, "nonsense"})
		{
			panel.show(page);
		}
		assertEquals(AsgardiansPanel.HOME, panel.page());
		panel.show(AsgardiansPanel.MEMBERS);
		panel.openProfile("Clo");
		assertEquals(AsgardiansPanel.MEMBER, panel.page());
		panel.updateProfile("Clo", null, true);
		panel.openProfile("Rozzle");
		assertEquals(AsgardiansPanel.PROFILE, panel.page());
	}

	@Test
	public void speedrunWindowIsRead()
	{
		assertEquals(761.4, ChatParsers.runTime("Duration: <col=ff0000>12:41.40</col>"), 0.001);
		assertEquals(3723.0, ChatParsers.runTime("1:02:03"), 0.001);
		assertNull(ChatParsers.runTime("no time"));
		assertEquals("Dragon Slayer I", ChatParsers.speedrunQuest("You have completed <col=ff0000>Dragon Slayer I</col>!"));
		assertEquals("Something", ChatParsers.speedrunQuest("Something"));
	}

	@Test
	public void backWalksThroughEveryPageAndHomeClearsIt()
	{
		AsgardiansPanel panel = PanelPreview.panel();
		panel.show(AsgardiansPanel.MEMBERS);
		panel.openProfile("Clo");
		panel.openProfile("Thor");
		panel.show(AsgardiansPanel.LEADERBOARD);
		panel.back();
		assertEquals(AsgardiansPanel.MEMBER, panel.page());
		panel.back();
		assertEquals(AsgardiansPanel.MEMBER, panel.page());
		panel.back();
		assertEquals(AsgardiansPanel.MEMBERS, panel.page());
		panel.back();
		assertEquals(AsgardiansPanel.HOME, panel.page());
		panel.back();
		assertEquals(AsgardiansPanel.HOME, panel.page());
		panel.show(AsgardiansPanel.EVENTS);
		panel.show(AsgardiansPanel.BINGO);
		panel.show(AsgardiansPanel.HOME);
		panel.back();
		assertEquals(AsgardiansPanel.HOME, panel.page());
	}

	@Test
	public void theBoardShowsWhenTheWebsiteSendsEveryTeamsBoard()
	{
		// /boards gives each team's board and no "own" board: the Bingo page must still draw the member's team's tiles.
		ClanData.Event event = GSON.fromJson("{\"id\":\"nov\",\"name\":\"November Test Bingo\",\"type\":\"bingo\",\"status\":\"live\","
			+ "\"teamId\":\"t1\",\"hasBoard\":true}", ClanData.Event.class);
		StringBuilder tiles = new StringBuilder();
		for (int i = 0; i < 25; i++)
		{
			tiles.append(i > 0 ? "," : "").append("{\"id\":\"t").append(i).append("\",\"name\":\"Tile ").append(i).append("\"}");
		}
		ClanData.Board board = GSON.fromJson("{\"tiles\":[" + tiles + "]}", ClanData.Board.class);
		ClanData.Team team = GSON.fromJson("{\"id\":\"t1\",\"name\":\"Team 1\"}", ClanData.Team.class);
		List<ClanData.Team> standings = GSON.fromJson("{\"teams\":[{\"id\":\"t1\",\"name\":\"Team 1\"},{\"id\":\"t2\",\"name\":\"Team 2\"}]}",
			ClanData.TeamList.class).getTeams();
		ClanView view = new ClanView(List.of(new ClanView.EventView(event, team, standings, null, Map.of("t1", board, "t2", board), List.of())),
			List.of(), NOW, null);
		ClanTabs.Input in = new ClanTabs.Input(view, Map.of(), 0, null, ZoneId.of("UTC"), NOW, "https://asgardiansclan.com",
			Map.of(), Set.of(), Set.of(), Map.of());
		JPanel column = PanelParts.column();
		ClanTabs.bingo(column, in, ClanTabsTest.ACTIONS, new ClanTabs.Ui(null, null, new HashMap<>(), null, new HashSet<>()),
			new ClanTabs.UiActions()
			{
				public void eventsPage(String page) { }
				public void event(String eventId) { }
				public void team(String eventId, String teamId) { }
				public void period(String period) { }
				public void fold(String title) { }
			});
		assertEquals(25, count(column, ClanTabs.BingoTile.class));
	}

	private static int count(java.awt.Container c, Class<?> type)
	{
		int n = 0;
		for (java.awt.Component child : c.getComponents())
		{
			n += type.isInstance(child) ? 1 : 0;
			if (child instanceof java.awt.Container)
			{
				n += count((java.awt.Container) child, type);
			}
		}
		return n;
	}

	@Test
	public void eventCardsSayWhoIsInAndHowFarThrough()
	{
		ClanData.Event botw = GSON.fromJson("{\"id\":\"b\",\"name\":\"Boss of the Week: Vorkath\",\"type\":\"botw\",\"status\":\"live\","
			+ "\"startsAt\":\"2026-11-03T12:00:00Z\",\"endsAt\":\"2026-11-10T12:00:00Z\",\"myProgress\":\"14 kills\",\"myRank\":4}", ClanData.Event.class);
		ClanData.Event party = GSON.fromJson("{\"id\":\"p\",\"name\":\"Drop party\",\"status\":\"upcoming\","
			+ "\"signup\":{\"open\":true,\"myStatus\":\"waitlist\"}}", ClanData.Event.class);
		ClanView.EventView b = new ClanView.EventView(botw, null, List.of(), null);
		assertEquals("BOTW", ClanTabs.typeTag(botw));
		assertEquals("BOTW: Vorkath", ClanTabs.shortName(botw));
		assertEquals("Enrolled via WOM", ClanTabs.enrolment(b));
		assertEquals("You: 14 kills · #4", ClanTabs.progressLine(b));
		assertEquals("Day 6 of 7", ClanTabs.dayOf(botw, NOW));
		assertEquals(5 / 7.0, ClanTabs.share(botw, NOW), 0.01);
		assertEquals("Other", ClanTabs.typeTag(party));
		assertEquals("On the waitlist", ClanTabs.enrolment(new ClanView.EventView(party, null, List.of(), null)));
		assertNull(ClanTabs.dayOf(party, NOW));
		assertTrue(ClanTabs.womLink("https://wiseoldman.net/competitions/1"));
		assertFalse(ClanTabs.womLink("https://wiseoldman.net.evil.io/x"));
		assertFalse(ClanTabs.womLink("http://wiseoldman.net/x"));
		// The page and the details page draw with the website's data, odd or missing.
		AsgardiansPanel panel = PanelPreview.panel();
		panel.show(AsgardiansPanel.EVENTS);
		panel.showEvent("botw");
		assertEquals(AsgardiansPanel.EVENT, panel.page());
		panel.showEvent("party");
		panel.back();
		assertEquals(AsgardiansPanel.EVENT, panel.page());
		panel.showEvent("missing");
	}

	@Test
	public void killTimesAreReadFromTheGamesMessages()
	{
		ChatParsers.KillTime vork = ChatParsers.killTime("Fight duration: 1:01.80. Personal best: 0:58.20");
		assertEquals(61.8, vork.getSeconds(), 0.001);
		assertFalse(vork.isTotal());
		assertNull(vork.getTeamSize());
		assertEquals(61.8, ChatParsers.killTime("Fight duration: 1:01.80 (new personal best)").getSeconds(), 0.001);
		ChatParsers.KillTime cox = ChatParsers.killTime("Congratulations - your raid is complete! Team size: 4 players Duration: 20:31.20 Personal best: 19:02.40");
		assertEquals(1231.2, cox.getSeconds(), 0.001);
		assertEquals("4", cox.getTeamSize());
		assertTrue(cox.isTotal());
		assertEquals("1", ChatParsers.killTime("Congratulations - your raid is complete! Team size: Solo Duration: 30:00").getTeamSize());
		assertEquals("24+", ChatParsers.killTime("Congratulations - your raid is complete! Team size: 24+ players Duration: 30:00").getTeamSize());
		ChatParsers.KillTime tob = ChatParsers.killTime("Theatre of Blood total completion time: 18:12.60. Personal best: 17:00.00");
		assertEquals(1092.6, tob.getSeconds(), 0.001);
		assertTrue(tob.isTotal());
		ChatParsers.KillTime jad = ChatParsers.killTime("Challenge duration: 0:40.80 (new personal best)");
		assertTrue(jad.isChallenge());
		assertFalse(AsgardiansPlugin.fits("TzTok-Jad", jad));
		assertTrue(AsgardiansPlugin.fits("TzHaar-Ket-Rak's First Challenge", jad));
		assertTrue(AsgardiansPlugin.fits("TzTok-Jad", ChatParsers.killTime("Duration: 45:12")));
		assertEquals(3723.0, ChatParsers.killTime("Subdued in 1:02:03").getSeconds(), 0.001);
		assertNull(ChatParsers.killTime("Your Vorkath kill count is: 512."));
		assertNull(ChatParsers.killTime(null));
		assertEquals("1", AsgardiansPlugin.teamSize(1));
		assertEquals("11-15", AsgardiansPlugin.teamSize(12));
		assertEquals("24+", AsgardiansPlugin.teamSize(30));
	}

	@Test
	public void bossTimeTasksAreDoneOrNotWithoutABar()
	{
		ClanData.Tile bossTime = GSON.fromJson("{\"name\":\"Vorkath solo in 1:00\",\"group\":\"Boss time\",\"have\":0,\"need\":1,"
			+ "\"items\":[{\"itemId\":21992,\"name\":\"Vorkath solo under 1:00\"}]}", ClanData.Tile.class);
		ClanData.Tile kills = GSON.fromJson("{\"name\":\"250 Vorkath\",\"have\":182,\"need\":250}", ClanData.Tile.class);
		assertFalse(ClanTabs.countsUp(bossTime));
		assertTrue(ClanTabs.countsUp(kills));
		assertEquals(Integer.valueOf(21992), ClanTabs.firstItem(bossTime));
		assertEquals(0, count(ClanTabs.taskCard(bossTime, true), javax.swing.JComponent.class) == 0 ? 1 : 0);
	}

	@Test
	public void theWebsitesGroupingWinsOnceItSendsGroups()
	{
		// Without "group": the plugin's own sorting (Tempoross is Skilling even if the website says bosses).
		assertEquals(List.of(BossGroups.SKILLING), java.util.Arrays.asList(BossGroups.place("Tempoross", "bosses", null)).subList(0, 1));
		// With "group": the website decides, including names the plugin doesn't know.
		assertEquals(List.of("Bosses", "Slayer"), java.util.Arrays.asList(BossGroups.place("Vorkath", "bosses", "Slayer")));
		assertEquals(java.util.Arrays.asList("Skilling", null), java.util.Arrays.asList(BossGroups.place("Tempoross", "skilling", "")));
		assertEquals(java.util.Arrays.asList("Clan challenges", "Week 1"), java.util.Arrays.asList(BossGroups.place("Boss", "clan challenges", "Week 1")));
		assertTrue(BossGroups.categoryOrder("Raids") < BossGroups.categoryOrder("Clan challenges"));
		assertTrue(BossGroups.groupOrder("God Wars") < BossGroups.groupOrder("Something new"));
		assertTrue(BossGroups.groupOrder("Something new") < BossGroups.groupOrder(BossGroups.OTHER));

		// The page: a category mixing grouped and ungrouped bosses puts the ungrouped ones under Other.
		ClanData.StatsFeed feed = GSON.fromJson("{\"records\":["
			+ "{\"boss\":\"Vorkath\",\"category\":\"Bosses\",\"group\":\"Dragons\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":60}]},"
			+ "{\"boss\":\"Zulrah\",\"category\":\"Bosses\",\"group\":\"\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":50}]},"
			+ "{\"boss\":\"Tempoross\",\"category\":\"Skilling\",\"group\":\"\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":180}]}]}",
			ClanData.StatsFeed.class);
		List<String> picked = new ArrayList<>();
		JPanel column = PanelParts.column();
		StatsTabs.pbs(column, new StatsTabs.Input(feed, false, null, null, NOW, NOW, ZoneId.of("UTC")),
			new StatsTabs.PbUi("Bosses", BossGroups.OTHER, "Zulrah", null), new StatsTabs.PbActions()
			{
				@Override
				public void pick(String category, String group, String boss, String teamSize)
				{
					picked.add(category + "/" + group + "/" + boss);
				}

				@Override
				public void openProfile(String rsn)
				{
				}
			}, null, StatsTabsTest.ACTIONS, null);
		assertTrue(column.getComponentCount() > 2);
	}

	@Test
	public void aPickedTileListsTheDropsSentForIt()
	{
		ClanData.Tile tile = GSON.fromJson("{\"id\":\"t1\",\"name\":\"Twisted bow\"}", ClanData.Tile.class);
		List<ClanData.Drop> drops = GSON.fromJson("{\"drops\":[{\"id\":\"a\",\"tileId\":\"t1\"},{\"id\":\"b\",\"tileId\":\"t2\",\"tileName\":\"Twisted bow\"},"
			+ "{\"id\":\"c\",\"tileName\":\"twisted BOW\"},{\"id\":\"d\"},null]}", ClanData.DropList.class).getDrops();
		List<String> ids = new ArrayList<>();
		ClanTabs.dropsFor(tile, drops).forEach(d -> ids.add(d.getId()));
		// By tile ID, or by name only when the drop has no tile ID.
		assertEquals(List.of("a", "c"), ids);
		assertTrue(ClanTabs.dropsFor(tile, null).isEmpty());
	}

	@Test
	public void boardsOfAnyShapeAreDrawn()
	{
		assertEquals(7, ClanTabs.boardColumns(7, 24));
		assertEquals(5, ClanTabs.boardColumns(null, 25));
		assertEquals(6, ClanTabs.boardColumns(null, 24));
		assertEquals(5, ClanTabs.boardColumns(null, 23));
		assertEquals(0, ClanTabs.boardColumns(null, 3));
		assertEquals(5, ClanTabs.boardColumns(99, 25));
		// 24 tiles, 7 across: rows of 7, 7, 7 and 3. The first row and the first column are done.
		List<ClanData.Tile> tiles = new ArrayList<>();
		for (int i = 0; i < 24; i++)
		{
			ClanData.Tile t = new ClanData.Tile();
			t.setState(i < 7 || i % 7 == 0 ? "accepted" : "needed");
			tiles.add(t);
		}
		Set<Integer> inLine = new HashSet<>();
		assertEquals(2, ClanTabs.finishedLines(tiles, 7, 4, inLine));
		assertTrue(inLine.containsAll(List.of(0, 6, 7, 14, 21)));

		// Drawn as a board, with the member's own team found from the event's teamId.
		ClanData.Event event = GSON.fromJson("{\"id\":\"t\",\"name\":\"Test-Bingo\",\"type\":\"bingo\",\"status\":\"live\","
			+ "\"teamId\":\"r\",\"hasBoard\":true}", ClanData.Event.class);
		ClanData.Board board = new ClanData.Board();
		board.setTiles(tiles);
		board.setColumns(7);
		List<ClanData.Team> teams = GSON.fromJson("{\"teams\":[{\"id\":\"a\",\"name\":\"Alpha\"},{\"id\":\"r\",\"name\":\"Rozzle's Team\",\"rank\":1}]}",
			ClanData.TeamList.class).getTeams();
		ClanView.EventView view = new ClanView.EventView(event, null, teams, null, Map.of("a", board, "r", board), List.of());
		assertEquals("Rozzle's Team", ClanTabs.ownTeam(view).getName());
		assertEquals("Rozzle's Team · #1", ClanTabs.enrolment(view));
		JPanel column = PanelParts.column();
		ClanTabs.bingo(column, new ClanTabs.Input(new ClanView(List.of(view), List.of(), NOW, null), Map.of(), 0, null, ZoneId.of("UTC"), NOW,
			"https://asgardiansclan.com", Map.of(), Set.of(), Set.of(), Map.of()), ClanTabsTest.ACTIONS,
			new ClanTabs.Ui(null, null, new HashMap<>(), null, new HashSet<>()), new ClanTabs.UiActions()
			{
				public void eventsPage(String page) { }
				public void event(String eventId) { }
				public void team(String eventId, String teamId) { }
				public void period(String period) { }
				public void fold(String title) { }
			});
		assertEquals(24, count(column, ClanTabs.BingoTile.class));
	}

	@Test
	public void newsCardsAreShortUntilOpened()
	{
		assertEquals("First line", ClanTabs.preview("First line\nSecond line"));
		assertEquals(80, ClanTabs.preview("x".repeat(200)).length());
		AsgardiansPanel panel = PanelPreview.panel();
		panel.show(AsgardiansPanel.NEWS);
		assertEquals(AsgardiansPanel.NEWS, panel.page());
	}

	@Test
	public void bossKillsDrillFromAllBossesIntoOne()
	{
		AsgardiansPanel panel = PanelPreview.panel();
		panel.showBoard("Boss kills", null, null, null);
		panel.showBoard("Boss kills", BossGroups.BOSSES, "Slayer", "Alchemical Hydra");
		panel.showBoard("Boss kills", BossGroups.BOSSES, BossGroups.OTHER, "Vorkath");
		panel.showBoard("Boss kills", "Gone", "gone", "gone");
		panel.showBoard("Drop value", null, null, null);
		assertEquals(AsgardiansPanel.LEADERBOARD, panel.page());
	}

	@Test
	public void xpDrillsFromAllSkillsIntoOne()
	{
		assertTrue(ClanTabs.skillOrder("Attack") < ClanTabs.skillOrder("Slayer"));
		assertEquals(Integer.MAX_VALUE, ClanTabs.skillOrder("Sailing!"));
		AsgardiansPanel panel = PanelPreview.panel();
		panel.showSkill("XP", null);
		panel.showSkill("XP", "Slayer");
		panel.showSkill("XP", "Nonsense");
		assertEquals(AsgardiansPanel.LEADERBOARD, panel.page());
	}
}
