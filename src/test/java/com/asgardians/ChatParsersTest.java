package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChatParsersTest
{
	@Test
	public void pickpockets()
	{
		assertTrue(ChatParsers.isPickpocket("You pick the guard's pocket."));
		assertTrue(ChatParsers.isPickpocket("You pick Martin the Master Gardener's pocket."));
		assertFalse(ChatParsers.isPickpocket("You fail to pick the guard's pocket."));
		assertFalse(ChatParsers.isPickpocket("Your Vorkath kill count is: 1,512."));
	}

	@Test
	public void killCounts()
	{
		ChatParsers.KillCount kc = ChatParsers.killCount("Your Vorkath kill count is: 1,512.");
		assertEquals("Vorkath", kc.getSource());
		assertEquals(1512, kc.getCount());

		kc = ChatParsers.killCount("Your completed Chambers of Xeric count is: 50.");
		assertEquals("Chambers of Xeric", kc.getSource());
		assertEquals(50, kc.getCount());

		kc = ChatParsers.killCount("Your completed Tombs of Amascut: Expert Mode count is: 7.");
		assertEquals("Tombs of Amascut: Expert Mode", kc.getSource());

		assertNull(ChatParsers.killCount("Welcome to Old School RuneScape."));
	}

	@Test
	public void collectionLog()
	{
		assertEquals("Virtus robe top", ChatParsers.newCollectionLogItem("New item added to your collection log: Virtus robe top"));
		assertNull(ChatParsers.newCollectionLogItem("Your collection log has been updated."));
	}

	@Test
	public void pets()
	{
		assertFalse(ChatParsers.pet("You have a funny feeling like you're being followed.").isDuplicate());
		assertFalse(ChatParsers.pet("You feel something weird sneaking into your backpack.").isDuplicate());
		assertTrue(ChatParsers.pet("You have a funny feeling like you would have been followed...").isDuplicate());
		assertNull(ChatParsers.pet("You feel a funny feeling."));
	}

	@Test
	public void clanPetBroadcast()
	{
		ChatParsers.ClanPet pet = ChatParsers.clanPet("Str4tty has a funny feeling like he's being followed: Vorki at 1,234 killcount.");
		assertEquals("Str4tty", pet.getRsn());
		assertEquals("Vorki", pet.getPetName());

		pet = ChatParsers.clanPet("Im CoffeeMan feels something weird sneaking into her backpack: Heron at 99 Fishing.");
		assertEquals("Im CoffeeMan", pet.getRsn());
		assertEquals("Heron", pet.getPetName());

		assertNull(ChatParsers.clanPet("Str4tty received a drop: Twisted bow (1,400,000,000 coins)."));
	}
}
