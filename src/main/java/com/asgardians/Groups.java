package com.asgardians;

import java.util.ArrayList;
import java.util.List;

/** Chat lines for the group finder: what changed between two group lists. Pure, so it can be tested. */
final class Groups
{
	private Groups()
	{
	}

	/**
	 * Lines about the member's own group: joining or starting one, players joining and leaving, a new host, the group
	 * filling, it moving world, and the member being taken out of it.
	 *
	 * @param before    the previous list, or null on the first load (which says nothing, so a login doesn't repeat)
	 * @param me        the member's RSN
	 * @param leftOnPurpose the ID of a group the member just left or closed themselves, or null
	 */
	static List<String> changes(ClanData.GroupList before, ClanData.GroupList now, String me, String leftOnPurpose)
	{
		List<String> out = new ArrayList<>();
		if (before == null || now == null)
		{
			return out;
		}
		ClanData.Group was = before.mineGroup();
		ClanData.Group is = now.mineGroup();

		if (was != null && (is == null || !is.getId().equals(was.getId())))
		{
			if (was.getId().equals(leftOnPurpose))
			{
				out.add(DropRecorder.sameRsn(was.getHost(), me) && now.find(was.getId()) == null
					? "You closed your " + was.getTitle() + " group." : "You left the " + was.getTitle() + " group.");
			}
			else
			{
				out.add(removed(was, now));
			}
		}

		if (is == null)
		{
			return out;
		}
		if (was == null || !was.getId().equals(is.getId()))
		{
			out.add(DropRecorder.sameRsn(is.getHost(), me)
				? "Your " + is.getTitle() + " group is open on " + world(is) + ". Others can join from the Groups tab."
				: "You joined " + possessive(is.getHost()) + " " + is.getTitle() + " group (" + is.seats() + ") on " + world(is) + ".");
			if (is.isFull())
			{
				out.add(full(is));
			}
			return out;
		}

		// Still in the same group: what changed in it.
		for (ClanData.GroupMember m : orEmpty(is.getMembers()))
		{
			if (m != null && m.getRsn() != null && !was.has(m.getRsn()) && !DropRecorder.sameRsn(m.getRsn(), me))
			{
				out.add(m.getRsn() + " joined your group (" + is.seats() + ").");
			}
		}
		for (ClanData.GroupMember m : orEmpty(was.getMembers()))
		{
			if (m != null && m.getRsn() != null && !is.has(m.getRsn()) && !DropRecorder.sameRsn(m.getRsn(), me))
			{
				out.add(m.getRsn() + " left your group (" + is.seats() + ").");
			}
		}
		if (is.getHost() != null && !DropRecorder.sameRsn(is.getHost(), was.getHost()))
		{
			out.add(DropRecorder.sameRsn(is.getHost(), me) ? "You're now the group's host." : is.getHost() + " is now the group's host.");
		}
		if (is.getWorld() != null && !is.getWorld().equals(was.getWorld()))
		{
			out.add("The group moved to " + world(is) + ".");
		}
		if (is.isFull() && !was.isFull())
		{
			out.add(full(is));
		}
		return out;
	}

	/** Lines for groups other members started since the last list, for the "New groups in chat" setting. */
	static List<String> started(ClanData.GroupList before, ClanData.GroupList now, String me)
	{
		List<String> out = new ArrayList<>();
		if (before == null || now == null)
		{
			return out;
		}
		for (ClanData.Group g : orEmpty(now.getGroups()))
		{
			if (g.getId() != null && before.find(g.getId()) == null && !DropRecorder.sameRsn(g.getHost(), me) && !g.isFull())
			{
				out.add((g.getHost() != null ? g.getHost() : "Someone") + " started a " + g.getTitle() + " group ("
					+ g.seats() + ") on " + world(g) + ". Join from the Groups tab.");
			}
		}
		return out;
	}

	private static String removed(ClanData.Group was, ClanData.GroupList now)
	{
		String reason = now.getReason() != null ? now.getReason() : now.find(was.getId()) == null ? "closed" : "kicked";
		switch (reason)
		{
			case "away":
				return "You were taken out of the " + was.getTitle() + " group after being away.";
			case "removed":
				return "Clan staff removed the " + was.getTitle() + " group.";
			case "kicked":
				return "You were removed from " + possessive(was.getHost()) + " " + was.getTitle() + " group.";
			default:
				return "The " + was.getTitle() + " group was closed.";
		}
	}

	static String full(ClanData.Group g)
	{
		return "Group full (" + g.seats() + "): time to go! Hop to " + world(g) + ".";
	}

	static String world(ClanData.Group g)
	{
		return g.getWorld() != null ? "W" + g.getWorld() : "the host's world";
	}

	private static String possessive(String name)
	{
		if (name == null)
		{
			return "the";
		}
		return name.endsWith("s") ? name + "'" : name + "'s";
	}

	private static <T> List<T> orEmpty(List<T> list)
	{
		return list != null ? list : List.of();
	}
}
