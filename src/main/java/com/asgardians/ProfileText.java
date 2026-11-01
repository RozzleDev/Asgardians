package com.asgardians;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.util.QuantityFormatter;

/** Turns the member's profile into the words the Connection card shows. */
final class ProfileText
{
	/** The ranks earned over time. Staff and honorary ranks aren't on this ladder, so they have no "next rank". */
	private static final Set<String> LADDER = Set.of("opal", "sapphire", "emerald", "ruby", "diamond", "dragonstone",
		"onyx", "zenyte");

	private ProfileText()
	{
	}

	/** "deputy_owner" becomes "Deputy Owner", "og_gamer" becomes "OG Gamer". */
	static String rank(String rank)
	{
		if (rank == null || rank.isEmpty())
		{
			return "Unranked";
		}
		return Arrays.stream(rank.split("[_ ]+"))
			.map(word -> word.equalsIgnoreCase("og") ? "OG"
				: word.substring(0, 1).toUpperCase(Locale.UK) + word.substring(1).toLowerCase(Locale.UK))
			.collect(Collectors.joining(" "));
	}

	/** 976 days becomes "2y 8m", the way the website's member list shows it. */
	static String time(int days)
	{
		if (days < 30)
		{
			return days + "d";
		}
		int years = days / 365;
		int months = (days % 365) / 30;
		if (years == 0)
		{
			return months + "m";
		}
		return months == 0 ? years + "y" : years + "y " + months + "m";
	}

	static String amount(long value)
	{
		return QuantityFormatter.quantityToStackSize(value);
	}

	/** How to link Discord, or null when it's already linked. */
	static String discordHelp(ClanData.Me me)
	{
		if (me.isDiscordLinked())
		{
			return null;
		}
		if (me.getDiscordHelp() != null)
		{
			return me.getDiscordHelp();
		}
		return "To link Discord: in the Asgardians Discord, open the welcome panel, choose Set your name and enter "
			+ (me.getRsn() != null ? me.getRsn() : "your RSN") + " exactly.";
	}

	/** What's left for the next rank, or null when the member isn't on the ladder or there's nothing above them. */
	static String nextRank(ClanData.Me me)
	{
		ClanData.NextRank next = me.getNextRank();
		if (next == null || next.getName() == null || me.getRank() == null
			|| !LADDER.contains(me.getRank().toLowerCase(Locale.UK)))
		{
			return null;
		}
		long credit = next.getCreditRemaining() != null ? next.getCreditRemaining() : 0;
		int days = next.getDaysRemaining() != null ? next.getDaysRemaining() : 0;
		if (credit <= 0 && days <= 0)
		{
			return "Ready for " + rank(next.getName()) + ". Staff will promote you.";
		}
		StringBuilder left = new StringBuilder();
		if (credit > 0)
		{
			left.append(amount(credit)).append(" credit");
		}
		if (days > 0)
		{
			left.append(left.length() > 0 ? " and " : "").append(days).append(days == 1 ? " day" : " days");
		}
		return "Next rank " + rank(next.getName()) + ": " + left + " to go";
	}
}
