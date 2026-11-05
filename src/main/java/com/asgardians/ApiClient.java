package com.asgardians;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talks to /api/v1/plugin/* on the configured address. Every call is asynchronous and reports back on OkHttp's thread.
 */
@Slf4j
class ApiClient
{
	static final String PLUGIN_VERSION = "0.1.0";
	static final String INSTALL_KEY_HEADER = "X-Install-Key";

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final MediaType PNG = MediaType.get("image/png");

	@Value
	static class RegisterResult
	{
		/** HTTP status, or 0 when the server couldn't be reached. */
		int status;
		String installKey;
		String displayName;
		/** True when the server accepted the RSN but holds its records for staff review (account hash mismatch). */
		boolean held;
		String error;
	}

	@Value
	static class SubmitResult
	{
		int status;
		String error;

		boolean ok()
		{
			return status >= 200 && status < 300;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;

	ApiClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	void register(String address, String rsn, long accountHash, String accountType, Consumer<RegisterResult> done)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);
		body.addProperty("accountHash", accountHash);
		body.addProperty("accountType", accountType);
		body.addProperty("pluginVersion", PLUGIN_VERSION);
		Request request = new Request.Builder()
			.url(endpoint(address, "register"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				done.accept(new RegisterResult(0, null, null, false, e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody != null ? responseBody.string() : "";
					if (!response.isSuccessful())
					{
						done.accept(new RegisterResult(response.code(), null, null, false, errorFrom(text)));
						return;
					}
					JsonObject json = gson.fromJson(text, JsonObject.class);
					String key = json != null && json.has("installKey") ? json.get("installKey").getAsString() : null;
					String name = json != null && json.has("displayName") ? json.get("displayName").getAsString() : rsn;
					boolean held = json != null && json.has("held") && json.get("held").getAsBoolean();
					done.accept(key == null
						? new RegisterResult(response.code(), null, null, false, "No install key in the response")
						: new RegisterResult(response.code(), key, name, held, null));
				}
				catch (IOException | JsonParseException | IllegalStateException e)
				{
					done.accept(new RegisterResult(response.code(), null, null, false, "Bad response: " + e.getMessage()));
				}
			}
		});
	}

