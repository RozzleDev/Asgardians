package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DropRecorderTest
{
	private static final int VIRTUS_TOP = 26243;
	private static final int BONES = 526;

	private final DropRecorder recorder = new DropRecorder(new DropRecorder.Items()
	{
		@Override
		public String name(int itemId)
		{
			return itemId == VIRTUS_TOP ? "Virtus robe top" : "Bones";
		}

		@Override
		public long price(int itemId)
		{
			return itemId == VIRTUS_TOP ? 28_900_000 : 100;
		}

		@Override
		public Integer idForName(String name)
		{
			return null;
		}
	});

	private static Map<Integer, Integer> stacks(int... idQty)
	{
		Map<Integer, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			map.put(idQty[i], idQty[i + 1]);
		}
		return map;
	}

	@Test
	public void lootRecordsEveryItemAndTotal()
	{
		Record record = recorder.loot("Vardorvis", 12223, stacks(VIRTUS_TOP, 1, BONES, 3), null, 416, 1_000);
		assertEquals(Record.Type.loot, record.getType());
		assertEquals(2, record.getItems().size());
		assertEquals(28_900_300, record.totalValue);
		assertEquals("Virtus robe top (Vardorvis)", record.describe());
		assertNull(record.getPartySize());
	}

	@Test
	public void killCountBeforeOrAfterLootIsAttached()
	{
		recorder.killCount(new ChatParsers.KillCount("Vardorvis", 511), 1_000);
		Record before = recorder.loot("Vardorvis", 12223, stacks(BONES, 1), null, 416, 2_000);
		assertEquals(Integer.valueOf(511), before.getSource().getKc());

		Record after = recorder.loot("Zulrah", 2042, stacks(BONES, 1), null, 416, 30_000);
		assertNull(after.getSource().getKc());
		recorder.killCount(new ChatParsers.KillCount("Zulrah", 77), 30_500);
		assertEquals(Integer.valueOf(77), after.getSource().getKc());
	}

	@Test
	public void killCountMatchesWithoutLeadingThe()
	{
		ChatParsers.KillCount kc = ChatParsers.killCount("Your Gauntlet completion count is: 40.");
		recorder.killCount(kc, 1_000);
		Record record = recorder.loot("The Gauntlet", null, stacks(BONES, 1), null, 416, 2_000);
		assertEquals(Integer.valueOf(40), record.getSource().getKc());
	}

	@Test
	public void staleKillCountIsIgnored()
	{
		recorder.killCount(new ChatParsers.KillCount("Vardorvis", 511), 1_000);
		Record record = recorder.loot("Vardorvis", 12223, stacks(BONES, 1), null, 416, 60_000);
		assertNull(record.getSource().getKc());
	}

	@Test
	public void clogFindsItemIdFromRecentLoot()
	{
		recorder.loot("Vardorvis", 12223, stacks(VIRTUS_TOP, 1), null, 416, 1_000);
		Record clog = recorder.clog("Virtus robe top", 496, 416, 2_000);
		assertEquals(Integer.valueOf(VIRTUS_TOP), clog.getItemId());
		assertEquals(Integer.valueOf(496), clog.getCompleted());
	}

	@Test
	public void petIsNamedByOwnClanBroadcastOnly()
	{
		Record pet = recorder.pet(false, 416, 1_000);
		recorder.clanPet(new ChatParsers.ClanPet("Someone Else", "Vorki"), "Str4tty", 1_200);
		assertNull(pet.getPetName());
		recorder.clanPet(new ChatParsers.ClanPet("Str4tty", "Vorki"), "Str4tty", 1_300);
		assertEquals("Vorki", pet.getPetName());
	}

	@Test
	public void partySizeIsKept()
	{
		Record record = recorder.loot("Chambers of Xeric", null, stacks(BONES, 1), 3, 416, 1_000);
		assertEquals(Integer.valueOf(3), record.getPartySize());
	}
}
