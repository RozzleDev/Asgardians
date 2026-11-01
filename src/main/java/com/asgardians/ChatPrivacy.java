package com.asgardians;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises private messages so they can be hidden before a screenshot. Screenshots are uploaded and kept, so a private
 * conversation must never be in one. Follows the approach of the Dink and Valiance Clan plugins (both BSD 2-Clause).
 * The names here stay in memory and are never sent anywhere. Thread-safe.
 */
class ChatPrivacy
{
	/** After a private message this recent, a screenshot with nothing to hide can't be trusted, so none is taken. */
	static final long RECENT_MILLIS = 45_000;
	private static final int MAX_NAMES = 30;

	/** "From Name: text" or "To Name: text", maybe after a timestamp. */
	private static final Pattern PRIVATE_LINE = Pattern.compile("^(?:\\[[0-9:]+\\]\\s*)?(?:From|To)\\s+[^:]{1,30}:.*");

	private final Deque<String> names = new ArrayDeque<>();
	private long lastMessageMillis = -RECENT_MILLIS - 1;

	/** Call for every private message, sent or received. */
	synchronized void privateMessage(String name, long now)
	{
		lastMessageMillis = now;
		String clean = clean(name);
		if (!clean.isEmpty())
		{
			names.remove(clean);
			names.addFirst(clean);
			while (names.size() > MAX_NAMES)
			{
				names.removeLast();
			}
		}
	}

	synchronized boolean messagedRecently(long now)
	{
		return now - lastMessageMillis < RECENT_MILLIS;
	}

	/** Whether a line of on-screen text, with tags removed, is or may be a private message. */
	synchronized boolean isPrivate(String text)
	{
		String plain = text.replace(' ', ' ').trim();
		if (PRIVATE_LINE.matcher(plain).matches())
		{
			return true;
		}
		String lower = clean(plain);
		for (String name : names)
		{
			if (lower.contains(name + ":") || lower.contains(name + " :"))
			{
				return true;
			}
		}
		return false;
	}

	private static String clean(String text)
	{
		return text == null ? "" : text.replace(' ', ' ').trim().toLowerCase(Locale.UK);
	}
}