	void fetchSettings(String address, Consumer<ServerSettings> done)
	{
		Request request = new Request.Builder().url(endpoint(address, "config")).get().build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Asgardians config fetch failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					if (response.isSuccessful() && responseBody != null)
					{
						ServerSettings settings = gson.fromJson(responseBody.string(), ServerSettings.class);
						if (settings != null)
						{
							if (settings.getScreenshots() == null)
							{
								settings.setScreenshots(new ServerSettings.Screenshots());
							}
							done.accept(settings);
						}
					}
				}
				catch (IOException | JsonParseException e)
				{
					log.debug("Asgardians config unreadable: {}", e.getMessage());
				}
			}
		});
	}

	/**
	 * Sends a batch of records. With no screenshots the body is JSON; with screenshots it is multipart, with the JSON in a
	 * "payload" part and each image in a "screenshot_<record id>" part.
	 */
	void submitRecords(String address, String installKey, AccountInfo account, List<Record> records,
		Map<String, byte[]> screenshots, Consumer<SubmitResult> done)
	{
		JsonObject body = new JsonObject();
		body.add("account", gson.toJsonTree(account));
		body.addProperty("pluginVersion", PLUGIN_VERSION);
		body.add("records", gson.toJsonTree(records));
		String json = gson.toJson(body);

		RequestBody requestBody;
		if (screenshots.isEmpty())
		{
			requestBody = RequestBody.create(JSON, json);
		}
		else
		{
			MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
				.addFormDataPart("payload", null, RequestBody.create(JSON, json));
			screenshots.forEach((id, png) ->
				multipart.addFormDataPart("screenshot_" + id, id + ".png", RequestBody.create(PNG, png)));
			requestBody = multipart.build();
		}

		Request request = new Request.Builder()
			.url(endpoint(address, "records"))
			.header(INSTALL_KEY_HEADER, installKey)
			.header("Idempotency-Key", idempotencyKey(records))
			.post(requestBody)
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				done.accept(new SubmitResult(0, e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody != null ? responseBody.string() : "";
					done.accept(new SubmitResult(response.code(), response.isSuccessful() ? null : errorFrom(text)));
				}
				catch (IOException e)
				{
					done.accept(new SubmitResult(response.code(), e.getMessage()));
				}
			}
		});
	}

	/** Sends every PB RuneLite has stored for this account. The server upserts each by key. */
	void syncPersonalBests(String address, String installKey, AccountInfo account, List<PersonalBest> pbs,
		Consumer<SubmitResult> done)
	{
		JsonObject body = new JsonObject();
		body.add("account", gson.toJsonTree(account));
		body.addProperty("pluginVersion", PLUGIN_VERSION);
		body.add("pbs", gson.toJsonTree(pbs));
		put(endpoint(address, "pbs/full"), installKey, gson.toJson(body), done);
	}

	/** Sends every obtained collection log item. The server replaces the member's collection log with it. */
	void syncCollectionLog(String address, String installKey, AccountInfo account, List<ClogCapture.Item> items,
		int obtained, int total, Consumer<SubmitResult> done)
	{
		JsonObject body = new JsonObject();
		body.add("account", gson.toJsonTree(account));
		body.addProperty("pluginVersion", PLUGIN_VERSION);
		body.addProperty("obtained", obtained);
		body.addProperty("total", total);
		body.add("items", gson.toJsonTree(items));
		put(endpoint(address, "clog/full"), installKey, gson.toJson(body), done);
	}

	@Value
	static class GetResult<T>
	{
		int status;
		T body;
		String error;

		boolean ok()
		{
			return status >= 200 && status < 300 && body != null;
		}
	}

	/** A read-only call, parsed into the given class. Build the URL with {@link #endpoint}. */
	<T> void get(HttpUrl url, String installKey, Class<T> type, Consumer<GetResult<T>> done)
	{
		Request request = new Request.Builder().url(url).header(INSTALL_KEY_HEADER, installKey).get().build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				done.accept(new GetResult<>(0, null, e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody != null ? responseBody.string() : "";
					if (!response.isSuccessful())
					{
						done.accept(new GetResult<>(response.code(), null, errorFrom(text)));
						return;
					}
					done.accept(new GetResult<>(response.code(), gson.fromJson(text, type), null));
				}
				catch (IOException | JsonParseException e)
				{
					done.accept(new GetResult<>(response.code(), null, "Bad response: " + e.getMessage()));
				}
			}
		});
	}

	/** Signs the member up for an event with their form answers. */
	void signUp(String address, String installKey, String eventId, java.util.Map<String, Object> answers,
		Consumer<GetResult<ClanData.SignupResult>> done)
	{
		JsonObject body = new JsonObject();
		body.add("answers", gson.toJsonTree(answers));
		send(new Request.Builder().url(endpoint(address, "events/" + eventId + "/signup"))
			.header(INSTALL_KEY_HEADER, installKey)
			.post(RequestBody.create(JSON, gson.toJson(body))).build(), ClanData.SignupResult.class, done);
	}

	/** Withdraws the member from an event. */
	/** Puts a drop that fits several tiles on the one the member picked. */
	void chooseTile(String address, String installKey, String eventId, String dropId, String tileId,
		Consumer<GetResult<ClanData.TileChoice>> done)
	{
		JsonObject body = new JsonObject();
		body.addProperty("tileId", tileId);
		send(new Request.Builder().url(endpoint(address, "events/" + eventId + "/drops/" + dropId + "/tile"))
			.header(INSTALL_KEY_HEADER, installKey)
			.post(RequestBody.create(JSON, gson.toJson(body))).build(), ClanData.TileChoice.class, done);
	}

	void withdraw(String address, String installKey, String eventId, Consumer<GetResult<ClanData.SignupResult>> done)
	{
		send(new Request.Builder().url(endpoint(address, "events/" + eventId + "/signup"))
			.header(INSTALL_KEY_HEADER, installKey)
			.delete().build(), ClanData.SignupResult.class, done);
	}

	private <T> void send(Request request, Class<T> type, Consumer<GetResult<T>> done)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				done.accept(new GetResult<>(0, null, e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody != null ? responseBody.string() : "";
					if (!response.isSuccessful())
					{
						done.accept(new GetResult<>(response.code(), null, errorFrom(text)));
						return;
					}
					T body = text.isEmpty() ? null : gson.fromJson(text, type);
					done.accept(new GetResult<>(response.code(), body, null));
				}
				catch (IOException | JsonParseException e)
				{
					done.accept(new GetResult<>(response.code(), null, "Bad response: " + e.getMessage()));
				}
			}
		});
	}

	private void put(HttpUrl url, String installKey, String json, Consumer<SubmitResult> done)
	{
		Request request = new Request.Builder()
			.url(url)
			.header(INSTALL_KEY_HEADER, installKey)
			.put(RequestBody.create(JSON, json))
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				done.accept(new SubmitResult(0, e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					String text = responseBody != null ? responseBody.string() : "";
					done.accept(new SubmitResult(response.code(), response.isSuccessful() ? null : errorFrom(text)));
				}
				catch (IOException e)
				{
					done.accept(new SubmitResult(response.code(), e.getMessage()));
				}
			}
		});
	}

	static HttpUrl endpoint(String address, String path)
	{
		HttpUrl base = HttpUrl.parse(address);
		if (base == null)
		{
			throw new IllegalArgumentException("Not a valid API address: " + address);
		}
		return base.newBuilder().addPathSegments("api/v1/plugin/" + path).build();
	}

	/** The same batch always gets the same key, so a retry after a timeout is recognised by the server. */
	static String idempotencyKey(List<Record> records)
	{
		StringBuilder ids = new StringBuilder();
		for (Record record : records)
		{
			ids.append(record.getId()).append(',');
		}
		return sha256(ids.toString());
	}

	static String sha256(String text)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : digest)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private String errorFrom(String text)
	{
		try
		{
			JsonObject json = gson.fromJson(text, JsonObject.class);
			if (json != null && json.has("error"))
			{
				return json.get("error").getAsString();
			}
		}
		catch (JsonParseException | IllegalStateException ignored)
		{
		}
		return text.length() > 200 ? text.substring(0, 200) : text;
	}

	@Value
	static class AccountInfo
	{
		long hash;
		String rsn;
		String type;
	}
}
