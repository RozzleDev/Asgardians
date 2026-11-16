package com.asgardians;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Records waiting to be sent, plus the last 20 for the panel. Thread-safe: the client thread adds, the upload thread drains.
 */
class RecordQueue
{
	static final int MAX_QUEUED = 1000;
	static final int BATCH_SIZE = 50;
	static final int HISTORY = 20;
	/** How long a record waits for its screenshot before going without one. */
	static final long SCREENSHOT_WAIT_MILLIS = 10_000;
	/**
	 * The most screenshot data held while waiting to upload. When the website can't be reached, pictures pile up; past
	 * this the oldest waiting records go without theirs, so a long outage can't run the client out of memory.
	 */
	static final long MAX_SCREENSHOT_BYTES = 40L * 1024 * 1024;

	enum Status
	{
		QUEUED,
		SENT,
		/** Not sent yet; will retry. */
		FAILED,
		/** The server refused this record; it won't be sent again. */
		REJECTED
	}

	static class Entry
	{
		final Record record;
		volatile Status status = Status.QUEUED;
		/** The server's reason, for rejected records. */
		volatile String reason;

		Entry(Record record)
		{
			this.record = record;
		}
	}

	private final Deque<Record> pending = new ArrayDeque<>();
	/**
	 * After the server rejects a batch, its records are retried one per batch so only the bad one is dropped. This many
	 * of the next batches are single records.
	 */
	private int sendSingly;
	private final Map<String, byte[]> screenshots = new HashMap<>();
	private long screenshotBytes;
	private final LinkedList<Entry> history = new LinkedList<>();
	private final Map<String, Entry> historyById = new HashMap<>();

	synchronized void add(Record record)
	{
		if (pending.size() >= MAX_QUEUED)
		{
			Record dropped = pending.pollFirst();
			if (dropped != null)
			{
				dropScreenshot(dropped.getId());
			}
		}
		pending.addLast(record);
		Entry entry = new Entry(record);
		history.addFirst(entry);
		historyById.put(record.getId(), entry);
		while (history.size() > HISTORY)
		{
			historyById.remove(history.removeLast().record.getId());
		}
	}

	/** The record goes without a picture, straight away rather than after the wait. */
	synchronized void noScreenshot(String recordId)
	{
		for (Record record : pending)
		{
			if (record.getId().equals(recordId))
			{
				record.awaitingScreenshot = false;
				record.setScreenshot(false);
				return;
			}
		}
	}

	synchronized void attachScreenshot(String recordId, byte[] png)
	{
		for (Record record : pending)
		{
			if (record.getId().equals(recordId))
			{
				dropScreenshot(recordId);
				screenshots.put(recordId, png);
				screenshotBytes += png.length;
				record.awaitingScreenshot = false;
				trimScreenshots(recordId);
				return;
			}
		}
	}

	/**
	 * Takes the next batch, oldest first. A record still waiting for its screenshot stops the batch there, so records stay
	 * in order, unless it has waited too long, in which case it goes without one.
	 */
	synchronized List<Record> takeBatch(long now)
	{
		List<Record> batch = new ArrayList<>();
		int limit = sendSingly > 0 ? 1 : BATCH_SIZE;
		while (!pending.isEmpty() && batch.size() < limit)
		{
			Record next = pending.peekFirst();
			if (next.awaitingScreenshot)
			{
				if (now - next.createdMillis < SCREENSHOT_WAIT_MILLIS)
				{
					break;
				}
				next.awaitingScreenshot = false;
				next.setScreenshot(false);
			}
			batch.add(pending.pollFirst());
		}
		if (!batch.isEmpty() && sendSingly > 0)
		{
			sendSingly--;
		}
		return batch;
	}

	/**
	 * The server refused a batch it couldn't accept. A single record is dropped and marked rejected; a larger batch goes
	 * back to be retried one record at a time, so good records aren't lost with a bad one.
	 *
	 * @return true when the batch was put back to retry singly, false when its one record was dropped
	 */
	synchronized boolean rejected(List<Record> batch, String reason)
	{
		if (batch.size() > 1)
		{
			for (int i = batch.size() - 1; i >= 0; i--)
			{
				pending.addFirst(batch.get(i));
			}
			sendSingly = Math.max(sendSingly, batch.size());
			mark(batch, Status.FAILED);
			return true;
		}
		for (Record record : batch)
		{
			dropScreenshot(record.getId());
			Entry entry = historyById.get(record.getId());
			if (entry != null)
			{
				entry.status = Status.REJECTED;
				entry.reason = reason;
			}
		}
		return false;
	}

	synchronized Map<String, byte[]> screenshotsFor(List<Record> batch)
	{
		Map<String, byte[]> out = new HashMap<>();
		for (Record record : batch)
		{
			byte[] png = screenshots.get(record.getId());
			if (png != null && record.isScreenshot())
			{
				out.put(record.getId(), png);
			}
		}
		return out;
	}

	/** Puts a failed batch back at the front, in its original order. */
	synchronized void requeue(List<Record> batch)
	{
		for (int i = batch.size() - 1; i >= 0; i--)
		{
			pending.addFirst(batch.get(i));
		}
		mark(batch, Status.FAILED);
	}

	synchronized void sent(List<Record> batch)
	{
		for (Record record : batch)
		{
			dropScreenshot(record.getId());
		}
		mark(batch, Status.SENT);
	}

	/** Total screenshot data held, in bytes. */
	synchronized long screenshotBytes()
	{
		return screenshotBytes;
	}

	private void dropScreenshot(String recordId)
	{
		byte[] png = screenshots.remove(recordId);
		if (png != null)
		{
			screenshotBytes -= png.length;
		}
	}

	/** Over the limit: the oldest waiting records lose their pictures (never {@code keep}, the one just added). */
	private void trimScreenshots(String keep)
	{
		for (Record record : pending)
		{
			if (screenshotBytes <= MAX_SCREENSHOT_BYTES)
			{
				return;
			}
			if (!record.getId().equals(keep) && screenshots.containsKey(record.getId()))
			{
				dropScreenshot(record.getId());
				record.setScreenshot(false);
			}
		}
		if (screenshotBytes > MAX_SCREENSHOT_BYTES)
		{
			// Even alone it's too big (or the rest are in flight): this one goes without.
			for (Record record : pending)
			{
				if (record.getId().equals(keep))
				{
					dropScreenshot(keep);
					record.setScreenshot(false);
				}
			}
		}
	}

	synchronized int size()
	{
		return pending.size();
	}

	synchronized List<Entry> recent()
	{
		return Collections.unmodifiableList(new ArrayList<>(history));
	}

	synchronized void clear()
	{
		pending.clear();
		screenshots.clear();
		screenshotBytes = 0;
	}

	private void mark(List<Record> batch, Status status)
	{
		for (Record record : batch)
		{
			Entry entry = historyById.get(record.getId());
			if (entry != null)
			{
				entry.status = status;
			}
		}
	}
}
