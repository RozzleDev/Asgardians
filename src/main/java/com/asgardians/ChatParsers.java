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
