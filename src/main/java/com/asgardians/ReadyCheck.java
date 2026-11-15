package com.asgardians;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * A ready check in the member's RuneLite party: someone starts it, each member answers ready or not, and it ends after
 * {@link #SECONDS} seconds or once everyone has answered. Only the members' own answers are sent, through RuneLite's
 * party server (never the clan website). Not thread safe: the plugin calls it from one thread.
 */
final class ReadyCheck
{
	static final int SECONDS = 30;
	/** How long the result stays on screen once the check is over. */
	static final int SHOW_RESULT_SECONDS = 10;

	enum Answer
	{
		WAITING, READY, NOT_READY
	}

	/** One party member's line. */
	@Value
	static class Line
	{
		String name;
		Answer answer;
	}

	/** What the panel and overlay show. */
	@Value
	static class View
	{
		String startedBy;
		/** Seconds left; 0 once it's over. */
		int secondsLeft;
		boolean over;
		List<Line> lines;
		int ready;
		/** My own answer. */
		Answer mine;

		/** "3/4 ready" or, once over, "All 4 ready!" / "3/4 ready. Not ready: Thor". */
		String summary()
		{
			int total = lines.size();
			if (!over)
			{
				return ready + "/" + total + " ready";
			}
			if (ready == total)
			{
				return "All " + total + " ready!";
			}
			List<String> notReady = new ArrayList<>();
			List<String> silent = new ArrayList<>();
			for (Line l : lines)
			{
				(l.getAnswer() == Answer.NOT_READY ? notReady : l.getAnswer() == Answer.WAITING ? silent : new ArrayList<String>())
					.add(l.getName());
			}
			StringBuilder out = new StringBuilder(ready + "/" + total + " ready.");
			if (!notReady.isEmpty())
			{
				out.append(" Not ready: ").append(String.join(", ", notReady)).append('.');
			}
			if (!silent.isEmpty())
			{
				out.append(" No answer: ").append(String.join(", ", silent)).append('.');
			}
			return out.toString();
		}
	}

	private long started;
	private long startedAtMs;
	private String startedBy;
	private final Map<Long, Answer> answers = new LinkedHashMap<>();

	/**
	 * Starts a check, keyed by its start time so the same message arriving twice (the party server echoes it back to
	 * the sender) doesn't restart it. Returns true when it's a new check.
	 */
	boolean start(long key, String by, long nowMs)
	{
		if (key == started)
		{
			return false;
		}
		started = key;
		startedAtMs = nowMs;
		startedBy = by;
		answers.clear();
		return true;
	}

	/** Records a member's answer to the check started at {@code key}; answers to an older check are ignored. */
	boolean answer(long key, long memberId, boolean ready, long nowMs)
	{
		if (key != started || started == 0)
		{
			return false;
		}
		answers.put(memberId, ready ? Answer.READY : Answer.NOT_READY);
		// Notes when the latest answer came in, so a check everyone answered fades out 10 seconds later.
		lastAnswerSeconds = (nowMs - startedAtMs) / 1000;
		return true;
	}

	long key()
	{
		return started;
	}

	/** True while a check is running (not yet timed out, and not everyone has answered). */
	boolean running(Map<Long, String> members, long nowMs)
	{
		View v = view(members, -1, nowMs);
		return v != null && !v.isOver();
	}

	/**
	 * The check as it stands, for the party's current members (ID to name, in order), or null when there's none or
	 * its result has been shown long enough.
	 */
	View view(Map<Long, String> members, long myId, long nowMs)
	{
		if (started == 0)
		{
			return null;
		}
		long elapsed = (nowMs - startedAtMs) / 1000;
		List<Line> lines = new ArrayList<>();
		int ready = 0;
		for (Map.Entry<Long, String> m : members.entrySet())
		{
			Answer a = answers.getOrDefault(m.getKey(), Answer.WAITING);
			ready += a == Answer.READY ? 1 : 0;
			lines.add(new Line(m.getValue(), a));
		}
		boolean everyone = !members.isEmpty() && members.keySet().stream().allMatch(answers::containsKey);
		boolean over = elapsed >= SECONDS || everyone;
		if (over)
		{
			// Keep the result up for a while after it ended (by time, or the moment the last member answered).
			long endedAt = everyone ? Math.min(elapsed, lastAnswerSeconds) : SECONDS;
			if (elapsed - endedAt >= SHOW_RESULT_SECONDS)
			{
				return null;
			}
		}
		return new View(startedBy, over ? 0 : (int) (SECONDS - elapsed), over, lines, ready,
			answers.getOrDefault(myId, Answer.WAITING));
	}

	private long lastAnswerSeconds;

	void clear()
	{
		started = 0;
		answers.clear();
	}
}
