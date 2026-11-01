package com.asgardians;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Gathers the whole collection log. When the collection log opens, the plugin triggers its Search, the game sends every
 * obtained item and runs script 4100 once per item with its ID and quantity (the approach WikiSync and RuneProfile use).
 * The capture is finished once the script has been quiet for a couple of ticks. Client thread only.
 */
class ClogCapture
{
	/** The game script run once per obtained collection log item. */
	static final int ITEM_SCRIPT = 4100;
	/** Runs when the collection log opens; that's when the plugin asks the game for every item. */
	static final int SETUP_SCRIPT = 7797;
	/** Resets the log view after the search, so the member sees their log as normal. */
	static final int INIT_SCRIPT = 2240;
	/** At most one automatic fetch this often. */
	static final long AUTO_FETCH_COOLDOWN_MILLIS = 60_000;
	/** Ticks without another item before the capture counts as complete. */
	static final int QUIET_TICKS = 2;

	@Value
	static class Item
	{
		int id;
		String name;
		int qty;
	}

	private final Map<Integer, Integer> items = new LinkedHashMap<>();
	private int lastItemTick = -1;

	void item(int itemId, int quantity, int tick)
	{
		if (itemId > 0 && quantity > 0)
		{
			items.merge(itemId, quantity, Math::max);
		}
		lastItemTick = tick;
	}

	/** Drops a capture in progress, e.g. when someone else's log is opened from their adventure log. */
	void reset()
	{
		items.clear();
		lastItemTick = -1;
	}

	/**
	 * @return the captured item IDs and quantities once the capture has gone quiet, else null. Clears the capture.
	 */
	Map<Integer, Integer> finishIfQuiet(int tick)
	{
		if (lastItemTick < 0 || tick <= lastItemTick + QUIET_TICKS)
		{
			return null;
		}
		Map<Integer, Integer> done = new LinkedHashMap<>(items);
		reset();
		return done.isEmpty() ? null : done;
	}

	static List<Item> named(Map<Integer, Integer> captured, java.util.function.IntFunction<String> names)
	{
		List<Item> list = new ArrayList<>();
		captured.forEach((id, qty) -> list.add(new Item(id, names.apply(id), qty)));
		return list;
	}
}
