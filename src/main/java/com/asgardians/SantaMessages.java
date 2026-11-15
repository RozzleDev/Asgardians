package com.asgardians;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Santa Claws chat messages from the board's latest entries. Only the member's own drops are about them; the clan-wide
 * ones are a boss being defeated and a boss (or its minions) charging a new attack. Nothing for anyone else's drops.
 */
final class SantaMessages
{
	private SantaMessages()
	{
	}

	/**
	 * The chat lines for entries newer than {@code afterId}, oldest first. Pass a negative {@code afterId} the first
	 * time: that only takes the starting point, so logging in never replays old news.
	 */
	static List<String> lines(SantaData.Board board, long afterId, String me)
	{
		List<String> out = new ArrayList<>();
		if (board == null || afterId < 0)
		{
			return out;
		}
		List<SantaData.LogItem> fresh = new ArrayList<>();
		for (SantaData.LogItem item : SantaData.orEmpty(board.getRecent()))
		{
			if (item.getId() > afterId)
			{
				fresh.add(item);
			}
		}
		fresh.sort(Comparator.comparingLong(SantaData.LogItem::getId));
		for (SantaData.LogItem item : fresh)
		{
			String line = line(board, item, me);
			if (line != null)
			{
				out.add(line);
			}
		}
		return out;
	}

	/** The newest entry's id, or {@code fallback} when there are none. */
	static long newest(SantaData.Board board, long fallback)
	{
		long max = fallback;
		for (SantaData.LogItem item : SantaData.orEmpty(board != null ? board.getRecent() : null))
		{
			max = Math.max(max, item.getId());
		}
		return max;
	}

	private static String line(SantaData.Board board, SantaData.LogItem item, String me)
	{
		String boss = bossName(board, item.getEncounterId());
		switch (item.getKind() == null ? "" : item.getKind())
		{
			case "hit":
				if (!DropRecorder.sameRsn(item.getRsn(), me))
				{
					return null;
				}
				return item.isHidden()
					? "Santa Claws: your " + item.getItemName() + " hit " + boss + ", but the shade hides the damage."
					: "Santa Claws: your " + item.getItemName() + " hit " + boss + " for " + item.getDamage() + " damage!";
			case "attack":
				return "Santa Claws: " + (item.isMinion() ? "the minions are" : boss + " is") + " charging "
					+ (item.getAttackName() != null ? item.getAttackName() : "a new attack")
					+ "! Help block it in the Santa Claws panel.";
			case "defeat":
				return "Santa Claws: " + boss + " has been defeated!";
			default:
				return null;
		}
	}

	private static String bossName(SantaData.Board board, Integer encounterId)
	{
		for (SantaData.Encounter e : SantaData.orEmpty(board.getEncounters()))
		{
			if (encounterId != null && e.getId() == encounterId && e.isRevealed())
			{
				return e.getShortName() != null ? e.getShortName() : e.getName();
			}
		}
		return "the boss";
	}
}
