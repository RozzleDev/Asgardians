package com.asgardians;

import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * One generic drop-recorder record. The server decides what each record counts for (drops page, clog page, events),
 * so the plugin records what happened and nothing more. Null fields are left out of the JSON.
 */
@Data
class Record
{
	enum Type
	{
		loot,
		pet,
		clog,
		pb,
		death,
		egg
	}

	/** Egg records: a handegg thrown by one player and caught by another. One of them is the player. */
	@Data
	static class Egg
	{
		private final String thrower;
		private final String target;
	}

	@Data
	static class Source
	{
		private final String name;
		private final Integer npcId;
		private Integer kc;
	}

	@Data
	static class Item
	{
		private final int id;
		private final String name;
		private final int qty;
		private final long gePrice;
	}

	private final String id = UUID.randomUUID().toString();
	private final Type type;
	/** ISO-8601 UTC time the event happened in game. */
	private final String at;
	private final int world;

	private Source source;
	private List<Item> items;
	/** How many players were in the instance, including the player. Only set for group content; never names. */
	private Integer partySize;

	/** Pet records: the pet's name from the clan broadcast, when one arrived. */
	private String petName;
	private Boolean duplicate;

	/** Clog records. */
	private String itemName;
	private Integer itemId;
	private Integer completed;

	/** PB records. */
	private PersonalBest pb;

	/** Death records. */
	private Death death;

	/** Egg records. */
	private Egg egg;

	/** True when a screenshot is uploaded with this record. */
	private boolean screenshot;

	/** Total GE value of the items, used for screenshot rules and the panel. Not sent. */
	transient long totalValue;
	/** Epoch millis when the record was created. Not sent. */
	transient long createdMillis;
	/** Still waiting for its screenshot; the uploader holds it back until it arrives or times out. Not sent. */
	transient volatile boolean awaitingScreenshot;

	String describe()
	{
		switch (type)
		{
			case pet:
				return "Pet" + (petName != null ? ": " + petName : "");
			case clog:
				return "Clog: " + itemName;
			case pb:
				return "PB: " + (pb != null ? pb.display() : "");
			case egg:
				return egg == null ? "Egg" : "Egg: " + egg.getThrower() + " egged " + egg.getTarget();
			case death:
				if (death == null || death.getKiller() == null || death.getKiller().getName() == null)
				{
					return "Death";
				}
				return "Death: " + death.getKiller().getName();
			default:
				if (items == null || items.isEmpty())
				{
					return "Loot";
				}
				Item best = items.get(0);
				for (Item item : items)
				{
					if ((long) item.getQty() * item.getGePrice() > (long) best.getQty() * best.getGePrice())
					{
						best = item;
					}
				}
				return best.getName() + (source != null ? " (" + source.getName() + ")" : "");
		}
	}
}
