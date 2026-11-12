package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ClanViewTest
{
	private static final Instant NOW = Instant.parse("2026-10-22T17:00:00Z");

	static ClanData.Event event(String id, String status, String start, boolean signedUp)
	{
		ClanData.Event e = new ClanData.Event();
		e.setId(id);
		e.setName("Event " + id);
		e.setStatus(status);
		e.setStartsAt(start);
		e.setEndsAt("2026-10-30T00:00:00Z");
		e.setSignedUp(signedUp);
		return e;
	}

	static ClanData.Announcement news(long id)
	{
		ClanData.Announcement a = new ClanData.Announcement();
		a.setId(id);
		a.setTitle("News " + id);
		return a;
	}

	static ClanData.Board board(String... idStates)
	{
		ClanData.Board b = new ClanData.Board();
		List<ClanData.Tile> tiles = new java.util.ArrayList<>();
		for (int i = 0; i < idStates.length; i += 2)
		{
			ClanData.Tile t = new ClanData.Tile();
			t.setId(idStates[i]);
			t.setName("Tile " + idStates[i]);
			t.setState(idStates[i + 1]);
			tiles.add(t);
		}
		b.setTiles(tiles);
		return b;
	}

	static ClanView view(List<ClanView.EventView> events, List<ClanData.Announcement> news)
	{
		return new ClanView(events, news, NOW, null);
	}

	@Test
	public void unreadAndNewAnnouncements()
	{
		ClanView v = view(List.of(), List.of(news(3), news(1), news(2)));
		assertEquals(3, v.latestAnnouncementId());
		assertEquals(2, v.unread(1));
		assertEquals(List.of(2L, 3L), v.newAnnouncements(1).stream().map(ClanData.Announcement::getId).collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void loginNewsListsUnreadNewestFirst()
	{
		ClanView v = view(List.of(), List.of(news(1), news(2), news(3), news(4), news(5), news(6)));
		assertTrue(v.loginNews(6).isEmpty());
		assertEquals(List.of("Unread news: News 6. Open the News tab to read it."), v.loginNews(5));
		assertEquals(List.of("You have 2 unread announcements. Open the News tab to read them.", "- News 6", "- News 5"), v.loginNews(4));
		assertEquals(List.of("You have 5 unread announcements. Open the News tab to read them.",
			"- News 6", "- News 5", "- News 4", "...and 2 more."), v.loginNews(1));
	}

	private static ClanView.EventView bingo(String id, String myStatus, Boolean paid, String note)
	{
		ClanData.Event e = event(id, "upcoming", "2026-12-01T18:00:00Z", false);
		e.setKind("bingo");
		ClanData.Signup signup = new ClanData.Signup();
		signup.setMyStatus(myStatus);
		signup.setPaid(paid);
		signup.setNote(note);
		e.setSignup(signup);
		return new ClanView.EventView(e, null, List.of(), null);
	}

	@Test
	public void remindsOnlyWhereABuyInIsOwed()
	{
		ClanView v = view(List.of(
			bingo("owed", "signedUp", false, "Buy-in 10m."),
			bingo("confirmed", "confirmed", false, null),
			bingo("paid", "signedUp", true, null),
			bingo("free", "signedUp", null, null),
			bingo("waiting", "waitlist", false, null),
			bingo("notin", "none", false, null)), List.of());
		assertEquals(List.of("Your buy-in for Event owed isn't marked as paid. Buy-in 10m.",
			"Your buy-in for Event confirmed isn't marked as paid."), v.buyInReminders());
	}

	@Test
	public void reportsTileChangesWithoutTeamDetails()
	{
		ClanData.Event bingo = event("bingo", "live", "2026-10-20T00:00:00Z", true);
		ClanView before = view(List.of(new ClanView.EventView(bingo, null, List.of(), board("a", "pending", "b", "needed", "c", "pending"))), List.of());
		ClanView after = view(List.of(new ClanView.EventView(bingo, null, List.of(), board("a", "accepted", "b", "pending", "c", "declined"))), List.of());
		assertEquals(List.of("Event bingo: Your team completed \"Tile a\".",
			"Event bingo: Your team submitted an item for \"Tile b\".",
			"Event bingo: Your team's submission for \"Tile c\" was declined."), after.tileChanges(before));
		// One tile completed, by the member's own team: a ding and fireworks.
		assertEquals(List.of(true), after.completedTiles(before));
		assertEquals(List.of(), after.completedTiles(after));
		assertEquals(List.of(), after.completedTiles(null));
	}

	static ClanData.Team team(String id, String name, String... members)
	{
		ClanData.Team t = new ClanData.Team();
		t.setId(id);
		t.setName(name);
		t.setMembers(members.length > 0 ? List.of(members) : null);
		return t;
	}

	static ClanData.Tile tile(String state, Integer have, Integer need, Integer pending, String item, String rsn)
	{
		ClanData.Tile t = new ClanData.Tile();
		t.setId("t");
		t.setName("Raids purples");
		t.setState(state);
		t.setHave(have);
		t.setNeed(need);
		t.setPending(pending);
		if (item != null)
		{
			ClanData.TileItem latest = new ClanData.TileItem();
			latest.setItem(item);
			latest.setRsn(rsn);
			t.setLatest(latest);
		}
		return t;
	}

	private static List<String> change(ClanData.Tile old, ClanData.Tile now)
	{
		List<String> out = new java.util.ArrayList<>();
		ClanView.tileChange("Bingo", "Team 2", old, now, out);
		return out;
	}

	static ClanData.Tile itemTile(String mode, int... haveNeed)
	{
		ClanData.Tile t = new ClanData.Tile();
		t.setName("Tile");
		t.setState("needed");
		t.setMode(mode);
		List<ClanData.TileNeed> items = new java.util.ArrayList<>();
		for (int i = 0; i < haveNeed.length; i += 2)
		{
			ClanData.TileNeed item = new ClanData.TileNeed();
			item.setHave(haveNeed[i]);
			item.setNeed(haveNeed[i + 1]);
			items.add(item);
		}
		t.setItems(items);
		return t;
	}

	@Test
	public void tileCountsComeFromItsItemsWhenItHasNoneOfItsOwn()
	{
		assertEquals("1/3", itemTile("all", 1, 3).progress());
		assertEquals("2/4", itemTile("all", 1, 1, 1, 3).progress());
		assertNull(itemTile("any", 0, 1, 0, 1).progress());
		assertTrue(itemTile("any", 0, 1, 1, 1).isComplete());
		assertTrue(itemTile("all", 3, 3).isComplete());
		assertEquals(false, itemTile("all", 1, 1, 0, 1).isComplete());
		assertEquals(List.of("Bingo: Team 2 had an item accepted for \"Tile\" (2/3)."),
			change(itemTile("all", 1, 3), itemTile("all", 2, 3)));
	}

	private static ClanData.Tile horns(String state, int have)
	{
		ClanData.Tile t = itemTile("all", have, 3);
		t.setId("horn");
		t.setName("3x Unicorn Horn");
		t.setState(state);
		t.getItems().get(0).setItemId(237);
		return t;
	}

	private static ClanView hornView(ClanData.Tile tile)
	{
		ClanData.Board board = new ClanData.Board();
		board.setTiles(List.of(tile));
		return view(List.of(new ClanView.EventView(event("bingo", "live", "2026-10-20T00:00:00Z", true), null, List.of(), board)), List.of());
	}

	@Test
	public void dropsThatCountOnTheOwnBoardAreAnnouncedAtOnce()
	{
		List<Record.Item> horn = List.of(new Record.Item(237, "Unicorn horn", 1, 100));
		List<ClanView.DropMatch> matches = hornView(horns("needed", 1)).dropMatches(horn);
		assertEquals(1, matches.size());
		assertEquals("Event bingo: Unicorn horn counts towards \"3x Unicorn Horn\". Drop submitted.", matches.get(0).getMessage());
		assertEquals(List.of(), hornView(horns("needed", 3)).dropMatches(horn));
		assertEquals(List.of(), hornView(horns("needed", 1)).dropMatches(List.of(new Record.Item(995, "Coins", 100, 1))));
	}

	@Test
	public void aDropThatFitsSeveralTilesGetsOneLine()
	{
		ClanData.Tile other = horns("needed", 0);
		other.setId("white");
		other.setName("Anything white");
		ClanData.Board board = new ClanData.Board();
		board.setTiles(List.of(horns("needed", 1), other));
		ClanView v = view(List.of(new ClanView.EventView(event("bingo", "live", "2026-10-20T00:00:00Z", true), null, List.of(), board)), List.of());
		List<ClanView.DropMatch> matches = v.dropMatches(List.of(new Record.Item(237, "Unicorn horn", 1, 100)));
		assertEquals(1, matches.size());
		assertEquals("Event bingo: Unicorn horn fits \"3x Unicorn Horn\" and \"Anything white\". Drop submitted.", matches.get(0).getMessage());
		assertEquals(2, matches.get(0).getKeys().size());
	}

	@Test
	public void agoIsShort()
	{
		Instant now = Instant.parse("2026-11-05T18:00:00Z");
		assertEquals("just now", ClanView.ago(now.minusSeconds(20), now));
		assertEquals("5m ago", ClanView.ago(now.minusSeconds(300), now));
		assertEquals("3h ago", ClanView.ago(now.minusSeconds(3 * 3600 + 60), now));
		assertEquals("2d ago", ClanView.ago(now.minusSeconds(2 * 86400), now));
	}

	@Test
	public void anAnnouncedDropSkipsTheWebsitesSubmittedLineButNotTheCompletion()
	{
		ClanView before = hornView(horns("needed", 1));
		String key = before.dropMatches(List.of(new Record.Item(237, "Unicorn horn", 1, 100))).get(0).getKeys().get(0);
		ClanView pending = hornView(horns("pending", 1));
		assertEquals(List.of(), pending.tileChanges(before, (k, rsn) -> k.equals(key)));
		assertEquals(1, pending.tileChanges(before).size());
		assertEquals(List.of("Event bingo: Your team completed \"3x Unicorn Horn\"."),
			hornView(horns("accepted", 3)).tileChanges(pending, (k, rsn) -> true));
	}

	@Test
	public void tilesThatNeedSeveralItemsReportEachStep()
	{
		assertEquals(List.of("Bingo: Str4tty (Team 2) submitted Twisted bow for \"Raids purples\". 0 of 3 accepted so far."),
			change(tile("needed", 0, 3, 0, null, null), tile("pending", 0, 3, 1, "Twisted bow", "Str4tty")));
		assertEquals(List.of("Bingo: Team 2 had an item accepted for \"Raids purples\" (1/3)."),
			change(tile("pending", 0, 3, 1, "Twisted bow", "Str4tty"), tile("needed", 1, 3, 0, "Twisted bow", "Str4tty")));
		assertEquals(List.of("Bingo: Team 2 completed \"Raids purples\"."),
			change(tile("pending", 2, 3, 1, "Elder maul", "Rozzle"), tile("accepted", 3, 3, 0, "Elder maul", "Rozzle")));
		assertEquals(List.of(), change(tile("needed", 1, 3, 0, null, null), tile("needed", 1, 3, 0, null, null)));
	}

	@Test
	public void reportsEveryTeamsBoardInEventsTheMemberIsIn()
	{
		ClanData.Event bingo = event("bingo", "live", "2026-10-20T00:00:00Z", true);
		ClanData.Event other = event("other", "live", "2026-10-20T00:00:00Z", false);
		List<ClanData.Team> teams = List.of(team("t1", "Team 1"), team("t2", "Team 2"));
		ClanView before = view(List.of(
			new ClanView.EventView(bingo, null, teams, null, java.util.Map.of("t1", board("a", "needed"), "t2", board("a", "pending"))),
			new ClanView.EventView(other, null, teams, null, java.util.Map.of("t1", board("a", "needed")))), List.of());
		ClanView after = view(List.of(
			new ClanView.EventView(bingo, null, teams, null, java.util.Map.of("t1", board("a", "pending"), "t2", board("a", "accepted"))),
			new ClanView.EventView(other, null, teams, null, java.util.Map.of("t1", board("a", "accepted")))), List.of());
		assertEquals(List.of("Event bingo: Team 1 submitted an item for \"Tile a\".", "Event bingo: Team 2 completed \"Tile a\"."),
			after.tileChanges(before));
	}

	@Test
	public void teamTagsUseNumbersWhenEveryTeamHasOne()
	{
		assertEquals(java.util.Map.of("a", "1", "b", "2"), ClanView.tags(List.of(team("a", "Team 1"), team("b", "Team 2"))));
		assertEquals(java.util.Map.of("a", "A", "b", "B"), ClanView.tags(List.of(team("a", "Asgardians"), team("b", "Team 2"))));
		ClanData.Team tagged = team("c", "Clan Europe");
		tagged.setTag("EU");
		assertEquals("EU", ClanView.tags(List.of(team("a", "Asgardians"), tagged)).get("c"));
	}

	@Test
	public void ownTeamKeepsItsMembersAndTeamsStayInIdOrder()
	{
		ClanData.Event bingo = event("bingo", "live", "2026-10-20T00:00:00Z", true);
		ClanData.Team standingB = team("b", "Team B");
		standingB.setRank(1);
		ClanData.Team standingA = team("a", "Team A");
		standingA.setRank(2);
		standingA.setPoints(12.0);
		ClanView.EventView v = new ClanView.EventView(bingo, team("a", "Team A", "Rozzle", "Str4tty"), List.of(standingB, standingA), null);
		assertEquals(List.of("a", "b"), v.teams().stream().map(ClanData.Team::getId).collect(java.util.stream.Collectors.toList()));
		assertEquals(List.of("Rozzle", "Str4tty"), v.teams().get(0).getMembers());
		assertEquals(12.0, v.teams().get(0).getPoints(), 0);
	}

	@Test
	public void currentEventsLeaveOutBossAndSkillOfTheWeek()
	{
		ClanData.Event botw = event("botw", "live", "2026-10-20T00:00:00Z", true);
		botw.setType("botw");
		ClanData.Event bingo = event("bingo", "live", "2026-10-20T00:00:00Z", false);
		ClanData.Event later = event("later", "upcoming", "2026-11-20T00:00:00Z", false);
		ClanView v = view(List.of(new ClanView.EventView(botw, null, List.of(), null), new ClanView.EventView(bingo, null, List.of(), null),
			new ClanView.EventView(later, null, List.of(), null)), List.of());
		assertEquals(List.of("bingo"), v.current().stream().map(e -> e.getEvent().getId()).collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void newlyLiveSkipsTheFirstFetch()
	{
		ClanData.Event botw = event("botw", "live", "2026-10-22T16:00:00Z", true);
		ClanView live = view(List.of(new ClanView.EventView(botw, null, List.of(), null)), List.of());
		assertTrue(live.newlyLive(ClanView.empty()).isEmpty());

		ClanData.Event upcoming = event("botw", "upcoming", "2026-10-22T16:00:00Z", true);
		ClanView before = view(List.of(new ClanView.EventView(upcoming, null, List.of(), null)), List.of());
		assertEquals(1, live.newlyLive(before).size());
	}

	@Test
	public void remindsSignedUpEventsOnceInsideTheWindow()
	{
		ClanData.Event soon = event("soon", "upcoming", "2026-10-22T17:10:00Z", true);
		ClanData.Event later = event("later", "upcoming", "2026-10-22T18:00:00Z", true);
		ClanData.Event notMine = event("other", "upcoming", "2026-10-22T17:05:00Z", false);
		ClanView v = view(List.of(
			new ClanView.EventView(later, null, List.of(), null),
			new ClanView.EventView(soon, null, List.of(), null),
			new ClanView.EventView(notMine, null, List.of(), null)), List.of());
		Set<String> reminded = new HashSet<>();
		List<ClanData.Event> due = v.dueReminders(NOW, Duration.ofMinutes(15), reminded);
		assertEquals(List.of(soon), due);
		reminded.add("soon");
		assertTrue(v.dueReminders(NOW, Duration.ofMinutes(15), reminded).isEmpty());
	}

	@Test
	public void upcomingSortedBySoonest()
	{
		ClanData.Event a = event("a", "upcoming", "2026-11-09T13:00:00Z", false);
		ClanData.Event b = event("b", "upcoming", "2026-10-26T13:00:00Z", false);
		ClanView v = view(List.of(new ClanView.EventView(a, null, List.of(), null), new ClanView.EventView(b, null, List.of(), null)), List.of());
		assertEquals("b", v.upcoming().get(0).getEvent().getId());
	}

	@Test
	public void groupsEventsLiveFirstThenSoonest()
	{
		ClanData.Event botwLater = event("b2", "upcoming", "2026-11-23T13:00:00Z", false);
		botwLater.setType("botw");
		ClanData.Event botwLive = event("b1", "live", "2026-10-26T13:00:00Z", false);
		botwLive.setType("botw");
		ClanData.Event sotw = event("s1", "upcoming", "2026-11-09T13:00:00Z", false);
		sotw.setType("sotw");
		ClanData.Event toz = event("toz", "live", "2026-10-18T00:00:00Z", false);
		toz.setKind("toz");
		ClanData.Event movie = event("m", "upcoming", "2026-11-01T20:00:00Z", false);
		movie.setKind("calendar");
		ClanView v = view(List.of(new ClanView.EventView(botwLater, null, List.of(), null), new ClanView.EventView(botwLive, null, List.of(), null),
			new ClanView.EventView(sotw, null, List.of(), null), new ClanView.EventView(toz, null, List.of(), null),
			new ClanView.EventView(movie, null, List.of(), null)), List.of());

		assertEquals(List.of("b1", "b2"), ids(v.inGroup("botw")));
		assertEquals(List.of("s1"), ids(v.inGroup("sotw")));
		assertEquals(List.of("toz"), ids(v.inGroup("bingo")));
		assertEquals(List.of("m"), ids(v.inGroup("other")));
	}

	private static List<String> ids(List<ClanView.EventView> views)
	{
		return views.stream().map(view -> view.getEvent().getId()).collect(java.util.stream.Collectors.toList());
	}

	@Test
	public void onlyOpensLinksOnTheClanWebsite()
	{
		String prod = "https://asgardiansclan.com";
		assertTrue(ClanTabs.safeLink("https://asgardiansclan.com/events/tug-of-zaros", prod));
		assertTrue(ClanTabs.safeLink("https://www.asgardiansclan.com/events", prod));
		assertTrue("test server links to the live site", ClanTabs.safeLink("https://asgardiansclan.com/events", "https://dev.asgardiansclan.com"));
		assertTrue(ClanTabs.safeLink("https://dev.asgardiansclan.com/news/7", "https://dev.asgardiansclan.com"));
		assertTrue(ClanTabs.safeLink("http://10.0.0.5:3000/events", "http://10.0.0.5:3000"));

		org.junit.Assert.assertFalse(ClanTabs.safeLink("https://evil.example/asgardiansclan.com", prod));
		org.junit.Assert.assertFalse(ClanTabs.safeLink("https://asgardiansclan.com.evil.example/", prod));
		org.junit.Assert.assertFalse(ClanTabs.safeLink("javascript:alert(1)", prod));
		org.junit.Assert.assertFalse(ClanTabs.safeLink("file:///C:/Windows", prod));
		org.junit.Assert.assertFalse(ClanTabs.safeLink(null, prod));
	}

	@Test
	public void countdownAndLocalTime()
	{
		assertEquals("2d 4h", ClanView.countdown(NOW, NOW.plus(Duration.ofHours(52))));
		assertEquals("1h 30m", ClanView.countdown(NOW, NOW.plus(Duration.ofMinutes(90))));
		assertEquals("12m", ClanView.countdown(NOW, NOW.plus(Duration.ofMinutes(12))));
		assertEquals("now", ClanView.countdown(NOW, NOW.minusSeconds(5)));
		assertEquals("Mon 26 Oct 13:00", ClanView.localTime(Instant.parse("2026-10-26T13:00:00Z"), java.time.ZoneOffset.UTC));
	}
}
