package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import lombok.Value;

/**
 * The Groups page's "Party" tab: the member's RuneLite party (the one RuneLite's Party plugin uses), who's in it and a
 * ready check. Clan groups put members in their group's party automatically; this tab also lets them start, join or
 * leave one. Swing thread.
 */
final class PartyTabs
{
	@Value
	static class Member
	{
		String name;
		boolean me;
		boolean loggedIn;
		/** In the Asgardians clan. */
		boolean clanmate;
		/** Current / base levels; 0 when the Party plugin hasn't shared them. */
		int hp;
		int hpMax;
		int prayer;
		int prayerMax;
	}

	@Value
	static class Input
	{
		boolean inParty;
		String passphrase;
		/** The clan group this party belongs to, e.g. "ToB · 4-man", or null. */
		String group;
		List<Member> members;
		/** Null when no ready check is on. */
		ReadyCheck.View ready;
	}

	interface Actions
	{
		void startParty();

		void joinParty(String passphrase);

		void leaveParty();

		void copyPassphrase();

		void readyCheck();

		void answer(boolean ready);
	}

	private PartyTabs()
	{
	}

	/** "ASG-123 · 4 in party". */
	static String subtitle(Input in)
	{
		int n = in.getMembers().size();
		return (in.getPassphrase() != null ? in.getPassphrase() + " · " : "") + n + (n == 1 ? " member" : " members");
	}

	/** A new party's name: "ASG-" and three random digits, short enough to read out in voice. */
	static String newPartyName(java.util.Random random)
	{
		return "ASG-" + (100 + random.nextInt(900));
	}

	/** What's shown, for skipping redraws that change nothing (the page is checked every second or two). */
	static Object redrawKey(Input in)
	{
		return in;
	}

	static void draw(JPanel column, Input in, Actions actions)
	{
		column.removeAll();
		if (!in.isInParty())
		{
			drawNoParty(column, actions);
			return;
		}

		Card top = new Card(null);
		top.addWide(PanelParts.spread(PanelParts.bold("Party " + (in.getPassphrase() != null ? in.getPassphrase() : ""), TEXT),
			PanelParts.link("Copy", actions::copyPassphrase)));
		top.add(small(in.getGroup() != null ? "Your LFG group's party: " + in.getGroup()
			: "Share the name so others can join with RuneLite's Party plugin or the Party page.", QUIET));
		PanelParts.addCard(column, top);

		PanelParts.addCard(column, readyCard(in, actions));

		List<Member> members = in.getMembers();
		PanelParts.addCard(column, PanelParts.section("Members (" + members.size() + ")"));
		boolean stats = false;
		for (Member m : members)
		{
			PanelParts.addCard(column, memberRow(m, in.getReady()));
			stats |= m.getHpMax() > 0;
		}
		if (!stats && members.size() > 1)
		{
			PanelParts.addCard(column, small("Turn on RuneLite's Party plugin to see everyone's HP and prayer here.", QUIET));
		}
		PanelParts.addCard(column, PanelParts.spread(PanelParts.plain(" ", QUIET), PanelParts.link("Leave party", actions::leaveParty)));
	}

