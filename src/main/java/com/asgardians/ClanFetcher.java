package com.asgardians;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.Value;

/**
 * Loads the Events and News tabs: the event list, then for each relevant event its team, standings and board, plus
 * announcements. The follow-up calls run in parallel; the callback gets one finished view.
 */
class ClanFetcher
{
	@Value
	static class Result
	{
		ClanView view;
		/** Highest HTTP status seen among failures (401 means the install key is no longer valid), or 0. */
		int failureStatus;
	}

	private final ApiClient api;

	ClanFetcher(ApiClient api)
	{
		this.api = api;
	}

	/**
	 * @param withBoards also fetch boards (polled more often than the rest)
	 * @param previous   the last view, whose boards are kept when this fetch skips them
	 */
	void fetch(String address, String key, boolean withBoards, ClanView previous, Consumer<Result> done)
	{
		api.get(ApiClient.endpoint(address, "events"), key, ClanData.EventList.class, events ->
		{
			if (!events.ok())
			{
				ClanView keep = previous != null ? previous : ClanView.empty();
				String error = events.getStatus() == 0
					? "Can't reach the clan website"
					: "Events unavailable (" + events.getStatus() + ")";
				done.accept(new Result(new ClanView(keep.getEvents(), keep.getAnnouncements(), keep.getFetchedAt(), error),
					events.getStatus()));
				return;
			}

			List<ClanData.Event> list = new ArrayList<>();
			for (ClanData.Event event : ClanView.orEmpty(events.getBody().getEvents()))
			{
				if (event.getId() != null && (event.isLive() || event.isUpcoming()))
				{
					list.add(event);
				}
			}

			Map<String, ClanData.Team> teams = new ConcurrentHashMap<>();
			Map<String, List<ClanData.Team>> standings = new ConcurrentHashMap<>();
			Map<String, ClanData.Board> boards = new ConcurrentHashMap<>();
			Map<String, Map<String, ClanData.Board>> teamBoards = new ConcurrentHashMap<>();
			Map<String, List<ClanData.Drop>> drops = new ConcurrentHashMap<>();
			Map<String, List<ClanData.Announcement>> news = new ConcurrentHashMap<>();
			AtomicInteger failure = new AtomicInteger();

			// Each call takes the "I'm done" callback to run once its response is stored.
			List<Consumer<Runnable>> calls = new ArrayList<>();
			for (ClanData.Event event : list)
			{
				String id = event.getId();
				if (event.getTeamId() != null)
				{
					calls.add(then -> api.get(ApiClient.endpoint(address, "events/" + id + "/team"), key,
						ClanData.Team.class, r -> store(r, failure, body -> teams.put(id, body), then)));
				}
				if (event.isLive())
				{
					calls.add(then -> api.get(ApiClient.endpoint(address, "events/" + id + "/teams"), key,
						ClanData.TeamList.class, r -> store(r, failure, body -> standings.put(id, ClanView.orEmpty(body.getTeams())), then)));
				}
				if (withBoards && event.isLive() && event.isHasBoard())
				{
					// Every team's board. A website without it may answer 404, or 200 with its web page instead of
					// JSON, so anything but a usable list falls back to the member's own board.
					calls.add(then -> api.get(ApiClient.endpoint(address, "events/" + id + "/boards"), key,
						ClanData.BoardList.class, r ->
						{
							boolean usable = r.ok() && r.getBody().getBoards() != null;
							if (!usable && event.getTeamId() != null)
							{
								api.get(ApiClient.endpoint(address, "events/" + id + "/board"), key,
									ClanData.Board.class, own -> store(own, failure, body -> boards.put(id, body), then));
								return;
							}
							store(r, failure, body -> teamBoards.put(id, byTeam(body)), then);
						}));
					// Recent drops. Optional: a website without them (404, or its web page) isn't a failure.
					calls.add(then -> api.get(ApiClient.endpoint(address, "events/" + id + "/drops"), key,
						ClanData.DropList.class, r ->
						{
							try
							{
								if (r.ok() && r.getBody().getDrops() != null)
								{
									drops.put(id, r.getBody().getDrops());
								}
							}
							finally
							{
								then.run();
							}
						}));
				}
			}
			calls.add(then -> api.get(ApiClient.endpoint(address, "announcements"), key,
				ClanData.AnnouncementList.class, r -> store(r, failure, body -> news.put("news", ClanView.orEmpty(body.getAnnouncements())), then)));

			Runnable finish = () ->
			{
				List<ClanView.EventView> views = new ArrayList<>();
				for (ClanData.Event event : list)
				{
					String id = event.getId();
					ClanData.Board board = boards.get(id);
					Map<String, ClanData.Board> all = teamBoards.getOrDefault(id, Map.of());
					List<ClanData.Drop> recent = drops.get(id);
					if (board == null && all.isEmpty() && !withBoards && previous != null)
					{
						ClanView.EventView before = previousView(previous, id);
						if (before != null)
						{
							board = before.getBoard();
							all = before.getTeamBoards();
							recent = before.getDrops();
						}
					}
					if (board == null && event.getTeamId() != null)
					{
						board = all.get(event.getTeamId());
					}
					views.add(new ClanView.EventView(event, teams.get(id), standings.getOrDefault(id, List.of()), board, all, recent));
				}
				List<ClanData.Announcement> items = news.containsKey("news")
					? news.get("news")
					: (previous != null ? previous.getAnnouncements() : List.of());
				String error = failure.get() != 0 ? "Some clan data couldn't load (" + failure.get() + ")" : null;
				done.accept(new Result(new ClanView(views, items, Instant.now(), error), failure.get()));
			};

			AtomicInteger remaining = new AtomicInteger(calls.size());
			Runnable oneDone = () ->
			{
				if (remaining.decrementAndGet() == 0)
				{
					finish.run();
				}
			};
			for (Consumer<Runnable> call : calls)
			{
				call.accept(oneDone);
			}
		});
	}

	private static <T> void store(ApiClient.GetResult<T> result, AtomicInteger failure, Consumer<T> keep, Runnable then)
	{
		try
		{
			if (result.ok())
			{
				keep.accept(result.getBody());
			}
			else if (result.getStatus() != 404)
			{
				// A 404 just means there's nothing of this kind for the event (no teams yet, say), so it isn't an error.
				failure.accumulateAndGet(Math.max(result.getStatus(), 1), Math::max);
			}
		}
		finally
		{
			then.run();
		}
	}

	private static ClanView.EventView previousView(ClanView previous, String eventId)
	{
		for (ClanView.EventView view : previous.getEvents())
		{
			if (view.getEvent().getId().equals(eventId))
			{
				return view;
			}
		}
		return null;
	}

	private static Map<String, ClanData.Board> byTeam(ClanData.BoardList list)
	{
		Map<String, ClanData.Board> out = new java.util.HashMap<>();
		for (ClanData.TeamBoard board : ClanView.orEmpty(list.getBoards()))
		{
			if (board.getTeamId() != null)
			{
				out.put(board.getTeamId(), board);
			}
		}
		return out;
	}
}
