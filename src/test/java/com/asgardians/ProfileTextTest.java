package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import org.junit.Test;

public class ProfileTextTest
{
	private static ClanData.Me me(String rank, Long credit, Integer days)
	{
		ClanData.Me me = new ClanData.Me();
		me.setRank(rank);
		ClanData.NextRank next = new ClanData.NextRank();
		next.setName("Sapphire");
		next.setCreditRemaining(credit);
		next.setDaysRemaining(days);
		me.setNextRank(next);
		return me;
	}

	@Test
	public void ranksReadLikeTheWebsite()
	{
		assertEquals("Deputy Owner", ProfileText.rank("deputy_owner"));
		assertEquals("Opal", ProfileText.rank("opal"));
		assertEquals("OG Gamer", ProfileText.rank("og_gamer"));
		assertEquals("Unranked", ProfileText.rank(null));
	}

	@Test
	public void timeInClan()
	{
		assertEquals("12d", ProfileText.time(12));
		assertEquals("5m", ProfileText.time(150));
		assertEquals("2y 8m", ProfileText.time(976));
		assertEquals("1y", ProfileText.time(365));
	}

	@Test
	public void nextRankIsOnlyForTheLadder()
	{
		assertNull("staff aren't on the ladder", ProfileText.nextRank(me("deputy_owner", 20_000_000L, 0)));
		assertEquals("Next rank Sapphire: 20M credit to go", ProfileText.nextRank(me("opal", 20_000_000L, 0)));
		assertEquals("Next rank Sapphire: 20M credit and 3 days to go", ProfileText.nextRank(me("opal", 20_000_000L, 3)));
		assertEquals("Next rank Sapphire: 1 day to go", ProfileText.nextRank(me("opal", 0L, 1)));
		assertEquals("Ready for Sapphire. Staff will promote you.", ProfileText.nextRank(me("opal", 0L, 0)));
	}

	@Test
	public void discordHelpIsOnlyForUnlinkedMembers()
	{
		ClanData.Me me = new ClanData.Me();
		me.setRsn("Rozzle");
		assertEquals("To link Discord: in the Asgardians Discord, open the welcome panel, choose Set your name and enter Rozzle exactly.",
			ProfileText.discordHelp(me));

		me.setDiscordHelp("Ask staff to link you.");
		assertEquals("Ask staff to link you.", ProfileText.discordHelp(me));

		me.setDiscordLinked(true);
		assertNull(ProfileText.discordHelp(me));
	}

	@Test
	public void readsTheTestServersReply()
	{
		String json = "{\"rsn\":\"Rozzle\",\"rank\":\"deputy_owner\",\"joinedAt\":\"2024-02-29T15:49:47.3633333\",\"daysInClan\":976,"
			+ "\"splitCredit\":0,\"discordLinked\":true,\"recommendedRank\":\"Opal\",\"nextRank\":{\"name\":\"Sapphire\",\"minDays\":14,"
			+ "\"minCredit\":20000000,\"daysRemaining\":0,\"creditRemaining\":20000000,\"progress\":0,\"daysProgress\":1,\"creditProgress\":0},"
			+ "\"accountType\":\"IRONMAN\",\"held\":false}";
		ClanData.Me me = new Gson().fromJson(json, ClanData.Me.class);
		assertEquals(Integer.valueOf(976), me.getDaysInClan());
		assertEquals(Long.valueOf(0), me.getSplitCredit());
		assertEquals(Long.valueOf(20_000_000), me.getNextRank().getCreditRemaining());
		assertNull(me.getDrops());
	}
}
