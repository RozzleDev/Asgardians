package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import org.junit.Test;

public class ClanTabsTest
{
	private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");

	@Test
	public void aMissingOrUnknownSavedPeriodMeansThisMonth()
	{
		// A member who never picked a period has none saved; this used to stop the plugin starting.
		assertEquals(ClanTabs.MONTH, ClanTabs.period(null));
		assertEquals(ClanTabs.MONTH, ClanTabs.period("week"));
		assertEquals(ClanTabs.YEAR, ClanTabs.period("year"));
		assertEquals(ClanTabs.ALL, ClanTabs.period("all"));
	}

	static final ClanTabs.Actions ACTIONS = new ClanTabs.Actions()
	{
		public void signUp(ClanData.Event event) { }
		public void withdraw(ClanData.Event event) { }
		public void toggleSignups(ClanData.Event event) { }
		public void chooseTile(ClanData.Event event, ClanData.Drop drop, ClanData.TileOption tile) { }
	};

	private static final ClanTabs.UiActions UI_ACTIONS = new ClanTabs.UiActions()
	{
		public void eventsPage(String page) { }
		public void event(String eventId) { }
		public void team(String eventId, String teamId) { }
		public void period(String period) { }
		public void fold(String title) { }
	};

	/** What the panel passes on a fresh install: no page, event, team or period picked, nothing folded. */
	private static ClanTabs.Ui freshUi(String page)
	{
		return new ClanTabs.Ui(page, null, new HashMap<>(), null, new LinkedHashSet<>());
	}

	private static ClanTabs.Input input(ClanView view, Map<String, List<ClanData.Leaderboard>> boards)
	{
		return new ClanTabs.Input(view, Map.of(), 0, null, ZoneId.of("Europe/London"), NOW, "https://asgardiansclan.com",
			Map.of(), Set.of(), Set.of(), boards);
	}

	private static void drawEverything(ClanTabs.Input in)
	{
		for (String page : new String[]{null, ClanTabs.CURRENT, ClanTabs.SCHEDULE})
		{
			ClanTabs.events(new JPanel(), in, () -> { }, ACTIONS, freshUi(page), UI_ACTIONS);
		}
		for (String period : new String[]{null, ClanTabs.MONTH, ClanTabs.YEAR, ClanTabs.ALL, "week"})
		{
			ClanTabs.leaderboards(new JPanel(), in, () -> { }, new ClanTabs.Ui(null, null, new HashMap<>(), period, new LinkedHashSet<>()),
				UI_ACTIONS);
		}
		ClanTabs.news(new JPanel(), in, () -> { });
	}

	@Test
	public void everyTabDrawsBeforeAnythingHasLoaded()
	{
		// The panel's very first draw, before login.
		drawEverything(new ClanTabs.Input(ClanView.empty(), Map.of(), 0, "Log in to see clan events.", ZoneId.systemDefault(),
			NOW, "https://asgardiansclan.com", Map.of(), Set.of(), Set.of(), Map.of()));
		drawEverything(input(ClanView.empty(), Map.of()));
	}

