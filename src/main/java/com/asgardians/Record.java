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
		egg,
		/** A kill count message, for total KC and KC bingo tasks. */
		kc,
		/** The member's XP in each skill, for XP bingo tasks and leaderboards. */
		xp,
		/** A finished quest speedrun, for medal bingo tasks. */
		speedrun
	}

	/** Kc records: the kill's own time, when the game showed one (a PB or not). */
	@Data
	static class KillTime
	{
		private final double seconds;
		/** Game ticks, when precise timing is on. */
		private final Integer ticks;
		private final boolean precise;
		/** "1" for solo, "2"…"8", "11-15", "24+"; null when unknown. */
		private final String teamSize;
	}

	/** Speedrun records: the quest, the run's time and the member's best, in seconds. */
	@Data
	static class Speedrun
	{
		private final String quest;
		private final Double seconds;
		private final Double bestSeconds;
		/** The game said this run was a new personal best. */
		private final boolean personalBest;
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

	/** XP records: XP by skill name ("Attack", ...), and the total. */
	private java.util.Map<String, Integer> xp;
	private Long totalXp;

	/** Speedrun records. */
	private Speedrun speedrun;

	/** Kc records: the kill's time, when the game showed one. */
	private KillTime time;

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
			case kc:
				return "KC: " + (source != null ? source.getName() + " " + source.getKc() : "");
			case xp:
				return "XP: " + (totalXp != null ? String.format("%,d", totalXp) + " total" : "");
			case speedrun:
				return "Speedrun: " + (speedrun != null ? speedrun.getQuest() : "");
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
