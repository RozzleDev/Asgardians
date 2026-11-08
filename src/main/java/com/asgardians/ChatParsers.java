package com.asgardians;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/** Parses the game and clan messages the drop recorder listens for. Input is chat text with tags already removed. */
final class ChatParsers
{
	/** "Your Vorkath kill count is: 512." / "Your completed Chambers of Xeric count is: 50." */
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completed |subdued )?(.+?) (?:kill |success |completion )?count is: ([\\d,]+)\\.?$");
	/** "You pick the guard's pocket." The same pattern RuneLite's Loot Tracker uses. */
	private static final Pattern PICKPOCKET = Pattern.compile("^You pick (?:the )?.+'s? pocket.*");
	private static final Pattern NEW_CLOG =Pattern.compile("^New item added to your collection log: (.+?)\\.?$");
	/** "Rsn has a funny feeling like he's being followed: Vorki at 1,234 killcount." */
	private static final Pattern CLAN_PET = Pattern.compile(
		"^(.+?) (?:has a funny feeling like .+? (?:being followed|have been followed)|feels something weird sneaking into .+? backpack|feels like .+? acquired something special): (.+?)(?: at .*)?\\.?$");

	/** "12:41.40", "1:02:03" or "0:58". */
	private static final Pattern RUN_TIME = Pattern.compile("(?:(\\d+):)?(\\d{1,2}):(\\d{2})(?:\\.(\\d+))?");
	/** The speedrun window's title: "You have completed Dragon Slayer I!" and similar. */
	private static final Pattern QUEST_DONE = Pattern.compile("^You have (?:completely |kind of )?completed (?:the )?(.+?)!?$");

	/**
	 * A kill or completion time: "Fight duration: 1:01.80", "Challenge duration: …", "Subdued in …", "… total
	 * completion time: …", "Duration: …" in a raid's completion message. The first time after the words is the kill's
	 * own; a "Personal best" after it is ignored.
	 */
	private static final Pattern KILL_TIME = Pattern.compile(
		"(?i)(fight duration|lap duration|corrupted challenge duration|challenge duration|total completion time|completion time|subdued in|duration)"
			+ "\\s*:?\\s*(\\d+(?::\\d{2}){1,2}(?:\\.\\d+)?)");
	/** "Team size: 4 players", "Team size: Solo", "Team size: 11-15 players", "Team size: 24+ players". */
	private static final Pattern TEAM_SIZE = Pattern.compile("(?i)team size:\\s*(solo|\\d+(?:-\\d+)?\\+?)(?:\\s*players?)?");

	/** A kill's time from a game message. */
	@Value
	static class KillTime
	{
		double seconds;
		/** "1", "4", "11-15", "24+"; null unless the message says. */
		String teamSize;
		/** A whole raid's time ("total completion time", a raid's "Duration"), which beats a room's time nearby. */
		boolean total;
		/** A TzHaar-Ket-Rak challenge (or Gauntlet) time, never a full Fight Cave. */
		boolean challenge;
	}

	/** The kill's own time in a game message (tags already removed), or null. */
	static KillTime killTime(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher m = KILL_TIME.matcher(message);
		if (!m.find())
		{
			return null;
		}
		Double seconds = runTime(m.group(2));
		if (seconds == null || seconds <= 0)
		{
			return null;
		}
		String words = m.group(1).toLowerCase(java.util.Locale.UK);
		String lower = message.toLowerCase(java.util.Locale.UK);
		Matcher team = TEAM_SIZE.matcher(message);
		String size = null;
		if (team.find())
		{
			size = team.group(1).equalsIgnoreCase("solo") ? "1" : team.group(1);
		}
		boolean raid = lower.contains("raid is complete") || words.startsWith("total");
		return new KillTime(seconds, size, raid, words.contains("challenge"));
	}

	/** A time in seconds from the speedrun window, or null. */
	static Double runTime(String text)
	{
		if (text == null)
		{
			return null;
		}
		Matcher m = RUN_TIME.matcher(text);
		if (!m.find())
		{
			return null;
		}
		double seconds = (m.group(1) != null ? Integer.parseInt(m.group(1)) * 3600 : 0) + Integer.parseInt(m.group(2)) * 60
			+ Integer.parseInt(m.group(3));
		if (m.group(4) != null)
		{
			seconds += Double.parseDouble("0." + m.group(4));
		}
		return seconds;
	}

	/** The quest's name from the speedrun window's title, or the title as it is. */
	static String speedrunQuest(String title)
	{
		if (title == null)
		{
			return null;
		}
		String clean = title.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
		Matcher m = QUEST_DONE.matcher(clean);
		return m.matches() ? m.group(1).trim() : clean;
	}

	@Value
	static class KillCount
	{
		String source;
		int count;
	}

	@Value
	static class PetMessage
	{
		boolean duplicate;
	}

	@Value
	static class ClanPet
	{
		String rsn;
		String petName;
	}

	private ChatParsers()
	{
	}

	static boolean isPickpocket(String message)
	{
		return PICKPOCKET.matcher(message).matches();
	}

	static KillCount killCount(String message)
	{
		Matcher m = KILL_COUNT.matcher(message);
		if (!m.matches())
		{
			return null;
		}
		try
		{
			return new KillCount(m.group(1), Integer.parseInt(m.group(2).replace(",", "")));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	static String newCollectionLogItem(String message)
	{
		Matcher m = NEW_CLOG.matcher(message);
		return m.matches() ? m.group(1) : null;
	}

	/** The player's own pet message. */
	static PetMessage pet(String message)
	{
		if (message.startsWith("You have a funny feeling like you would have been followed"))
		{
			return new PetMessage(true);
		}
		if (message.startsWith("You have a funny feeling like you're being followed")
			|| message.startsWith("You feel something weird sneaking into your backpack"))
		{
			return new PetMessage(false);
		}
		return null;
	}

	/** The clan broadcast that follows a pet, which names the pet. */
	static ClanPet clanPet(String message)
	{
		Matcher m = CLAN_PET.matcher(message);
		return m.matches() ? new ClanPet(m.group(1), m.group(2)) : null;
	}
}
