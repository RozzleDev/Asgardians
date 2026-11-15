package com.asgardians;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SantaMessagesTest
{
	private static SantaData.LogItem item(long id, String kind, String rsn)
	{
		SantaData.LogItem i = new SantaData.LogItem();
		i.setId(id);
		i.setKind(kind);
		i.setRsn(rsn);
		i.setEncounterId(1);
		return i;
	}

	private static SantaData.Board board(SantaData.LogItem... recent)
	{
		SantaData.Encounter e = new SantaData.Encounter();
		e.setId(1);
		e.setRevealed(true);
		e.setName("The Nutcracka");
		e.setShortName("Nutcracka");
		SantaData.Board b = new SantaData.Board();
		b.setEncounters(new ArrayList<>(Arrays.asList(e)));
		b.setRecent(new ArrayList<>(Arrays.asList(recent)));
		return b;
	}

	@Test
	public void onlyMyDropsAndClanWideNewsBecomeMessages()
	{
		SantaData.LogItem mine = item(12, "hit", "Iron Orish");
		mine.setItemName("Dragon warhammer");
		mine.setDamage(42);
		SantaData.LogItem theirs = item(11, "hit", "Tyr Hand");
		theirs.setItemName("Abyssal whip");
		SantaData.LogItem attack = item(13, "attack", null);
		attack.setAttackName("Lockjaw");
		SantaData.LogItem minions = item(14, "attack", null);
		minions.setAttackName("Passive Aggression");
		minions.setMinion(true);
		SantaData.LogItem defeat = item(15, "defeat", null);
		SantaData.LogItem old = item(9, "defeat", null);
		SantaData.Board b = board(defeat, minions, attack, mine, theirs, old);

		List<String> lines = SantaMessages.lines(b, 10, "Iron Orish");
		assertEquals(Arrays.asList(
			"Santa Claws: your Dragon warhammer hit Nutcracka for 42 damage!",
			"Santa Claws: Nutcracka is charging Lockjaw! Help block it in the Santa Claws panel.",
			"Santa Claws: the minions are charging Passive Aggression! Help block it in the Santa Claws panel.",
			"Santa Claws: Nutcracka has been defeated!"), lines);
		assertEquals(15, SantaMessages.newest(b, 0));
	}

	@Test
	public void theFirstLookOnlySetsTheStartingPoint()
	{
		assertTrue(SantaMessages.lines(board(item(5, "defeat", null)), -1, "Iron Orish").isEmpty());
	}

	@Test
	public void aShadedHitKeepsItsDamageHidden()
	{
		SantaData.LogItem hit = item(3, "hit", "Iron Orish");
		hit.setItemName("Abyssal whip");
		hit.setHidden(true);
		assertEquals(Arrays.asList("Santa Claws: your Abyssal whip hit Nutcracka, but the shade hides the damage."),
			SantaMessages.lines(board(hit), 0, "iron orish"));
	}
}
