package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Runs the API client against a tiny local stand-in for the website. */
public class ApiClientTest
{
	private HttpServer server;
	private String address;
	private final Gson gson = new Gson();
	private final ApiClient api = new ApiClient(new OkHttpClient(), gson);
	private volatile String lastBody;
	private volatile String lastKey;
	private volatile String lastContentType;

	@Before
	public void start() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/v1/plugin/register", exchange ->
		{
			JsonObject body = gson.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
			boolean onRoster = "Str4tty".equals(body.get("rsn").getAsString());
			respond(exchange, onRoster ? 200 : 403, onRoster
				? "{\"installKey\":\"key-123\",\"displayName\":\"Str4tty\"}"
				: "{\"error\":\"Not on the roster\"}");
		});
		server.createContext("/api/v1/plugin/records", exchange ->
		{
			lastKey = exchange.getRequestHeaders().getFirst(ApiClient.INSTALL_KEY_HEADER);
			lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			respond(exchange, "key-123".equals(lastKey) ? 202 : 401, "{}");
		});
		server.createContext("/api/v1/plugin/pbs/full", exchange ->
		{
			lastKey = exchange.getRequestHeaders().getFirst(ApiClient.INSTALL_KEY_HEADER);
			lastContentType = exchange.getRequestMethod();
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			respond(exchange, 200, "{}");
		});
		server.createContext("/api/v1/plugin/clog/full", exchange ->
		{
			lastKey = exchange.getRequestHeaders().getFirst(ApiClient.INSTALL_KEY_HEADER);
			lastContentType = exchange.getRequestMethod();
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			respond(exchange, 200, "{}");
		});
		server.createContext("/api/v1/plugin/events", exchange ->
		{
			lastKey = exchange.getRequestHeaders().getFirst(ApiClient.INSTALL_KEY_HEADER);
			lastContentType = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (exchange.getRequestURI().getPath().contains("closed-event"))
			{
				respond(exchange, 409, "{\"error\":\"Sign-ups for this event have closed.\"}");
			}
			else if ("DELETE".equals(exchange.getRequestMethod()))
			{
				respond(exchange, 200, "{\"myStatus\":\"none\"}");
			}
			else
			{
				respond(exchange, 200, "{\"myStatus\":\"waitlist\"}");
			}
		});
		server.createContext("/api/v1/plugin/config", exchange ->
			respond(exchange, 200, "{\"killSwitch\":true,\"screenshots\":{\"minLootValue\":5000000}}"));
		server.start();
		address = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@After
	public void stop()
	{
		server.stop(0);
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody())
		{
			out.write(bytes);
		}
	}

	@Test
	public void registersMemberOnRoster() throws Exception
	{
		CompletableFuture<ApiClient.RegisterResult> done = new CompletableFuture<>();
		api.register(address, "Str4tty", 42L, "NORMAL", done::complete);
		ApiClient.RegisterResult result = done.get(5, TimeUnit.SECONDS);
		assertEquals("key-123", result.getInstallKey());
		assertEquals("Str4tty", result.getDisplayName());
	}

	@Test
	public void refusesRsnNotOnRoster() throws Exception
	{
		CompletableFuture<ApiClient.RegisterResult> done = new CompletableFuture<>();
		api.register(address, "Stranger", 7L, "NORMAL", done::complete);
		ApiClient.RegisterResult result = done.get(5, TimeUnit.SECONDS);
		assertEquals(403, result.getStatus());
		assertNull(result.getInstallKey());
		assertEquals("Not on the roster", result.getError());
	}

	@Test
	public void sendsRecordsAsJsonWithoutTransientFields() throws Exception
	{
		Record record = new Record(Record.Type.loot, "2026-10-22T12:00:00Z", 416);
		record.setSource(new Record.Source("Vardorvis", 12223));
		record.setItems(List.of(new Record.Item(26243, "Virtus robe top", 1, 28_900_000)));
		record.totalValue = 28_900_000;

		CompletableFuture<ApiClient.SubmitResult> done = new CompletableFuture<>();
		api.submitRecords(address, "key-123", new ApiClient.AccountInfo(42L, "Str4tty", "NORMAL"), List.of(record), Map.of(), done::complete);
		assertTrue(done.get(5, TimeUnit.SECONDS).ok());

		assertEquals("key-123", lastKey);
		assertTrue(lastContentType.startsWith("application/json"));
		JsonObject body = gson.fromJson(lastBody, JsonObject.class);
		assertEquals("Str4tty", body.getAsJsonObject("account").get("rsn").getAsString());
		JsonObject sent = body.getAsJsonArray("records").get(0).getAsJsonObject();
		assertEquals("loot", sent.get("type").getAsString());
		assertEquals(record.getId(), sent.get("id").getAsString());
		assertTrue(!sent.has("totalValue") && !sent.has("createdMillis") && !sent.has("awaitingScreenshot"));
	}

	@Test
	public void sendsScreenshotsAsMultipart() throws Exception
	{
		Record record = new Record(Record.Type.pet, "2026-10-22T12:00:00Z", 416);
		record.setScreenshot(true);
		CompletableFuture<ApiClient.SubmitResult> done = new CompletableFuture<>();
		api.submitRecords(address, "key-123", new ApiClient.AccountInfo(42L, "Str4tty", "NORMAL"), List.of(record),
			Map.of(record.getId(), new byte[]{(byte) 0x89, 'P', 'N', 'G'}), done::complete);
		assertTrue(done.get(5, TimeUnit.SECONDS).ok());
		assertTrue(lastContentType.startsWith("multipart/form-data"));
		assertTrue(lastBody.contains("name=\"payload\""));
		assertTrue(lastBody.contains("name=\"screenshot_" + record.getId() + "\""));
	}

	@Test
	public void syncsAllPersonalBests() throws Exception
	{
		PersonalBest vorkath = PersonalBest.of("vorkath", 84.6);
		vorkath.setKc(512);
		CompletableFuture<ApiClient.SubmitResult> done = new CompletableFuture<>();
		api.syncPersonalBests(address, "key-123", new ApiClient.AccountInfo(42L, "Str4tty", "NORMAL"),
			List.of(vorkath, PersonalBest.of("theatre of blood 3 players", 1200)), done::complete);
		assertTrue(done.get(5, TimeUnit.SECONDS).ok());
		assertEquals("PUT", lastContentType);
		assertEquals("key-123", lastKey);
		JsonObject first = gson.fromJson(lastBody, JsonObject.class).getAsJsonArray("pbs").get(0).getAsJsonObject();
		assertEquals("vorkath", first.get("key").getAsString());
		assertEquals(84.6, first.get("seconds").getAsDouble(), 0.0001);
		assertEquals(141, first.get("ticks").getAsInt());
		assertEquals(512, first.get("kc").getAsInt());
		assertTrue(first.get("precise").getAsBoolean());
	}

	@Test
	public void syncsWholeCollectionLog() throws Exception
	{
		CompletableFuture<ApiClient.SubmitResult> done = new CompletableFuture<>();
		api.syncCollectionLog(address, "key-123", new ApiClient.AccountInfo(42L, "Str4tty", "NORMAL"),
			List.of(new ClogCapture.Item(26243, "Virtus robe top", 1)), 1456, 1699, done::complete);
		assertTrue(done.get(5, TimeUnit.SECONDS).ok());
		assertEquals("PUT", lastContentType);
		JsonObject body = gson.fromJson(lastBody, JsonObject.class);
		assertEquals(1456, body.get("obtained").getAsInt());
		assertEquals(1699, body.get("total").getAsInt());
		JsonObject item = body.getAsJsonArray("items").get(0).getAsJsonObject();
		assertEquals(26243, item.get("id").getAsInt());
		assertEquals(1, item.get("qty").getAsInt());
	}

	@Test
	public void signsUpWithAnswersAndWithdraws() throws Exception
	{
		CompletableFuture<ApiClient.GetResult<ClanData.SignupResult>> joined = new CompletableFuture<>();
		java.util.Map<String, Object> answers = new java.util.LinkedHashMap<>();
		answers.put("hoursPerDay", 4L);
		answers.put("timezone", "Europe/London");
		api.signUp(address, "key-123", "winter-bingo", answers, joined::complete);
		ApiClient.GetResult<ClanData.SignupResult> join = joined.get(5, TimeUnit.SECONDS);
		assertEquals("waitlist", join.getBody().getMyStatus());
		assertEquals("POST /api/v1/plugin/events/winter-bingo/signup", lastContentType);
		assertEquals("key-123", lastKey);
		JsonObject sent = gson.fromJson(lastBody, JsonObject.class).getAsJsonObject("answers");
		assertEquals(4, sent.get("hoursPerDay").getAsInt());
		assertEquals("Europe/London", sent.get("timezone").getAsString());

		CompletableFuture<ApiClient.GetResult<ClanData.SignupResult>> left = new CompletableFuture<>();
		api.withdraw(address, "key-123", "winter-bingo", left::complete);
		assertEquals("none", left.get(5, TimeUnit.SECONDS).getBody().getMyStatus());
		assertEquals("DELETE /api/v1/plugin/events/winter-bingo/signup", lastContentType);
	}

	@Test
	public void closedSignupReturnsTheServersReason() throws Exception
	{
		CompletableFuture<ApiClient.GetResult<ClanData.SignupResult>> done = new CompletableFuture<>();
		api.signUp(address, "key-123", "closed-event", java.util.Map.of(), done::complete);
		ApiClient.GetResult<ClanData.SignupResult> result = done.get(5, TimeUnit.SECONDS);
		assertEquals(409, result.getStatus());
		assertEquals("Sign-ups for this event have closed.", result.getError());
	}

	@Test
	public void wrongKeyGets401() throws Exception
	{
		CompletableFuture<ApiClient.SubmitResult> done = new CompletableFuture<>();
		api.submitRecords(address, "old-key", new ApiClient.AccountInfo(42L, "Str4tty", "NORMAL"),
			List.of(new Record(Record.Type.pet, "2026-10-22T12:00:00Z", 416)), Map.of(), done::complete);
		assertEquals(401, done.get(5, TimeUnit.SECONDS).getStatus());
	}

	@Test
	public void readsServerSettings() throws Exception
	{
		CompletableFuture<ServerSettings> done = new CompletableFuture<>();
		api.fetchSettings(address, done::complete);
		ServerSettings settings = done.get(5, TimeUnit.SECONDS);
		assertTrue(settings.isKillSwitch());
		assertEquals(5_000_000, settings.getScreenshots().getMinLootValue());
		assertTrue(settings.getScreenshots().isPets());
	}

	@Test
	public void endpointKeepsBasePath()
	{
		assertEquals("https://dev.asgardiansclan.com/api/v1/plugin/records",
			ApiClient.endpoint("https://dev.asgardiansclan.com", "records").toString());
		assertEquals("http://10.0.0.5:3000/api/v1/plugin/register",
			ApiClient.endpoint("http://10.0.0.5:3000", "register").toString());
	}
}
