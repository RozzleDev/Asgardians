package com.asgardians;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sorts bosses for the PBs page: a category (Raids, Bosses, Skilling, Minigames), and a group within it (God Wars,
 * Slayer, Wilderness and so on), so the boss drop-down stays short. The website's category and group come first, so
 * staff can move bosses and add groups without a plugin update; the plugin's own list is for records without them.
 */
final class BossGroups
{
	static final String RAIDS = "Raids";
	static final String BOSSES = "Bosses";
	static final String SKILLING = "Skilling";
	static final String MINIGAMES = "Minigames";
	static final List<String> CATEGORIES = List.of(RAIDS, BOSSES, SKILLING, MINIGAMES);

	static final String OTHER = "Other";
	/** The groups under Bosses, in drop-down order. */
	static final List<String> BOSS_GROUPS = List.of("God Wars", "Slayer", "Wilderness", "Desert Treasure II", "Varlamore", OTHER);

	/** Name (squashed: lowercase letters and digits) to {category, group}. Group is null outside Bosses. */
	private static final Map<String, String[]> KNOWN = new LinkedHashMap<>();

	static
	{
		for (String b : new String[]{"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"})
		{
			put(b, RAIDS, null);
		}
		for (String b : new String[]{"Tempoross", "Wintertodt", "Zalcano", "Guardians of the Rift", "Hespori"})
		{
			put(b, SKILLING, null);
		}
		for (String b : new String[]{"TzTok-Jad", "TzHaar Fight Cave", "TzKal-Zuk", "The Inferno", "Sol Heredit", "Fortis Colosseum",
			"The Gauntlet", "The Corrupted Gauntlet", "Corrupted Gauntlet", "Crystalline Hunllef", "Corrupted Hunllef", "Barrows",
			"Hallowed Sepulchre", "Lunar Chests", "Moons of Peril", "Mimic", "The Mimic"})
		{
			put(b, MINIGAMES, null);
		}
		for (String b : new String[]{"General Graardor", "K'ril Tsutsaroth", "Kree'arra", "Commander Zilyana", "Nex"})
		{
			put(b, BOSSES, "God Wars");
		}
		for (String b : new String[]{"Abyssal Sire", "Alchemical Hydra", "Cerberus", "Grotesque Guardians", "Kraken",
			"Thermonuclear Smoke Devil", "Araxxor", "Skotizo"})
		{
			put(b, BOSSES, "Slayer");
		}
		for (String b : new String[]{"Callisto", "Artio", "Vet'ion", "Calvar'ion", "Venenatis", "Spindel", "Chaos Elemental",
			"Chaos Fanatic", "Crazy Archaeologist", "Scorpia", "King Black Dragon"})
		{
			put(b, BOSSES, "Wilderness");
		}
		for (String b : new String[]{"Duke Sucellus", "The Leviathan", "Vardorvis", "The Whisperer"})
		{
			put(b, BOSSES, "Desert Treasure II");
		}
		for (String b : new String[]{"The Hueycoatl", "Amoxliatl", "Doom of Mokhaiotl", "Yama"})
		{
			put(b, BOSSES, "Varlamore");
		}
	}

	private BossGroups()
	{
	}

	private static void put(String name, String category, String group)
	{
		KNOWN.put(squash(name), new String[]{category, group});
	}

	static String squash(String name)
	{
		if (name == null)
		{
			return "";
		}
		String k = name.toLowerCase(Locale.UK).trim();
		k = k.startsWith("the ") ? k.substring(4) : k;
		return k.replaceAll("[^a-z0-9]", "");
	}

	/**
	 * {category, group} for a boss. Modes such as "Theatre of Blood: Hard Mode" go with their boss. The group is null
	 * outside Bosses.
	 */
	static String[] classify(String boss, String websiteCategory)
	{
		String key = squash(boss);
		String[] best = null;
		int bestLength = 0;
		for (Map.Entry<String, String[]> e : KNOWN.entrySet())
		{
			String k = e.getKey();
			if (!k.isEmpty() && key.startsWith(k) && k.length() > bestLength)
			{
				best = e.getValue();
				bestLength = k.length();
			}
		}
		if (best != null)
		{
			return best;
		}
		String category = fromWebsite(websiteCategory);
		return new String[]{category, category.equals(BOSSES) ? OTHER : null};
	}

	/**
	 * {category, group} for a record. Once the website sends {@code group} (an empty string means "no group"), its
	 * category and group are used as they are, so staff decide. Records without it are sorted by the plugin's own list,
	 * which only falls back on the website's category for bosses it doesn't know.
	 */
	static String[] place(String boss, String websiteCategory, String websiteGroup)
	{
		if (websiteGroup != null && websiteCategory != null && !websiteCategory.trim().isEmpty())
		{
			String group = websiteGroup.trim().isEmpty() ? null : websiteGroup.trim();
			return new String[]{categoryName(websiteCategory), group};
		}
		return classify(boss, websiteCategory);
	}

	/** "raids" becomes "Raids"; a name staff made up keeps its words, with a capital first letter. */
	static String categoryName(String category)
	{
		String c = category.trim();
		for (String known : CATEGORIES)
		{
			if (known.equalsIgnoreCase(c))
			{
				return known;
			}
		}
		return c.substring(0, 1).toUpperCase(Locale.UK) + c.substring(1);
	}

	/** Raids, Bosses, Skilling and Minigames first, in that order; other categories after, as the website sent them. */
	static int categoryOrder(String category)
	{
		int i = CATEGORIES.indexOf(category);
		return i >= 0 ? i : CATEGORIES.size();
	}

	/** The plugin's groups in their order, then any others as the website sent them, with Other always last. */
	static int groupOrder(String group)
	{
		if (OTHER.equals(group))
		{
			return Integer.MAX_VALUE;
		}
		int i = BOSS_GROUPS.indexOf(group);
		return i >= 0 ? i : BOSS_GROUPS.size();
	}

	/** The website's category word as one of ours; anything unknown counts as a boss. */
	private static String fromWebsite(String category)
	{
		if (category == null)
		{
			return BOSSES;
		}
		for (String c : CATEGORIES)
		{
			if (c.equalsIgnoreCase(category.trim()))
			{
				return c;
			}
		}
		return BOSSES;
	}

	/** The groups that have bosses, in drop-down order. */
	static List<String> groups(Iterable<String> present)
	{
		List<String> out = new ArrayList<>();
		for (String g : BOSS_GROUPS)
		{
			for (String p : present)
			{
				if (g.equals(p) && !out.contains(g))
				{
					out.add(g);
				}
			}
		}
		return out;
	}
}
