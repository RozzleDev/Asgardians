package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.Test;

public class EggTrackerTest
{
	private static final int THROW = EggTracker.THROWS.iterator().next();
	private static final int CATCH = EggTracker.CATCH;

	@Test
	public void spotsEggingTheLocalPlayer()
	{
		EggTracker eggs = new EggTracker();
		assertNull(eggs.animation("pricey", THROW, 100, "Rozzle"));
		assertEquals(new EggTracker.Egg("pricey", "Rozzle"), eggs.animation("Rozzle", CATCH, 102, "Rozzle"));
	}

	@Test
	public void spotsTheLocalPlayersThrow()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("pricey", THROW, 100, "pricey");
		// Names come back with plain spaces.
		assertEquals(new EggTracker.Egg("pricey", "Mc Rozzle"),
			eggs.animation("Mc Rozzle", CATCH, 103, "pricey"));
	}

	@Test
	public void everyHandeggCounts()
	{
		for (int animation : EggTracker.THROWS)
		{
			EggTracker eggs = new EggTracker();
			eggs.animation("pricey", animation, 10, "Rozzle");
			assertEquals(new EggTracker.Egg("pricey", "Rozzle"), eggs.animation("Rozzle", CATCH, 11, "Rozzle"));
		}
	}

	@Test
	public void ignoresEggingsBetweenOtherPlayers()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("a", THROW, 100, "Rozzle");
		assertNull(eggs.animation("b", CATCH, 101, "Rozzle"));
		// That egg was caught by b, so Rozzle catching something now isn't an egging.
		assertNull(eggs.animation("Rozzle", CATCH, 102, "Rozzle"));
	}

	@Test
	public void theCatchCanArriveBeforeTheThrow()
	{
		EggTracker eggs = new EggTracker();
		assertNull(eggs.animation("Rozzle", CATCH, 100, "pricey"));
		assertEquals(new EggTracker.Egg("pricey", "Rozzle"), eggs.animation("pricey", THROW, 100, "pricey"));
	}

	@Test
	public void anOldCatchDoesntPairWithANewThrow()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("Rozzle", CATCH, 100, "pricey");
		assertNull(eggs.animation("pricey", THROW, 100 + EggTracker.WINDOW_TICKS + 1, "pricey"));
	}

	@Test
	public void aCatchWithNoThrowIsNotAnEgg()
	{
		EggTracker eggs = new EggTracker();
		assertNull(eggs.animation("Rozzle", CATCH, 100, "Rozzle"));
	}

	@Test
	public void oldThrowsDontCount()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("pricey", THROW, 100, "Rozzle");
		assertNull(eggs.animation("Rozzle", CATCH, 100 + EggTracker.WINDOW_TICKS + 1, "Rozzle"));
	}

	@Test
	public void throwersCantCatchTheirOwnEgg()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("Rozzle", THROW, 100, "Rozzle");
		assertNull(eggs.animation("Rozzle", CATCH, 101, "Rozzle"));
	}

	@Test
	public void theLatestThrowIsTheOneCaught()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("a", THROW, 100, "Rozzle");
		eggs.animation("pricey", THROW, 102, "Rozzle");
		assertEquals(new EggTracker.Egg("pricey", "Rozzle"), eggs.animation("Rozzle", CATCH, 103, "Rozzle"));
	}

	@Test
	public void otherAnimationsAreIgnored()
	{
		EggTracker eggs = new EggTracker();
		eggs.animation("pricey", 808, 100, "Rozzle");
		assertNull(eggs.animation("Rozzle", CATCH, 101, "Rozzle"));
		assertEquals(false, EggTracker.isEggAnimation(808));
	}

	@Test
	public void eggRecordsSayWhoThrewAtWhom()
	{
		Record record = new Record(Record.Type.egg, "2026-11-06T20:52:00Z", 302);
		record.setEgg(new Record.Egg("pricey", "Rozzle"));
		JsonObject json = new Gson().toJsonTree(record).getAsJsonObject();
		assertEquals("egg", json.get("type").getAsString());
		assertEquals("pricey", json.getAsJsonObject("egg").get("thrower").getAsString());
		assertEquals("Rozzle", json.getAsJsonObject("egg").get("target").getAsString());
		assertEquals("Egg: pricey egged Rozzle", record.describe());
	}

	@Test
	public void clanEggMessagesLeaveOutTheMembersOwn()
	{
		ClanData.EggFeed feed = new Gson().fromJson("{\"cursor\":\"c2\",\"eggs\":["
			+ "{\"id\":\"1\",\"thrower\":\"pricey\",\"target\":\"Rozzle\"},"
			+ "{\"id\":\"2\",\"thrower\":\"Mc Rozzle\",\"target\":\"bob\"},"
			+ "{\"id\":\"3\",\"thrower\":\"a\"},"
			+ "{\"id\":\"4\",\"thrower\":\"a\",\"target\":\"b\"}]}", ClanData.EggFeed.class);
		assertEquals(List.of("pricey egged Rozzle!", "a egged b!"), AsgardiansPlugin.eggLines(feed.getEggs(), "Mc_Rozzle"));
	}

	private static net.runelite.api.clan.ClanMember member(String name)
	{
		return new net.runelite.api.clan.ClanMember()
		{
			@Override
			public String getName()
			{
				return name;
			}

			@Override
			public net.runelite.api.clan.ClanRank getRank()
			{
				return null;
			}

			@Override
			public java.time.LocalDate getJoinDate()
			{
				return null;
			}
		};
	}

	@Test
	public void onlyClanMembersCount()
	{
		List<net.runelite.api.clan.ClanMember> members = java.util.Arrays.asList(member("pricey"), null,
			member("Mc Rozzle"));
		assertEquals(true, AsgardiansPlugin.isClanMember(members, "Pricey"));
		assertEquals(true, AsgardiansPlugin.isClanMember(members, "Mc Rozzle"));
		assertEquals(false, AsgardiansPlugin.isClanMember(members, "SomeRandom"));
		assertEquals(false, AsgardiansPlugin.isClanMember(members, null));
		assertEquals(false, AsgardiansPlugin.isClanMember(null, "pricey"));
	}

	@Test
	public void clanEggMessagesAreCapped()
	{
		ClanData.EggAlert alert = new ClanData.EggAlert();
		alert.setThrower("a");
		alert.setTarget("b");
		assertEquals(5, AsgardiansPlugin.eggLines(java.util.Collections.nCopies(9, alert), "me").size());
		assertEquals(List.of(), AsgardiansPlugin.eggLines(null, "me"));
	}
}
