package com.asgardians;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/** What a death record carries: who, where, and what it cost. */
@Data
class Death
{
	@Data
	static class Killer
	{
		/** "npc", "player" or "unknown". Player killers are never named. */
		private final String type;
		private final String name;
		private final Integer npcId;
		private final Integer combatLevel;

		static Killer unknown()
		{
			return new Killer("unknown", null, null, null);
		}

		static Killer player()
		{
			return new Killer("player", null, null, null);
		}
	}

	/** A known activity or area, keyed by map region. */
	@Value
	static class Place
	{
		String name;
		/** Items are kept on death here. */
		boolean safe;
	}

	/** Region IDs of activities where deaths are safe, from Dink's WorldUtils (BSD-2-Clause, pajlads). */
	static final Map<Integer, Place> PLACES = places();

	private Killer killer;
	private int regionId;
	private int plane;
	private boolean instanced;
	/** Name of the activity, when the region is a known one. */
	private String area;
	/** Items are kept on a death here (minigames, raids and similar). */
	private boolean safe;
	private boolean wilderness;
	/** Estimated GE value of what was lost: 0 for safe deaths. */
	private long valueLost;
	/** How many items the player keeps on a dangerous death (3, 0 when skulled, +1 with Protect Item, 0 for UIM). */
	private int keepCount;
	private List<Record.Item> lostItems;

	/** One stack the player had at death. */
	@Value
	static class Held
	{
		int id;
		String name;
		int qty;
		long price;
	}

	@Value
	static class Split
	{
		List<Held> kept;
		List<Held> lost;

		long lostValue()
		{
			long total = 0;
			for (Held held : lost)
			{
				total += held.getPrice() * held.getQty();
			}
			return total;
		}
	}

	/** Bonds are always kept and don't use up a kept slot. */
	private static final Set<Integer> ALWAYS_KEPT = Set.of(ItemID.OSRS_BOND, ItemID.BOUGHT_OSRS_BOND, ItemID.OSRS_BOND_UNTRADEABLE);

	/**
	 * Splits what the player held into kept and lost the way the game does for an ordinary dangerous death: the most
	 * valuable single items are kept, one at a time, up to keepCount.
	 */
	static Split split(List<Held> held, int keepCount)
	{
		List<Held> sorted = new ArrayList<>(held);
		sorted.sort(Comparator.comparingLong(Held::getPrice).reversed());
		List<Held> kept = new ArrayList<>();
		List<Held> lost = new ArrayList<>();
		int keptSoFar = 0;
		for (Held stack : sorted)
		{
			if (ALWAYS_KEPT.contains(stack.getId()))
			{
				kept.add(stack);
				continue;
			}
			int keepHere = Math.min(stack.getQty(), keepCount - keptSoFar);
			if (keepHere > 0)
			{
				kept.add(new Held(stack.getId(), stack.getName(), keepHere, stack.getPrice()));
				keptSoFar += keepHere;
			}
			if (stack.getQty() > keepHere)
			{
				lost.add(new Held(stack.getId(), stack.getName(), stack.getQty() - keepHere, stack.getPrice()));
			}
		}
		return new Split(kept, lost);
	}

	private static Map<Integer, Place> places()
	{
		Map<Integer, Place> map = new java.util.HashMap<>();
		add(map, "Chambers of Xeric", 12889, 13136, 13137, 13138, 13139, 13140, 13141, 13145, 13393, 13394, 13395, 13396, 13397, 13401);
		add(map, "Theatre of Blood", 12611, 12612, 12613, 12867, 12869, 13122, 13123, 13125, 13379);
		add(map, "Tombs of Amascut", 14160, 14162, 14164, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700);
		add(map, "The Gauntlet", 7512, 7768, 12127);
		add(map, "The Inferno", 9043);
		add(map, "Fight Caves", 9551);
		add(map, "Fight Pits", 9552);
		add(map, "Fortis Colosseum", 7216);
		add(map, "Nightmare Zone", 9033);
		add(map, "Barbarian Assault", 7508, 7509, 10322);
		add(map, "Castle Wars", 9520, 9620);
		add(map, "Soul Wars", 8493, 8748, 8749, 9005);
		add(map, "Last Man Standing", 13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);
		add(map, "Player-owned house", 7257, 7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);
		add(map, "Clan Wars", 12621, 12622, 12623, 13130, 13131, 13133, 13134, 13135, 13386, 13387, 13390, 13641, 13642, 13643, 13644, 13645, 13646, 13647, 13899, 13900, 14155, 14156);
		add(map, "Creature Graveyard", 13462);
		add(map, "Clan hall", 6997);
		return java.util.Collections.unmodifiableMap(map);
	}

	private static void add(Map<Integer, Place> map, String name, int... regions)
	{
		for (int region : regions)
		{
			map.put(region, new Place(name, true));
		}
	}
}