	/** Not in a party: what one is for, and start or join one. */
	private static void drawNoParty(JPanel column, Actions actions)
	{
		Card card = new Card("RuneLite party");
		card.add(small("A party shares HP, prayer and tile pings with your team through RuneLite's Party plugin, and "
			+ "lets you run a ready check. Joining an LFG group puts you in its party for you. New parties are "
			+ "named like ASG-123: tell your team the name so they can join.", QUIET));
		card.gap();
		card.addWide(PanelParts.button("Start a party", true, actions::startParty));
		card.gap();
		JTextField name = new JTextField();
		name.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		name.setForeground(TEXT);
		name.setBackground(PanelParts.BACKGROUND);
		name.setCaretColor(TEXT);
		name.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(PanelParts.LINE),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));
		name.setToolTipText("The party's name, e.g. ASG-123");
		Runnable join = () ->
		{
			String pass = name.getText().trim();
			if (!pass.isEmpty())
			{
				actions.joinParty(pass);
			}
		};
		name.addActionListener(e -> join.run());
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(name, BorderLayout.CENTER);
		row.add(PanelParts.button("Join", join), BorderLayout.EAST);
		card.add(small("Or join one by its name:", TEXT));
		card.addWide(row);
		PanelParts.addCard(column, card);
	}

	/** Start a check; while one is on, the count, time left and the member's own Ready / Not ready buttons. */
	static Card readyCard(Input in, Actions actions)
	{
		ReadyCheck.View v = in.getReady();
		Card card = new Card("Ready check");
		if (v == null)
		{
			card.add(small("Everyone in the party hears a bell and gets a box on screen. Ready up here, right-click the chat message or type !asg r (!asg nr for not ready).", QUIET));
			card.gap();
			card.addWide(PanelParts.button("Start a ready check", true, actions::readyCheck));
			return card;
		}
		if (v.isOver())
		{
			card.add(small(v.summary(), v.getReady() == v.getLines().size() ? GOOD : BAD));
			card.gap();
			card.addWide(PanelParts.button("Start another", actions::readyCheck));
			return card;
		}
		card.add(small((v.getStartedBy() != null ? "Started by " + v.getStartedBy() + " · " : "") + v.getSecondsLeft() + "s left", QUIET));
		card.addWide(PanelParts.progress(v.getReady(), Math.max(1, v.getLines().size()), v.summary()));
		card.gap();
		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		buttons.setOpaque(false);
		buttons.add(PanelParts.button("Ready", v.getMine() != ReadyCheck.Answer.NOT_READY, () -> actions.answer(true)));
		buttons.add(PanelParts.button("Not ready", v.getMine() == ReadyCheck.Answer.NOT_READY, () -> actions.answer(false)));
		card.addWide(buttons);
		return card;
	}

	private static JPanel memberRow(Member m, ReadyCheck.View ready)
	{
		Card card = new Card(null);
		card.setBorder(new javax.swing.border.EmptyBorder(5, 8, 5, 8));
		JPanel top = new JPanel(new BorderLayout(5, 0));
		top.setOpaque(false);
		top.add(PanelParts.dotIcon(m.isLoggedIn() ? GOOD : QUIET), BorderLayout.WEST);
		JPanel name = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		name.setOpaque(false);
		name.add(PanelParts.bold(m.getName() + (m.isMe() ? " (you)" : ""), m.isLoggedIn() ? TEXT : QUIET));
		if (m.isClanmate())
		{
			name.add(PanelParts.tag("ASG", HIGHLIGHT));
		}
		top.add(name, BorderLayout.CENTER);
		ReadyCheck.Answer answer = answerFor(m, ready);
		if (answer != null)
		{
			top.add(PanelParts.plain(answer == ReadyCheck.Answer.READY ? "Ready" : answer == ReadyCheck.Answer.NOT_READY
				? "Not ready" : "...", colour(answer)), BorderLayout.EAST);
		}
		card.addWide(top);
		if (m.getHpMax() > 0)
		{
			card.addWide(PanelParts.plain("HP " + m.getHp() + "/" + m.getHpMax() + "  ·  Prayer " + m.getPrayer() + "/"
				+ m.getPrayerMax(), m.getHp() * 3 < m.getHpMax() ? BAD : QUIET));
		}
		else if (!m.isLoggedIn())
		{
			card.addWide(PanelParts.plain("Logged out", QUIET));
		}
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private static ReadyCheck.Answer answerFor(Member m, ReadyCheck.View ready)
	{
		if (ready == null)
		{
			return null;
		}
		for (ReadyCheck.Line l : ready.getLines())
		{
			if (l.getName().equals(m.getName()))
			{
				return l.getAnswer();
			}
		}
		return null;
	}

	static Color colour(ReadyCheck.Answer a)
	{
		return a == ReadyCheck.Answer.READY ? GOOD : a == ReadyCheck.Answer.NOT_READY ? BAD : QUIET;
	}
}
