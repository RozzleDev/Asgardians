package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ClogCaptureTest
{
	@Test
	public void finishesOnlyOnceTheScriptGoesQuiet()
	{
		ClogCapture capture = new ClogCapture();
		capture.item(26243, 1, 100);
		capture.item(13071, 3, 100);
		capture.item(13071, 3, 101);
		assertNull(capture.finishIfQuiet(101 + ClogCapture.QUIET_TICKS));

		Map<Integer, Integer> done = capture.finishIfQuiet(101 + ClogCapture.QUIET_TICKS + 1);
		assertEquals(Map.of(26243, 1, 13071, 3), done);
		assertNull("cleared after finishing", capture.finishIfQuiet(1_000));
	}

	@Test
	public void ignoresEmptySlotsAndResets()
	{
		ClogCapture capture = new ClogCapture();
		capture.item(0, 1, 5);
		capture.item(995, 0, 5);
		assertNull(capture.finishIfQuiet(100));

		capture.item(26243, 1, 5);
		capture.reset();
		assertNull(capture.finishIfQuiet(100));
	}

	@Test
	public void namesItems()
	{
		List<ClogCapture.Item> items = ClogCapture.named(Map.of(26243, 1), id -> "Virtus robe top");
		assertEquals(new ClogCapture.Item(26243, "Virtus robe top", 1), items.get(0));
	}
}
