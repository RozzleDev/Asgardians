package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ReadyCheckTest
{
	private static Map<Long, String> party()
	{
		Map<Long, String> m = new LinkedHashMap<>();
		m.put(1L, "pricey");
		m.put(2L, "Clo");
		m.put(3L, "Thor");
		return m;
	}

	@Test
	public void countsAnswersAndEndsWhenEveryoneHasAnswered()
	{
		ReadyCheck rc = new ReadyCheck();
		assertNull(rc.view(party(), 1, 0));
		assertTrue(rc.start(1000, "pricey", 1000));
		// The party server echoes the start back to the sender: that mustn't restart it.
		assertFalse(rc.start(1000, "pricey", 5000));
		rc.answer(1000, 1, true, 1000);
		ReadyCheck.View v = rc.view(party(), 2, 6000);
		assertEquals("1/3 ready", v.summary());
		assertEquals(25, v.getSecondsLeft());
		assertEquals(ReadyCheck.Answer.WAITING, v.getMine());
		// An answer to an older check is ignored.
		assertFalse(rc.answer(999, 2, true, 6000));
		rc.answer(1000, 2, true, 7000);
		rc.answer(1000, 3, false, 8000);
		v = rc.view(party(), 2, 9000);
		assertTrue(v.isOver());
		assertEquals("2/3 ready. Not ready: Thor.", v.summary());
		// The result stays up for 10 seconds after the last answer, then goes.
		assertTrue(rc.view(party(), 2, 16000) != null);
		assertNull(rc.view(party(), 2, 18000));
	}

	@Test
	public void newPartiesAreNamedAsgAndThreeDigits()
	{
		java.util.Random random = new java.util.Random(1);
		for (int i = 0; i < 200; i++)
		{
			assertTrue(PartyTabs.newPartyName(random).matches("ASG-[1-9][0-9]{2}"));
		}
	}

	@Test
	public void timesOutAfterThirtySeconds()
	{
		ReadyCheck rc = new ReadyCheck();
		rc.start(1000, "Clo", 1000);
		rc.answer(1000, 2, true, 1000);
		rc.answer(1000, 1, true, 2000);
		ReadyCheck.View v = rc.view(party(), 1, 31000);
		assertTrue(v.isOver());
		assertEquals("2/3 ready. No answer: Thor.", v.summary());
		assertNull(rc.view(party(), 1, 41000));

		rc.start(50000, "Clo", 50000);
		for (long id = 1; id <= 3; id++)
		{
			rc.answer(50000, id, true, 51000);
		}
		assertEquals("All 3 ready!", rc.view(party(), 1, 52000).summary());
	}
}
