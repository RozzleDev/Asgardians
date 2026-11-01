package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class RecordQueueTest
{
	private static Record record(long created)
	{
		Record record = new Record(Record.Type.pet, "2026-10-22T12:00:00Z", 416);
		record.createdMillis = created;
		return record;
	}

	@Test
	public void batchStopsAtRecordWaitingForScreenshot()
	{
		RecordQueue queue = new RecordQueue();
		Record first = record(0);
		Record waiting = record(0);
		waiting.setScreenshot(true);
		waiting.awaitingScreenshot = true;
		Record third = record(0);
		queue.add(first);
		queue.add(waiting);
		queue.add(third);

		assertEquals(List.of(first), queue.takeBatch(1_000));
		queue.attachScreenshot(waiting.getId(), new byte[]{1});
		List<Record> batch = queue.takeBatch(1_000);
		assertEquals(List.of(waiting, third), batch);
		assertEquals(1, queue.screenshotsFor(batch).size());
	}

	@Test
	public void screenshotTimesOut()
	{
		RecordQueue queue = new RecordQueue();
		Record waiting = record(0);
		waiting.setScreenshot(true);
		waiting.awaitingScreenshot = true;
		queue.add(waiting);
		assertTrue(queue.takeBatch(1_000).isEmpty());
		List<Record> batch = queue.takeBatch(RecordQueue.SCREENSHOT_WAIT_MILLIS + 1);
		assertEquals(1, batch.size());
		assertFalse(batch.get(0).isScreenshot());
	}

	@Test
	public void noScreenshotReleasesTheRecordStraightAway()
	{
		RecordQueue queue = new RecordQueue();
		Record waiting = record(0);
		waiting.setScreenshot(true);
		waiting.awaitingScreenshot = true;
		queue.add(waiting);
		queue.noScreenshot(waiting.getId());
		List<Record> batch = queue.takeBatch(0);
		assertEquals(List.of(waiting), batch);
		assertFalse(batch.get(0).isScreenshot());
		assertTrue(queue.screenshotsFor(batch).isEmpty());
	}

	@Test
	public void failedBatchGoesBackInOrder()
	{
		RecordQueue queue = new RecordQueue();
		Record a = record(0);
		Record b = record(0);
		queue.add(a);
		queue.add(b);
		List<Record> batch = queue.takeBatch(0);
		queue.requeue(batch);
		assertEquals(List.of(a, b), queue.takeBatch(0));
		assertEquals(RecordQueue.Status.FAILED, queue.recent().get(0).status);
	}

	@Test
	public void rejectedBatchIsRetriedOneByOneAndOnlyTheBadRecordIsDropped()
	{
		RecordQueue queue = new RecordQueue();
		Record good1 = record(0);
		Record bad = record(0);
		Record good2 = record(0);
		queue.add(good1);
		queue.add(bad);
		queue.add(good2);

		List<Record> batch = queue.takeBatch(0);
		assertEquals(3, batch.size());
		assertTrue(queue.rejected(batch, "records[1].type must be loot, pet or clog."));

		List<Record> first = queue.takeBatch(0);
		assertEquals(List.of(good1), first);
		queue.sent(first);

		List<Record> second = queue.takeBatch(0);
		assertEquals(List.of(bad), second);
		assertFalse(queue.rejected(second, "type must be loot, pet or clog."));

		assertEquals(List.of(good2), queue.takeBatch(0));
		assertEquals(0, queue.size());

		RecordQueue.Entry badEntry = queue.recent().get(1);
		assertEquals(RecordQueue.Status.REJECTED, badEntry.status);
		assertEquals("type must be loot, pet or clog.", badEntry.reason);
		assertEquals(RecordQueue.Status.SENT, queue.recent().get(2).status);
	}

	@Test
	public void batchesGoBackToFullSizeAfterIsolating()
	{
		RecordQueue queue = new RecordQueue();
		for (int i = 0; i < 2; i++)
		{
			queue.add(record(0));
		}
		queue.rejected(queue.takeBatch(0), "bad");
		queue.sent(queue.takeBatch(0));
		queue.sent(queue.takeBatch(0));
		for (int i = 0; i < 5; i++)
		{
			queue.add(record(0));
		}
		assertEquals(5, queue.takeBatch(0).size());
	}

	@Test
	public void historyKeepsLastTwenty()
	{
		RecordQueue queue = new RecordQueue();
		for (int i = 0; i < 25; i++)
		{
			queue.add(record(0));
		}
		assertEquals(RecordQueue.HISTORY, queue.recent().size());
	}
}
