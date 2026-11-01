package com.asgardians;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Turns game events into generic records. It knows nothing about events or bingo: the server decides what each
 * record counts for. Called on the client thread only.
 */
class DropRecorder
{
	/** How close a kill count message must be to its loot to be attached to it. */
	static final long KC_WINDOW_MILLIS = 10_000;
	/** How long after a pet message the clan broadcast can still name it. */
	static final long PET_WINDOW_MILLIS = 10_000;
	/** How far back a new clog slot looks for the loot that caused it, to find its item ID. */
	static final long CLOG_LOOT_WINDOW_MILLIS = 60_000;
	private static final int RECENT = 20;

	/** What the recorder needs from the game's item data. */
	interface Items
	{
		String name(int itemId);

		/** GE price per item. */
		long price(int itemId);

		/** Item ID for an exact item name, or null when it can't be found. */
		Integer idForName(String name);
	}

	private final Items items;
	private final LinkedList<Record> recentLoot = new LinkedList<>();
	private Record lastPet;
	private String lastKcSource;
	private int lastKc;
	private long lastKcMillis;

	DropRecorder(Items items)
	{
		this.items = items;
	}

	/**
	 * @param stacks item ID to quantity, already merged by item
	 */
	Record loot(String sourceName, Integer npcId, Map<Integer, Integer> stacks, Integer partySize, int world, long now)
	{
		List<Record.Item> list = new ArrayList<>();
		long total = 0;
		for (Map.Entry<Integer, Integer> stack : stacks.entrySet())
		{
			int id = stack.getKey();
			int qty = stack.getValue();
			long price = items.price(id);
			list.add(new Record.Item(id, items.name(id), qty, price));
			total += price * qty;
		}
		if (list.isEmpty())
		{
			return null;
		}

		Record record = newRecord(Record.Type.loot, world, now);
		Record.Source source = new Record.Source(sourceName, npcId);
		if (sameSource(lastKcSource, sourceName) && now - lastKcMillis <= KC_WINDOW_MILLIS)
		{
			source.setKc(lastKc);
		}
		record.setSource(source);
		record.setItems(list);
		record.setPartySize(partySize);
		record.totalValue = total;

		recentLoot.addFirst(record);
		while (recentLoot.size() > RECENT)
		{
			recentLoot.removeLast();
		}
		return record;
	}

	/** Kill count messages can arrive just before or just after the loot, so fill in recent loot from the same source too. */
	void killCount(ChatParsers.KillCount kc, long now)
	{
		lastKcSource = kc.getSource();
		lastKc = kc.getCount();
		lastKcMillis = now;
		for (Record record : recentLoot)
		{
			if (now - record.createdMillis > KC_WINDOW_MILLIS)
			{
				break;
			}
			Record.Source source = record.getSource();
			if (source != null && source.getKc() == null && sameSource(source.getName(), kc.getSource()))
			{
				source.setKc(kc.getCount());
			}
		}
	}

	Record pet(boolean duplicate, int world, long now)
	{
		Record record = newRecord(Record.Type.pet, world, now);
		record.setDuplicate(duplicate);
		Record.Source source = lastKcSource != null && now - lastKcMillis <= KC_WINDOW_MILLIS
			? new Record.Source(lastKcSource, null) : null;
		if (source != null)
		{
			source.setKc(lastKc);
		}
		record.setSource(source);
		lastPet = record;
		return record;
	}

	/** Names the most recent pet from the clan broadcast, if it is ours and recent. */
	void clanPet(ChatParsers.ClanPet broadcast, String localRsn, long now)
	{
		if (lastPet != null && lastPet.getPetName() == null && now - lastPet.createdMillis <= PET_WINDOW_MILLIS
			&& sameRsn(broadcast.getRsn(), localRsn))
		{
			lastPet.setPetName(broadcast.getPetName());
		}
	}

	Record clog(String itemName, Integer completed, int world, long now)
	{
		Record record = newRecord(Record.Type.clog, world, now);
		record.setItemName(itemName);
		record.setItemId(findRecentItemId(itemName, now));
		if (record.getItemId() == null)
		{
			record.setItemId(items.idForName(itemName));
		}
		record.setCompleted(completed);
		return record;
	}

	private Integer findRecentItemId(String itemName, long now)
	{
		Iterator<Record> it = recentLoot.iterator();
		while (it.hasNext())
		{
			Record record = it.next();
			if (now - record.createdMillis > CLOG_LOOT_WINDOW_MILLIS)
			{
				break;
			}
			for (Record.Item item : record.getItems())
			{
				if (item.getName().equalsIgnoreCase(itemName))
				{
					return item.getId();
				}
			}
		}
		return null;
	}

	private static Record newRecord(Record.Type type, int world, long now)
	{
		Record record = new Record(type, Instant.ofEpochMilli(now).toString(), world);
		record.createdMillis = now;
		return record;
	}

	/** The Gauntlet bosses are recorded under the name of the activity. */
	static String npcLootSource(String npcName)
	{
		switch (npcName)
		{
			case "Crystalline Hunllef":
				return "The Gauntlet";
			case "Corrupted Hunllef":
				return "Corrupted Gauntlet";
			default:
				return npcName;
		}
	}

	/** Kill count messages drop a leading "The" ("Your Gauntlet completion count"), loot sources keep it. */
	static boolean sameSource(String a, String b)
	{
		return a != null && b != null && withoutThe(a).equalsIgnoreCase(withoutThe(b));
	}

	private static String withoutThe(String name)
	{
		return name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name;
	}

	/** RSNs in chat can use non-breaking spaces or underscores where the player name has spaces. */
	static boolean sameRsn(String a, String b)
	{
		return a != null && b != null && normalise(a).equalsIgnoreCase(normalise(b));
	}

	private static String normalise(String rsn)
	{
		return rsn.replace(' ', ' ').replace('_', ' ').replace('-', ' ').trim();
	}
}
