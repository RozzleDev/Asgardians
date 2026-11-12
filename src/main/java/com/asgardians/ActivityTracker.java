package com.asgardians;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Works out what the member is doing from their own client, for the Members page: "Theatre of Blood · Verzik Vitur",
 * "Vorkath", "Slayer · Abyssal demon", "Mining", "Banking · Varrock West", "Grand Exchange". The part before " · " is the
 * activity or place; after it, the boss, monster or bank. Only the member's own state is used, and it's only sent with
 * "Share my activity" on. The plugin calls {@link #describe} from the client thread.
 */
final class ActivityTracker
{
	static final String RAIDS = "Raids";
	static final String BOSSING = "Bossing";
	static final String SKILLING = "Skilling";
	static final String OTHER = "Other";

	static final String COX = "Chambers of Xeric";
	static final String TOB = "Theatre of Blood";
	static final String TOA = "Tombs of Amascut";
	static final String GE = "Grand Exchange";
	static final String BANK = "Banking";

	/** A fight or a skill counts for this long after the last hit or XP drop. */
	static final int RECENT_TICKS = 50;
	/** The Grand Exchange's region. */
	static final int GE_REGION = 12598;

	/** Boss names (lowercase) to what they're shown as; raid bosses show their raid. */
	private static final Map<String, String> BOSSES = new HashMap<>();
	/** Activities to an item whose picture stands for them. */
	private static final Map<String, Integer> ICONS = new HashMap<>();
	/** Map regions with a bank, to the bank's name. */
	private static final Map<Integer, String> BANKS = new HashMap<>();

	static
	{
		for (String b : new String[]{"great olm", "tekton", "vasa nistirio", "vespula", "muttadile", "vanguard", "skeletal mystic",
			"lizardman shaman", "ice demon", "guardian", "deathly ranger", "deathly mage"})
		{
			BOSSES.put(b, COX);
		}
		for (String b : new String[]{"the maiden of sugadinti", "pestilent bloat", "nylocas vasilias", "sotetseg", "xarpus",
			"verzik vitur", "nylocas hagios", "nylocas toxobolos", "nylocas ischyros"})
		{
			BOSSES.put(b, TOB);
		}
		for (String b : new String[]{"akkha", "ba-ba", "kephri", "zebak", "tumeken's warden", "elidinis' warden", "obelisk"})
		{
			BOSSES.put(b, TOA);
		}
		for (String b : new String[]{"vorkath", "zulrah", "nex", "general graardor", "k'ril tsutsaroth", "kree'arra",
			"commander zilyana", "corporeal beast", "cerberus", "alchemical hydra", "phantom muspah", "the leviathan",
			"duke sucellus", "vardorvis", "the whisperer", "sarachnis", "kalphite queen", "king black dragon", "giant mole",
			"abyssal sire", "kraken", "thermonuclear smoke devil", "callisto", "vet'ion", "venenatis", "artio", "calvar'ion",
			"spindel", "chaos elemental", "scorpia", "the nightmare", "phosani's nightmare", "zalcano", "araxxor",
			"the hueycoatl", "amoxliatl", "scurrius", "yama", "skotizo", "hespori", "tormented demon", "bryophyta", "obor",
			"mimic", "crazy archaeologist", "chaos fanatic", "deranged archaeologist", "the gauntlet"})
		{
			BOSSES.put(b, b.substring(0, 1).toUpperCase(Locale.UK) + b.substring(1));
		}
		BOSSES.put("dagannoth rex", "Dagannoth Kings");
		BOSSES.put("dagannoth prime", "Dagannoth Kings");
		BOSSES.put("dagannoth supreme", "Dagannoth Kings");
		BOSSES.put("dusk", "Grotesque Guardians");
		BOSSES.put("dawn", "Grotesque Guardians");
		BOSSES.put("branda the fire queen", "Royal Titans");
		BOSSES.put("eldric the ice king", "Royal Titans");
		BOSSES.put("sol heredit", "Fortis Colosseum");
		BOSSES.put("tztok-jad", "TzHaar Fight Cave");
		BOSSES.put("tzkal-zuk", "The Inferno");
		BOSSES.put("crystalline hunllef", "The Gauntlet");
		BOSSES.put("corrupted hunllef", "The Corrupted Gauntlet");
		// Proper names keep their capitals.
		BOSSES.put("k'ril tsutsaroth", "K'ril Tsutsaroth");
		BOSSES.put("kree'arra", "Kree'arra");
		BOSSES.put("commander zilyana", "Commander Zilyana");
		BOSSES.put("general graardor", "General Graardor");
		BOSSES.put("corporeal beast", "Corporeal Beast");
		BOSSES.put("alchemical hydra", "Alchemical Hydra");
		BOSSES.put("phantom muspah", "Phantom Muspah");
		BOSSES.put("the leviathan", "The Leviathan");
		BOSSES.put("duke sucellus", "Duke Sucellus");
		BOSSES.put("the whisperer", "The Whisperer");
		BOSSES.put("kalphite queen", "Kalphite Queen");
		BOSSES.put("king black dragon", "King Black Dragon");
		BOSSES.put("giant mole", "Giant Mole");
		BOSSES.put("abyssal sire", "Abyssal Sire");
		BOSSES.put("thermonuclear smoke devil", "Thermonuclear Smoke Devil");
		BOSSES.put("chaos elemental", "Chaos Elemental");
		BOSSES.put("the nightmare", "The Nightmare");
		BOSSES.put("phosani's nightmare", "Phosani's Nightmare");
		BOSSES.put("the hueycoatl", "The Hueycoatl");
		BOSSES.put("tormented demon", "Tormented Demons");
		BOSSES.put("crazy archaeologist", "Crazy Archaeologist");
		BOSSES.put("chaos fanatic", "Chaos Fanatic");
		BOSSES.put("deranged archaeologist", "Deranged Archaeologist");
		BOSSES.put("the gauntlet", "The Gauntlet");

		BANKS.put(12597, "Varrock West");
		BANKS.put(12853, "Varrock East");
		BANKS.put(12084, "Falador East");
		BANKS.put(11828, "Falador West");
		BANKS.put(12342, "Edgeville");
		BANKS.put(12338, "Draynor Village");
		BANKS.put(12850, "Lumbridge");
		BANKS.put(13105, "Al Kharid");
		BANKS.put(11061, "Catherby");
		BANKS.put(10806, "Seers' Village");
		BANKS.put(10547, "East Ardougne");
		BANKS.put(10292, "East Ardougne");
		BANKS.put(10288, "Yanille");
		BANKS.put(9776, "Castle Wars");
		BANKS.put(12344, "Ferox Enclave");
		BANKS.put(13878, "Canifis");
		BANKS.put(14646, "Port Phasmatys");
		BANKS.put(14642, "Ver Sinhaza");
		BANKS.put(4919, "Mount Quidamortem");
		BANKS.put(4922, "Farming Guild");
		BANKS.put(6198, "Woodcutting Guild");
		BANKS.put(13362, "Emir's Arena");
		BANKS.put(13613, "Nardah");
		BANKS.put(11571, "Crafting Guild");
		BANKS.put(10293, "Fishing Guild");
		BANKS.put(12109, "Rogues' Den");
		BANKS.put(14936, "Motherlode Mine");
		BANKS.put(9541, "Zanaris");
		BANKS.put(11310, "Shilo Village");
		BANKS.put(8253, "Lunar Isle");
		BANKS.put(14907, "Fossil Island");

		ICONS.put(COX, ItemID.OLMPET);
		ICONS.put(TOB, ItemID.VERZIKPET);
		ICONS.put(TOA, ItemID.WARDENPET_TUMEKEN);
		ICONS.put("Vorkath", ItemID.VORKATHPET);
		ICONS.put("Zulrah", ItemID.SNAKEPET);
		ICONS.put("Nex", ItemID.NEXPET);
		ICONS.put("General Graardor", ItemID.BANDOSPET);
		ICONS.put("Kree'arra", ItemID.ARMADYLPET);
		ICONS.put("Commander Zilyana", ItemID.SARADOMINPET);
		ICONS.put("K'ril Tsutsaroth", ItemID.ZAMORAKPET);
		ICONS.put("Corporeal Beast", ItemID.COREPET);
		ICONS.put("Cerberus", ItemID.HELL_PET);
		ICONS.put("Alchemical Hydra", ItemID.HYDRAPET);
		ICONS.put("Kalphite Queen", ItemID.KQPET_WALKING);
		ICONS.put("Giant Mole", ItemID.MOLEPET);
		ICONS.put("Kraken", ItemID.KRAKENPET);
		ICONS.put("Abyssal Sire", ItemID.ABYSSALSIRE_PET);
		ICONS.put("Grotesque Guardians", ItemID.DAWNPET);
		ICONS.put("The Nightmare", ItemID.NIGHTMAREPET);
		ICONS.put("Phosani's Nightmare", ItemID.NIGHTMAREPET);
		ICONS.put("The Whisperer", ItemID.WHISPERERPET);
		ICONS.put("The Hueycoatl", ItemID.HUEYPET);
		ICONS.put("Yama", ItemID.YAMAPET);
		ICONS.put("Skotizo", ItemID.SKOTIZOPET);
		ICONS.put("TzHaar Fight Cave", ItemID.TZHAAR_CAPE_FIRE);
		ICONS.put("The Inferno", ItemID.INFERNAL_CAPE);
		ICONS.put("Tempoross", ItemID.TEMPOROSSPET);
		ICONS.put(GE, ItemID.COINS);
		ICONS.put(BANK, ItemID.COINS);
		ICONS.put("Slayer", ItemID.SLAYER_HELM);
		ICONS.put("Mining", ItemID.RUNE_PICKAXE);
		ICONS.put("Woodcutting", ItemID.RUNE_AXE);
		ICONS.put("Fishing", ItemID.LOBSTER_POT);
		ICONS.put("Smithing", ItemID.HAMMER);
		ICONS.put("Fletching", ItemID.KNIFE);
		ICONS.put("Firemaking", ItemID.TINDERBOX);
		ICONS.put("Crafting", ItemID.CHISEL);
		ICONS.put("Herblore", ItemID.PESTLE_AND_MORTAR);
		ICONS.put("Runecraft", ItemID.BLANKRUNE_HIGH);
		ICONS.put("Farming", ItemID.RAKE);
		ICONS.put("Prayer", ItemID.BONES);
		ICONS.put("Agility", ItemID.GRACEFUL_HOOD);
	}

	private ActivityTracker()
	{
	}

	/** What the member's client sees right now. Ticks are game ticks; -1 means never. */
	@lombok.Value
	static class Snapshot
	{
		int tick;
		boolean inCox;
		boolean inTob;
		/** The NPC last fought, and on which tick. */
		String target;
		int targetTick;
		/** The member's Slayer task count, and when Slayer XP last came in: only fights giving Slayer XP are Slayer. */
		int slayerCount;
		int slayerXpTick;
		/** The skill that last gave XP outside combat, and on which tick. */
		String skill;
		int skillTick;
		boolean bankOpen;
		int region;
	}

	/** What the member is doing, or null when nothing in particular (shown as just "Online"). */
	static String describe(Snapshot s)
	{
		boolean fighting = s.getTarget() != null && s.getTargetTick() >= 0 && s.getTick() - s.getTargetTick() <= RECENT_TICKS;
		String boss = fighting ? boss(s.getTarget()) : null;
		if (s.isInCox())
		{
			return COX + (COX.equals(boss) ? " · " + s.getTarget() : "");
		}
		if (s.isInTob())
		{
			return TOB + (TOB.equals(boss) ? " · " + s.getTarget() : "");
		}
		if (boss != null)
		{
			// A raid's bosses show the raid first, then the boss.
			return boss.equals(COX) || boss.equals(TOB) || boss.equals(TOA) ? boss + " · " + s.getTarget() : boss;
		}
		boolean slayerXp = s.getSlayerXpTick() >= 0 && s.getTick() - s.getSlayerXpTick() <= RECENT_TICKS;
		if (fighting && slayerXp && s.getSlayerCount() > 0)
		{
			return "Slayer · " + s.getTarget();
		}
		boolean skilling = s.getSkill() != null && s.getSkillTick() >= 0 && s.getTick() - s.getSkillTick() <= RECENT_TICKS;
		if (skilling)
		{
			return s.getSkill();
		}
		if (s.isBankOpen())
		{
			String bank = BANKS.get(s.getRegion());
			return s.getRegion() == GE_REGION ? GE : bank != null ? BANK + " · " + bank : BANK;
		}
		if (s.getRegion() == GE_REGION)
		{
			return GE;
		}
		if (fighting)
		{
			return "Combat · " + s.getTarget();
		}
		return null;
	}

	/** The bank in a map region, or null when it isn't one the plugin knows. */
	static String bank(int region)
	{
		return BANKS.get(region);
	}

	/** The boss an NPC name belongs to, or null when it isn't one. */
	static String boss(String npc)
	{
		return npc == null ? null : BOSSES.get(npc.toLowerCase(Locale.UK).trim());
	}

	/** Raids, Bossing, Skilling or Other, for the Members page's filter. */
	static String category(String activity)
	{
		if (activity == null)
		{
			return OTHER;
		}
		if (activity.startsWith(COX) || activity.startsWith(TOB) || activity.startsWith(TOA))
		{
			return RAIDS;
		}
		if (activity.startsWith("Slayer") || activity.startsWith("Combat") || BOSSES.containsValue(activity)
			|| activity.equals("Fortis Colosseum") || activity.equals("The Inferno") || activity.equals("TzHaar Fight Cave"))
		{
			return BOSSING;
		}
		for (net.runelite.api.Skill skill : net.runelite.api.Skill.values())
		{
			if (activity.startsWith(skill.getName()))
			{
				return SKILLING;
			}
		}
		return activity.equals("Tempoross") || activity.equals("Wintertodt") ? SKILLING : OTHER;
	}

	/** An item whose picture stands for the activity, or null. */
	static Integer icon(String activity)
	{
		if (activity == null)
		{
			return null;
		}
		Integer id = ICONS.get(activity);
		if (id == null)
		{
			int dot = activity.indexOf(" · ");
			id = dot > 0 ? ICONS.get(activity.substring(0, dot)) : null;
		}
		return id;
	}
}
