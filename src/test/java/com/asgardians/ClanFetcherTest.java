package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Loads events, team, standings, board and news from a stand-in website, in the shapes PLUGIN_API.md documents. */
public class ClanFetcherTest
{
	private HttpServer server;
	private String address;
	private final ClanFetcher fetcher = new ClanFetcher(new ApiClient(new OkHttpClient(), new Gson()));
	private volatile boolean boardFails;
	private volatile int boardStatus = 200;

	@Before
	public void start() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/v1/plugin/events", exchange ->
		{
			String path = exchange.getRequestURI().getPath();
			if (path.endsWith("/events"))
			{
				respond(exchange, 200, "{\"events\":["
					+ "{\"id\":\"tug-of-zaros\",\"name\":\"Clan vs Clan - Tug of Zaros\",\"type\":\"bingo\",\"status\":\"live\","
					+ "\"startsAt\":\"2026-10-18T00:00:00Z\",\"endsAt\":\"2026-10-28T00:00:00Z\",\"codeword\":\"Zaros-7\","
					+ "\"signedUp\":true,\"teamId\":\"asg\",\"hasBoard\":true},"
					+ "{\"id\":\"botw-toa\",\"name\":\"Tombs of Amascut\",\"type\":\"botw\",\"status\":\"upcoming\","
					+ "\"startsAt\":\"2026-10-26T13:00:00Z\",\"endsAt\":\"2026-11-02T13:00:00Z\",\"signedUp\":false,\"hasBoard\":false},"
					+ "{\"id\":\"winter-bingo\",\"name\":\"Winter Bingo\",\"type\":\"bingo\",\"kind\":\"bingo\",\"status\":\"upcoming\","
					+ "\"startsAt\":\"2026-12-01T18:00:00Z\",\"endsAt\":\"2026-12-10T18:00:00Z\","
					+ "\"signup\":{\"open\":true,\"closesAt\":\"2026-11-20T20:00:00Z\",\"count\":37,\"cap\":50,\"myStatus\":\"signedUp\",\"paid\":false,"
					+ "\"note\":\"Buy-in 10m\",\"form\":[{\"key\":\"hoursPerDay\",\"label\":\"Hours per day\",\"type\":\"number\",\"required\":true,\"min\":0,\"max\":24}]}},"
					+ "{\"id\":\"old\",\"name\":\"Old\",\"status\":\"finished\"}]}");
			}
			else if (path.endsWith("/team"))
			{
				respond(exchange, 200, "{\"id\":\"asg\",\"name\":\"Asgardians\",\"captain\":\"Rozzle\",\"members\":[\"Rozzle\",\"Str4tty\"],"
					+ "\"points\":155,\"pointsLabel\":\"notches\",\"rank\":1}");
			}
			else if (path.endsWith("/teams"))
			{
				respond(exchange, 200, "{\"teams\":[{\"id\":\"asg\",\"name\":\"Asgardians\",\"points\":155,\"rank\":1},"
					+ "{\"id\":\"eu\",\"name\":\"Clan Europe\",\"points\":136,\"rank\":2}]}");
			}
			else if (path.endsWith("/board"))
			{
				if (boardFails)
				{
					respond(exchange, 500, "{\"error\":\"boom\"}");
					return;
				}
				if (boardStatus != 200)
				{
					respond(exchange, boardStatus, "{}");
					return;
				}
				respond(exchange, 200, "{\"done\":1,\"total\":2,\"tiles\":["
					+ "{\"id\":\"cox\",\"name\":\"CoX purple\",\"group\":\"Raids\",\"state\":\"accepted\",\"points\":3},"
					+ "{\"id\":\"tob\",\"name\":\"ToB purple\",\"group\":\"Raids\",\"state\":\"needed\"}]}");
			}
		});
		server.createContext("/api/v1/plugin/announcements", exchange ->
			respond(exchange, 200, "{\"announcements\":[{\"id\":7,\"title\":\"BOTW next week\",\"body\":\"ToA from Monday.\","
				+ "\"postedAt\":\"2026-10-22T12:00:00Z\",\"postedBy\":\"Dolorosa\"}]}"));
		server.start();
		address = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@After
	public void stop()
	{
		server.stop(0);
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody())
		{
			out.write(bytes);
		}
	}

	private ClanFetcher.Result fetch(boolean withBoards, ClanView previous) throws Exception
	{
		CompletableFuture<ClanFetcher.Result> done = new CompletableFuture<>();
		fetcher.fetch(address, "key", withBoards, previous, done::complete);
		return done.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void loadsEverythingForLiveAndUpcomingEvents() throws Exception
	{
		ClanFetcher.Result result = fetch(true, null);
		ClanView view = result.getView();
		assertEquals(0, result.getFailureStatus());
		assertNull(view.getError());
		assertEquals(3, view.getEvents().size());

		ClanData.Event bingo = view.upcoming().get(1).getEvent();
		assertEquals("bingo", bingo.getKind());
		assertEquals(Integer.valueOf(37), bingo.getSignup().getCount());
		assertEquals(Boolean.FALSE, bingo.getSignup().getPaid());
		assertEquals(24.0, bingo.getSignup().getForm().get(0).getMax(), 0);
		assertTrue(bingo.isIn());

		ClanView.EventView live = view.live().get(0);
		assertEquals("Zaros-7", live.getEvent().getCodeword());
		assertEquals("Rozzle", live.getTeam().getCaptain());
		assertEquals(2, live.getStandings().size());
		assertEquals("accepted", live.getBoard().getTiles().get(0).getState());

		ClanView.EventView upcoming = view.upcoming().get(0);
		assertNull(upcoming.getTeam());
		assertNull(upcoming.getBoard());

		assertEquals("BOTW next week", view.getAnnouncements().get(0).getTitle());
	}

	@Test
	public void keepsPreviousBoardWhenOneFailsOrIsSkipped() throws Exception
	{
		ClanView first = fetch(true, null).getView();
		ClanView skipped = fetch(false, first).getView();
		assertNotNull(skipped.live().get(0).getBoard());

		boardFails = true;
		ClanFetcher.Result failed = fetch(true, first);
		assertEquals(500, failed.getFailureStatus());
		assertNotNull(failed.getView().getError());
	}

	@Test
	public void aMissingEndpointIsNotAnError() throws Exception
	{
		boardStatus = 404;
		ClanFetcher.Result result = fetch(true, null);
		assertEquals(0, result.getFailureStatus());
		assertNull(result.getView().getError());
		assertNull(result.getView().live().get(0).getBoard());
	}

	@Test
	public void unreachableServerKeepsLastView() throws Exception
	{
		ClanView first = fetch(true, null).getView();
		server.stop(0);
		ClanFetcher.Result result = fetch(true, first);
		assertEquals("Can't reach the clan website", result.getView().getError());
		assertEquals(3, result.getView().getEvents().size());
	}
}
