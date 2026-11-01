package com.asgardians;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides what to do when RuneLite's stored PBs change. Pure logic so it can be tested; the plugin feeds it events.
 *
 * <p>RuneLite's Chat Commands plugin parses every PB message and the Adventure Log counters page and writes the result
 * to its "personalbest" store. Mirroring that store means the plugin tracks exactly what RuneLite shows, including
 * team sizes and precise times, without parsing the messages a second time.
 */
class PbTracker
{
	/** A store change this close to a "(new personal best)" message is a live PB. */
	static final long NEW_PB_WINDOW_MILLIS = 10_000;
	/** Other changes (Adventure Log counters, corrections) are gathered and sent as one full sync this long after the last. */
	static final long SYNC_DEBOUNCE_MILLIS = 5_000;

	interface Store
	{
		/** Every key in the current account's "personalbest" store. */
		List<String> keys();

		String value(String key);

		/** Kill count RuneLite stores for a boss, or null. */
		Integer killCount(String boss);
	}

	private final Store store;
	private long lastNewPbMessage = -NEW_PB_WINDOW_MILLIS - 1;
	private long syncDueAt = -1;

	PbTracker(Store store)
	{
		this.store = store;
	}

	/** Call for every game message; remembers when the last "(new personal best)" was seen. */
	void chatMessage(String message, long now)
	{
		if (message.contains("(new personal best)"))
		{
			lastNewPbMessage = now;
		}
	}

	/**
	 * Call when RuneLite's "personalbest" store changes for the logged-in account.
	 *
	 * @return the PB to send right away as a record, or null when it will go in the next full sync instead
	 */
	PersonalBest changed(String key, String newValue, long now)
	{
		PersonalBest pb = PersonalBest.parse(key, newValue);
		if (pb == null)
		{
			return null;
		}
		if (now - lastNewPbMessage <= NEW_PB_WINDOW_MILLIS)
		{
			pb.setNewPb(true);
			pb.setKc(store.killCount(pb.getBoss()));
			return pb;
		}
		requestSync(now);
		return null;
	}

	/** Schedules a full sync after the debounce, pushing it back if one is already waiting. */
	void requestSync(long now)
	{
		syncDueAt = now + SYNC_DEBOUNCE_MILLIS;
	}

	/** Schedules a full sync straight away (login, button). */
	void requestSyncNow(long now)
	{
		syncDueAt = now;
	}

	boolean syncDue(long now)
	{
		return syncDueAt >= 0 && now >= syncDueAt;
	}

	void syncStarted()
	{
		syncDueAt = -1;
	}

	/** Every PB in the store, with kill counts. */
	List<PersonalBest> snapshot()
	{
		List<PersonalBest> all = new ArrayList<>();
		for (String key : store.keys())
		{
			PersonalBest pb = PersonalBest.parse(key, store.value(key));
			if (pb != null)
			{
				pb.setKc(store.killCount(pb.getBoss()));
				all.add(pb);
			}
		}
		return all;
	}
}
