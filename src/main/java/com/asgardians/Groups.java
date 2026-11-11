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
		String askedBefore = before.getWaiting();
		String askedNow = now.getWaiting();
		// The host accepted the member's request, which took them out of any group they were in.
		boolean accepted = is != null && is.getId().equals(askedBefore);

		if (askedNow != null && !askedNow.equals(askedBefore) && now.find(askedNow) != null)
		{
			ClanData.Group g = now.find(askedNow);
			out.add("You asked to join " + possessive(g.getHost()) + " " + g.getTitle() + " group. You'll join when the host accepts you.");
		}
		if (askedBefore != null && !askedBefore.equals(askedNow) && !accepted)
		{
			ClanData.Group g = before.find(askedBefore);
			String title = g != null ? g.getTitle() : "the";
			if (askedBefore.equals(leftOnPurpose))
			{
				out.add("You took back your request to join the " + title + " group.");
			}
			else if ("declined".equals(now.getReason()))
			{
				out.add((g != null && g.getHost() != null ? g.getHost() : "The host") + " didn't accept you into the " + title + " group this time.");
			}
			else if (now.find(askedBefore) == null)
			{
				out.add("The " + title + " group you asked to join was closed.");
			}
			else
			{
				out.add("Your request to join the " + title + " group has ended.");
			}
		}

		if (was != null && (is == null || !is.getId().equals(was.getId())))
		{
			if (was.getId().equals(leftOnPurpose) || accepted)
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
			if (DropRecorder.sameRsn(is.getHost(), me))
			{
				out.add("Your " + is.getTitle() + " group is open on " + world(is) + (is.isApproval()
					? ". Others can ask to join from the Groups tab, and you pick who joins." : ". Others can join from the Groups tab."));
			}
			else
			{
				out.add((accepted ? (is.getHost() != null ? is.getHost() : "The host") + " accepted you. " : "")
					+ "You joined " + possessive(is.getHost()) + " " + is.getTitle() + " group (" + is.seats() + ") on " + world(is) + ".");
			}
			if (is.isFull())
			{
				out.add(full(is));
			}
			return out;
		}

		// Still in the same group: what changed in it.
		if (DropRecorder.sameRsn(is.getHost(), me))
		{
			for (ClanData.GroupMember m : orEmpty(is.getRequests()))
			{
				if (m != null && m.getRsn() != null && !was.asked(m.getRsn()) && !is.has(m.getRsn()))
				{
					out.add(m.getRsn() + (m.getKc() != null ? " (" + m.getKc() + " kc)" : "")
						+ " asked to join your group. Accept or decline in the Groups tab.");
				}
			}
		}
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
		return started(before, now, me, activity -> true);
	}

	/** @param rings whether new groups for an activity ID are announced to the member */
	static List<String> started(ClanData.GroupList before, ClanData.GroupList now, String me,
		java.util.function.Predicate<String> rings)
	{
		List<String> out = new ArrayList<>();
		for (ClanData.Group g : startedGroups(before, now, me, rings))
		{
			out.add(startedLine(g));
		}
		return out;
	}

	/** Groups other members started since the last list that still have room, for activities that ring. */
	static List<ClanData.Group> startedGroups(ClanData.GroupList before, ClanData.GroupList now, String me,
		java.util.function.Predicate<String> rings)
	{
		List<ClanData.Group> out = new ArrayList<>();
		if (before == null || now == null)
		{
			return out;
		}
		for (ClanData.Group g : orEmpty(now.getGroups()))
		{
			if (g != null && g.getId() != null && before.find(g.getId()) == null && !DropRecorder.sameRsn(g.getHost(), me)
				&& !g.isFull() && rings.test(g.getActivity()))
			{
				out.add(g);
			}
		}
		return out;
	}

	/**
	 * The group's Discord voice link, when it's a Discord channel or invite link: https on discord.com (or
	 * ptb./canary.), discord.gg or discordapp.com. Anything else is ignored, so a bad link can't open some other site.
	 */
	static String voiceLink(ClanData.Group g)
	{
		String url = g != null && g.getVoice() != null ? g.getVoice().trim() : null;
		okhttp3.HttpUrl link = url != null ? okhttp3.HttpUrl.parse(url) : null;
		if (link == null || !link.isHttps())
		{
			return null;
		}
		String host = link.host();
		boolean discord = host.equals("discord.com") || host.equals("ptb.discord.com") || host.equals("canary.discord.com")
			|| host.equals("discord.gg") || host.equals("discordapp.com");
		return discord ? link.toString() : null;
	}

	static String startedLine(ClanData.Group g)
	{
		return startedLine(g, g.getTitle());
	}

	/** @param title the group's title, short where the website gives short names */
	static String startedLine(ClanData.Group g, String title)
	{
		return (g.getHost() != null ? g.getHost() : "Someone") + " started a group: " + title + ", " + g.seats() + ", "
			+ world(g) + (g.isApproval() ? ". Ask to join from the Groups tab." : ". Join from the Groups tab.");
	}

	/**
	 * "ToB HM" when the website gives short names for the activity and its mode, otherwise the full title. A part
	 * without a short name is shown in full: "ToB (Entry mode)" or "Theatre of Blood HM".
	 */
	static String shortTitle(ClanData.Group g, ClanData.GroupActivity activity)
	{
		String shortName = activity != null ? blankToNull(activity.getShortName()) : null;
		String mode = blankToNull(g.getMode());
		String shortMode = mode != null && activity != null && activity.getModeShorts() != null
			? blankToNull(activity.getModeShorts().get(mode)) : null;
		if (shortName == null && shortMode == null)
		{
			return g.getTitle();
		}
		String base = shortName != null ? shortName : g.getName() != null ? g.getName() : g.getActivity() != null ? g.getActivity() : "Group";
		if (mode == null)
		{
			return base;
		}
		return shortMode != null ? base + " " + shortMode : base + " (" + mode + ")";
	}

	private static String blankToNull(String s)
	{
		return s != null && !s.trim().isEmpty() ? s.trim() : null;
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
