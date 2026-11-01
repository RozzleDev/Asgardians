package com.asgardians;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class ChatPrivacyTest
{
	@Test
	public void recognisesPrivateLines()
	{
		ChatPrivacy privacy = new ChatPrivacy();
		assertTrue(privacy.isPrivate("From Dolorosa: are you coming to the raid?"));
		assertTrue(privacy.isPrivate("To Dolorosa: yes, two minutes"));
		assertTrue(privacy.isPrivate("[17:20] From Str4tty: hi"));
		assertTrue("non-breaking spaces in names", privacy.isPrivate("From Slay Solo: hi"));
	}

	@Test
	public void leavesOrdinaryLinesAlone()
	{
		ChatPrivacy privacy = new ChatPrivacy();
		assertFalse(privacy.isPrivate("Rozzle: anyone for cox"));
		assertFalse(privacy.isPrivate("Your Vorkath kill count is: 512."));
		assertFalse(privacy.isPrivate("Attack"));
		assertFalse("needs a colon after the name", privacy.isPrivate("From here you can see the bank"));
	}

	@Test
	public void hidesOtherLinesFromRecentPrivateContacts()
	{
		ChatPrivacy privacy = new ChatPrivacy();
		assertFalse(privacy.isPrivate("Dolorosa: meet at the bank"));
		privacy.privateMessage("Dolorosa", 1_000);
		assertTrue(privacy.isPrivate("Dolorosa: meet at the bank"));
		assertTrue("matches any case", privacy.isPrivate("[Clan] DOLOROSA: hi"));
		assertFalse(privacy.isPrivate("Str4tty: hello"));
	}

	@Test
	public void remembersARecentMessageForTheSafetyCheck()
	{
		ChatPrivacy privacy = new ChatPrivacy();
		assertFalse(privacy.messagedRecently(1_000));
		privacy.privateMessage("Dolorosa", 1_000);
		assertTrue(privacy.messagedRecently(1_000 + ChatPrivacy.RECENT_MILLIS - 1));
		assertFalse(privacy.messagedRecently(1_000 + ChatPrivacy.RECENT_MILLIS));
	}

	@Test
	public void watchListAndValueDecideLootScreenshots()
	{
		ServerSettings.Screenshots shots = new ServerSettings.Screenshots();
		Record cheap = new Record(Record.Type.loot, "2026-11-01T12:00:00Z", 416);
		cheap.setItems(List.of(new Record.Item(22124, "Superior dragon bones", 1, 9_000)));
		cheap.totalValue = 9_000;
		assertFalse(shots.wantsLoot(cheap));

		shots.setWatch(java.util.Set.of(22124));
		assertTrue(shots.wantsLoot(cheap));

		shots.setWatch(null);
		assertFalse("a null list from the server is safe", shots.wantsLoot(cheap));

		cheap.totalValue = 1_000_000;
		assertTrue(shots.wantsLoot(cheap));
	}
}
