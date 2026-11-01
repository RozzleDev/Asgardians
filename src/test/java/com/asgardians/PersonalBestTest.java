package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PersonalBestTest
{
	@Test
	public void splitsTeamSizeFromRuneLiteKeys()
	{
		PersonalBest solo = PersonalBest.of("theatre of blood solo", 1500.0);
		assertEquals("theatre of blood", solo.getBoss());
		assertEquals("1", solo.getTeamSize());

		PersonalBest three = PersonalBest.of("chambers of xeric challenge mode 3 players", 1800.6);
		assertEquals("chambers of xeric challenge mode", three.getBoss());
		assertEquals("3", three.getTeamSize());

		assertEquals("11-15", PersonalBest.of("chambers of xeric 11-15 players", 2000).getTeamSize());
		assertEquals("24+", PersonalBest.of("chambers of xeric 24+ players", 2000).getTeamSize());

		PersonalBest vorkath = PersonalBest.of("vorkath", 60.6);
		assertEquals("vorkath", vorkath.getBoss());
		assertNull(vorkath.getTeamSize());
	}

	@Test
	public void preciseTimesGetTicks()
	{
		PersonalBest precise = PersonalBest.of("vorkath", 84.6);
		assertTrue(precise.isPrecise());
		assertEquals(Integer.valueOf(141), precise.getTicks());
		assertEquals("vorkath 1:24.60", precise.display());

		PersonalBest whole = PersonalBest.of("vorkath", 85.0);
		assertFalse(whole.isPrecise());
		assertNull(whole.getTicks());
		assertEquals("vorkath 1:25", whole.display());

		assertEquals("tzkal-zuk 1:02:03.60", PersonalBest.of("tzkal-zuk", 3723.6).display());
	}

	@Test
	public void ignoresUnusableValues()
	{
		assertNull(PersonalBest.parse("vorkath", "abc"));
		assertNull(PersonalBest.parse("vorkath", "0"));
		assertNull(PersonalBest.parse("vorkath", null));
	}

	private static PbTracker tracker(Map<String, String> store)
	{
		return new PbTracker(new PbTracker.Store()
		{
			@Override
			public List<String> keys()
			{
				return List.copyOf(store.keySet());
			}

			@Override
			public String value(String key)
			{
				return store.get(key);
			}

			@Override
			public Integer killCount(String boss)
			{
				return "vorkath".equals(boss) ? 512 : null;
			}
		});
	}

	@Test
	public void changeAfterNewPbMessageIsSentLive()
	{
		PbTracker pbs = tracker(Map.of());
		pbs.chatMessage("Fight duration: 1:24.60 (new personal best)", 1_000);
		PersonalBest pb = pbs.changed("vorkath", "84.6", 1_100);
		assertTrue(pb.isNewPb());
		assertEquals(Integer.valueOf(512), pb.getKc());
		assertFalse(pbs.syncDue(100_000));
	}

	@Test
	public void otherChangesWaitForOneFullSync()
	{
		PbTracker pbs = tracker(Map.of());
		assertNull(pbs.changed("tzkal-zuk", "3723.6", 1_000));
		assertNull(pbs.changed("chambers of xeric 3 players", "1800", 2_000));
		assertFalse(pbs.syncDue(2_000 + PbTracker.SYNC_DEBOUNCE_MILLIS - 1));
		assertTrue(pbs.syncDue(2_000 + PbTracker.SYNC_DEBOUNCE_MILLIS));
		pbs.syncStarted();
		assertFalse(pbs.syncDue(1_000_000));
	}

	@Test
	public void oldNewPbMessageDoesNotCount()
	{
		PbTracker pbs = tracker(Map.of());
		pbs.chatMessage("Fight duration: 1:24.60 (new personal best)", 1_000);
		assertNull(pbs.changed("vorkath", "84.6", 1_000 + PbTracker.NEW_PB_WINDOW_MILLIS + 1));
	}

	@Test
	public void snapshotReadsWholeStoreWithKillCounts()
	{
		Map<String, String> store = new LinkedHashMap<>();
		store.put("vorkath", "84.6");
		store.put("theatre of blood 4 players", "1200.0");
		store.put("broken", "nope");
		List<PersonalBest> all = tracker(store).snapshot();
		assertEquals(2, all.size());
		assertEquals(Integer.valueOf(512), all.get(0).getKc());
		assertFalse(all.get(0).isNewPb());
		assertEquals("4", all.get(1).getTeamSize());
	}
}
