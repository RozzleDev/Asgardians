package com.asgardians;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.AnimationID;

/**
 * Spots handegg throws (Easter 2018 eggs from Diango) that the local player is part of. The game says nothing to the
 * player who's hit, so this goes by animations: a player throws an egg, and another plays the catch animation. The
 * two can arrive in either order within a tick, so each is held for a few ticks waiting for the other. Catching is a
 * generic animation, so it only counts next to a throw by someone else.
 */
class EggTracker
{
	/** Holy, peaceful and chaotic handegg throws. */
	static final Set<Integer> THROWS = Set.of(AnimationID.HUMAN_THROW_EGG_SARA, AnimationID.HUMAN_THROW_EGG_GUTHIX,
		AnimationID.HUMAN_THROW_EGG_ZAM);
	static final int CATCH = AnimationID.HUMAN_CATCH;
	/** How long an egg can be in the air, in game ticks. */
	static final int WINDOW_TICKS = 8;

	@Value
	static class Egg
	{
		String thrower;
		String target;
	}

	@Value
	private static class Seen
	{
		String name;
		int tick;
	}

	private final Deque<Seen> throwsSeen = new ArrayDeque<>();
	private final Deque<Seen> catchesSeen = new ArrayDeque<>();

	static boolean isEggAnimation(int animation)
	{
		return animation == CATCH || THROWS.contains(animation);
	}

	/**
	 * Called when any player starts an animation.
	 *
	 * @return the egging, when this throw or catch completes one the local player threw or was hit by; otherwise null
	 */
	Egg animation(String actor, int animation, int tick, String me)
	{
		String name = clean(actor);
		throwsSeen.removeIf(s -> tick - s.getTick() > WINDOW_TICKS || s.getTick() > tick);
		catchesSeen.removeIf(s -> tick - s.getTick() > WINDOW_TICKS || s.getTick() > tick);
		if (name == null || me == null || !isEggAnimation(animation))
		{
			return null;
		}
		boolean isThrow = THROWS.contains(animation);
		// A throw pairs with the latest catch by someone else, and a catch with the latest throw. Each is used once.
		Seen other = takeLatestNotBy(isThrow ? catchesSeen : throwsSeen, name);
		if (other == null)
		{
			(isThrow ? throwsSeen : catchesSeen).addLast(new Seen(name, tick));
			return null;
		}
		Egg egg = isThrow ? new Egg(name, other.getName()) : new Egg(other.getName(), name);
		boolean mine = DropRecorder.sameRsn(egg.getThrower(), me) || DropRecorder.sameRsn(egg.getTarget(), me);
		return mine ? egg : null;
	}

	private static Seen takeLatestNotBy(Deque<Seen> seen, String name)
	{
		Iterator<Seen> it = seen.descendingIterator();
		while (it.hasNext())
		{
			Seen s = it.next();
			if (!DropRecorder.sameRsn(s.getName(), name))
			{
				it.remove();
				return s;
			}
		}
		return null;
	}

	void clear()
	{
		throwsSeen.clear();
		catchesSeen.clear();
	}

	/** Player names can carry non-breaking spaces. */
	private static String clean(String name)
	{
		if (name == null)
		{
			return null;
		}
		String cleaned = name.replace(' ', ' ').trim();
		return cleaned.isEmpty() ? null : cleaned;
	}
}
