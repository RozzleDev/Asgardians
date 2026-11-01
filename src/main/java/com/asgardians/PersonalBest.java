package com.asgardians;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

/**
 * One personal best as RuneLite's Chat Commands plugin stores it: config group "personalbest", RS profile scoped,
 * key = lowercase boss name with an optional team size suffix ("chambers of xeric 3 players", "theatre of blood solo"),
 * value = seconds.
 */
@Data
class PersonalBest
{
	static final String CONFIG_GROUP = "personalbest";
	static final String KC_CONFIG_GROUP = "killcount";

	/** "solo", "3 players", "11-15 players", "24+ players". */
	private static final Pattern TEAM_SUFFIX = Pattern.compile("^(.+?) (solo|\\d+(?:-\\d+|\\+)? players)$");
	private static final double TICK_SECONDS = 0.6;

	/** RuneLite's key, exactly as stored. Unique per boss and team size. */
	private final String key;
	/** The key without its team size. */
	private final String boss;
	/** "1" for solo, "3", "11-15", "24+"; null when the PB isn't for a team size. */
	private final String teamSize;
	private final double seconds;
	/** Game ticks, when the time is precise and a whole number of ticks. */
	private final Integer ticks;
	/** False when the time is whole seconds, which may be a rounded (non-precise) time. */
	private final boolean precise;
	private Integer kc;
	/** True when this came from a "(new personal best)" message just now, rather than a sync. */
	private boolean newPb;

	static PersonalBest of(String key, double seconds)
	{
		String boss = key;
		String teamSize = null;
		Matcher m = TEAM_SUFFIX.matcher(key);
		if (m.matches())
		{
			boss = m.group(1);
			String suffix = m.group(2);
			teamSize = suffix.equals("solo") ? "1" : suffix.substring(0, suffix.length() - " players".length());
		}
		boolean precise = seconds != Math.floor(seconds);
		Integer ticks = null;
		if (precise)
		{
			double t = seconds / TICK_SECONDS;
			long rounded = Math.round(t);
			if (Math.abs(t - rounded) < 0.02)
			{
				ticks = (int) rounded;
			}
		}
		return new PersonalBest(key, boss, teamSize, seconds, ticks, precise);
	}

	/** Parses a stored value, or returns null for anything that isn't a positive number of seconds. */
	static PersonalBest parse(String key, String value)
	{
		if (key == null || value == null)
		{
			return null;
		}
		try
		{
			double seconds = Double.parseDouble(value);
			return seconds > 0 ? of(key, seconds) : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	String display()
	{
		int whole = (int) seconds;
		String time = whole >= 3600
			? String.format("%d:%02d:%02d", whole / 3600, whole / 60 % 60, whole % 60)
			: String.format("%d:%02d", whole / 60, whole % 60);
		if (precise)
		{
			time += String.format(".%02d", Math.round((seconds - whole) * 100) % 100);
		}
		return key + " " + time;
	}
}
