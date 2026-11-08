package com.asgardians;

import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;

/**
 * The Members page: clanmates online now with their world and, when they share it, what they're doing; clanmates on
 * the member's own world first. Worlds come from the game's clan channel; activities from the website. Swing thread.
 */
final class MembersTabs
{
	static final List<String> FILTERS = List.of("Everyone", "On my world", ActivityTracker.RAIDS, ActivityTracker.BOSSING,
		ActivityTracker.SKILLING);

	@Value
	static class Member
	{
		String name;
		/** Null when offline. */
		Integer world;
		/** What they're doing, when they share it. */
		String activity;
	}

	@Value
	static class Input
	{
		/** Online first is not needed: the page sorts. */
		List<Member> members;
		/** Null when not logged in. */
		String me;
		Integer myWorld;
		/** What the plugin would share for the member right now. */
		String myActivity;
		/** "Share my activity" is on. */
		boolean sharing;
		/** The website doesn't send activities yet. */
		boolean activityUnsupported;
		/** Why the page can't show members, or null. */
		String notConnected;
	}

	@Value
	static class Ui
	{
		String filter;
		boolean showOffline;
	}

	interface Actions
	{
		void filter(String filter);

		void showOffline(boolean show);

		void toggleSharing();

		void openProfile(String rsn);
	}

	private MembersTabs()
	{
	}

	/** A known filter, or Everyone. */
	static String filter(String saved)
	{
		return saved != null && FILTERS.contains(saved) ? saved : FILTERS.get(0);
	}

	static boolean matches(Member m, String filter, Integer myWorld)
	{
		switch (filter(filter))
		{
			case "On my world":
				return m.getWorld() != null && m.getWorld().equals(myWorld);
			case "Everyone":
				return true;
			default:
				return ActivityTracker.category(m.getActivity()).equals(filter);
		}
	}

	static int online(Input in)
	{
		int n = 0;
		for (Member m : in.getMembers())
		{
			n += m.getWorld() != null ? 1 : 0;
		}
		return n;
	}

	static void draw(JPanel column, Input in, Ui ui, Actions actions)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Members");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		String filter = filter(ui.getFilter());
		addCard(column, PanelParts.dropdowns(PanelParts.dropdown(FILTERS, FILTERS.indexOf(filter), i -> actions.filter(FILTERS.get(i))),
			PanelParts.checkbox("Show offline", ui.isShowOffline(), () -> actions.showOffline(!ui.isShowOffline()))));

		// The member's own row, with the switch for sharing what they're doing.
		Card you = new Card(null).outlined(new java.awt.Color(0x6E, 0x37, 0x15), PanelParts.CARD);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		JPanel words = new JPanel();
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		words.setOpaque(false);
		words.add(PanelParts.plain("YOU" + (in.getMyWorld() != null ? " · W" + in.getMyWorld() : ""), QUIET));
		String mine = in.isSharing() ? (in.getMyActivity() != null ? in.getMyActivity() : "Online, nothing in particular")
			: "Hidden: clanmates see Online and your world";
		words.add(PanelParts.cut(mine, in.isSharing() ? TEXT : QUIET));
		top.add(words, BorderLayout.CENTER);
		top.add(PanelParts.toggle(in.isSharing(), actions::toggleSharing), BorderLayout.EAST);
		you.addWide(top);
		addCard(column, you);

		List<Member> sameWorld = new ArrayList<>();
		List<Member> others = new ArrayList<>();
		List<Member> offline = new ArrayList<>();
		for (Member m : sorted(in.getMembers()))
		{
			if (in.getMe() != null && DropRecorder.sameRsn(m.getName(), in.getMe()))
			{
				continue;
			}
			if (m.getWorld() == null)
			{
				offline.add(m);
			}
			else if (matches(m, filter, in.getMyWorld()))
			{
				(m.getWorld().equals(in.getMyWorld()) ? sameWorld : others).add(m);
			}
		}
		if (!sameWorld.isEmpty())
		{
			section(column, "On your world · W" + in.getMyWorld());
			for (Member m : sameWorld)
			{
				column.add(Box.createVerticalStrut(4));
				column.add(row(m, true, actions));
			}
		}
		if (!others.isEmpty() && !filter.equals("On my world"))
		{
			section(column, "Other worlds");
			for (Member m : others)
			{
				column.add(Box.createVerticalStrut(4));
				column.add(row(m, false, actions));
			}
		}
		if (sameWorld.isEmpty() && (others.isEmpty() || filter.equals("On my world")))
		{
			Card card = new Card(null);
			card.add(small(online(in) <= 1 ? "No other clanmates are online." : "Nobody online matches that.", QUIET));
			addCard(column, card);
		}
		if (ui.isShowOffline() && !offline.isEmpty())
		{
			section(column, "Offline · " + offline.size());
			for (Member m : offline)
			{
				column.add(Box.createVerticalStrut(4));
				column.add(row(m, false, actions));
			}
		}
		column.add(Box.createVerticalStrut(PanelParts.GAP));
		JPanel note = PanelParts.column();
		note.add(small(in.isActivityUnsupported()
			? "Activities show once the clan website supports them. Worlds come from clan chat."
			: "Clanmates who turn sharing off show as Online with their world.", QUIET));
		note.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		column.add(note);
	}

	/** Online first, then by name. */
	static List<Member> sorted(List<Member> members)
	{
		List<Member> out = new ArrayList<>(members);
		out.sort((a, b) ->
		{
			if ((a.getWorld() == null) != (b.getWorld() == null))
			{
				return a.getWorld() == null ? 1 : -1;
			}
			return a.getName().toLowerCase(Locale.UK).compareTo(b.getName().toLowerCase(Locale.UK));
		});
		return out;
	}

	private static void section(JPanel column, String text)
	{
		column.add(Box.createVerticalStrut(PanelParts.GAP + 2));
		javax.swing.JLabel label = PanelParts.section(text);
		label.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		column.add(label);
	}

	/** Name and world on top, what they're doing underneath with its picture. Click for their profile. */
	private static Card row(Member m, boolean sameWorld, Actions actions)
	{
		boolean on = m.getWorld() != null;
		Card card = new Card(null);
		card.setBorder(new EmptyBorder(5, 8, 5, 8));
		JPanel top = new JPanel(new BorderLayout(5, 0));
		top.setOpaque(false);
		top.add(PanelParts.dotIcon(on ? GOOD : PanelParts.LINE), BorderLayout.WEST);
		top.add(PanelParts.bold(m.getName(), on ? TEXT : QUIET), BorderLayout.CENTER);
		if (on)
		{
			top.add(PanelParts.plain("W" + m.getWorld(), sameWorld ? GOOD : QUIET), BorderLayout.EAST);
		}
		card.addWide(top);
		JPanel under = new JPanel(new BorderLayout(5, 0));
		under.setOpaque(false);
		under.setBorder(new EmptyBorder(1, 11, 0, 0));
		Integer icon = ActivityTracker.icon(m.getActivity());
		if (icon != null)
		{
			under.add(PanelParts.itemBox(icon, 14), BorderLayout.WEST);
		}
		String what = !on ? "Offline" : m.getActivity() != null ? m.getActivity() : "Online";
		under.add(PanelParts.cut(what, on && m.getActivity() != null ? TEXT : QUIET), BorderLayout.CENTER);
		card.addWide(under);
		card.onClick(() -> actions.openProfile(m.getName()), "See " + m.getName() + "'s profile");
		card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		return card;
	}
}
