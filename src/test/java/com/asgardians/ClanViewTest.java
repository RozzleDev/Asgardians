package com.asgardians;

import static org.junit.Assert.assertEquals;
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

	@Test
	public void reportsOnlyReviewedTileChanges()
	{
		ClanData.Event bingo = event("bingo", "live", "2026-10-20T00:00:00Z", true);
		ClanView before = view(List.of(new ClanView.EventView(bingo, null, List.of(), board("a", "pending", "b", "needed", "c", "pending"))), List.of());
		ClanView after = view(List.of(new ClanView.EventView(bingo, null, List.of(), board("a", "accepted", "b", "pending", "c", "declined"))), List.of());
		List<String> changes = after.tileChanges(before);
		assertEquals(2, changes.size());
		assertTrue(changes.get(0).contains("\"Tile a\" was accepted"));
		assertTrue(changes.get(1).contains("\"Tile c\" was declined"));
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