	/** Part and odd data, as an older or buggy website might send: missing names, IDs, dates and counts. */
	static ClanTabs.Input oddInput()
	{
		Gson gson = new Gson();
		List<ClanData.Event> events = gson.fromJson("{\"events\":["
			+ "{\"id\":\"bingo\",\"name\":\"Test-Bingo\",\"type\":\"bingo\",\"status\":\"live\",\"startsAt\":\"2026-11-01T00:00:00Z\","
			+ "\"endsAt\":\"2026-11-20T00:00:00Z\",\"signedUp\":true,\"teamId\":\"t24\",\"hasBoard\":true},"
			+ "{\"id\":\"second\",\"status\":\"live\",\"hasBoard\":true},"
			+ "{\"id\":\"botw\",\"name\":\"ToA\",\"type\":\"botw\",\"status\":\"live\"},"
			+ "{\"id\":\"later\",\"status\":\"upcoming\"}]}", ClanData.EventList.class).getEvents();
		ClanData.Team own = gson.fromJson("{\"id\":\"t24\",\"members\":[\"Rozzle\",null,\"\"]}", ClanData.Team.class);
		List<ClanData.Team> standings = gson.fromJson("{\"teams\":[{\"id\":\"t25\",\"name\":\"Clo's Team\",\"members\":[null]},"
			+ "{\"id\":\"t24\",\"name\":\"Rozzle's Team\",\"rank\":2},{\"name\":\"No id\"},{\"id\":\"t26\",\"tag\":\"  \"}]}",
			ClanData.TeamList.class).getTeams();
		ClanData.Board board = gson.fromJson("{\"tiles\":[{\"id\":\"a\"},{\"id\":\"b\",\"name\":\"Gems\",\"mode\":\"any\","
			+ "\"items\":[{\"itemId\":1619},{\"name\":\"no id\",\"have\":2,\"need\":0}]},{\"name\":\"no id or state\",\"items\":[]}]}",
			ClanData.Board.class);
		List<ClanData.Drop> drops = gson.fromJson("{\"drops\":[{},{\"id\":\"d1\",\"state\":\"choose\",\"canChoose\":true,"
			+ "\"options\":[{\"tileId\":\"a\"},{}]},{\"id\":\"d2\",\"state\":\"choose\",\"canChoose\":true},"
			+ "{\"id\":\"d3\",\"item\":\"Uncut ruby\",\"quantity\":3,\"at\":\"not a date\",\"teamId\":\"t25\",\"state\":\"accepted\"}]}",
			ClanData.DropList.class).getDrops();
		ClanView view = new ClanView(List.of(
			new ClanView.EventView(events.get(0), own, standings, board, Map.of("t25", new ClanData.Board()), drops),
			new ClanView.EventView(events.get(1), null, List.of(), null, Map.of(), List.of()),
			new ClanView.EventView(events.get(2), null, List.of(), null),
			new ClanView.EventView(events.get(3), null, List.of(), null)), List.of(), NOW, null);
		List<ClanData.Leaderboard> boards = gson.fromJson("{\"boards\":[{},{\"title\":\"Deaths\",\"rows\":[{},{\"rank\":1}],\"me\":{}}]}",
			ClanData.LeaderboardList.class).getBoards();
		return input(view, Map.of(ClanTabs.MONTH, boards));
	}

	@Test
	public void everyTabDrawsWithPartAndOddDataFromTheWebsite()
	{
		ClanTabs.Input in = oddInput();
		ClanView view = in.getView();
		drawEverything(in);
		// Every team's tab, and every event on the Current event page.
		for (ClanData.Team team : view.getEvents().get(0).teams())
		{
			Map<String, String> picked = new HashMap<>();
			picked.put("bingo", team.getId());
			ClanTabs.events(new JPanel(), in, () -> { }, ACTIONS,
				new ClanTabs.Ui(ClanTabs.CURRENT, "bingo", picked, null, new LinkedHashSet<>()), UI_ACTIONS);
		}
		ClanTabs.events(new JPanel(), in, () -> { }, ACTIONS,
			new ClanTabs.Ui(ClanTabs.CURRENT, "second", new HashMap<>(), null, new LinkedHashSet<>()), UI_ACTIONS);
		// Folded sections.
		Set<String> folded = new LinkedHashSet<>(List.of(ClanTabs.dropsFoldKey("bingo"), "Deaths"));
		ClanTabs.events(new JPanel(), in, () -> { }, ACTIONS, new ClanTabs.Ui(ClanTabs.CURRENT, null, new HashMap<>(), null, folded), UI_ACTIONS);
		ClanTabs.leaderboards(new JPanel(), in, () -> { }, new ClanTabs.Ui(null, null, new HashMap<>(), null, folded), UI_ACTIONS);

		// Badges and chat lines from the same data.
		assertTrue(TeamBadges.players(view).containsKey("rozzle"));
		view.tileChanges(view);
		view.dropMatches(List.of(new Record.Item(1619, "Uncut ruby", 1, 1)));
		view.choices();
	}
}
