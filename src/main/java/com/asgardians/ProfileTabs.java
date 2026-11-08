package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOLD;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.TODO;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.client.ui.FontManager;

/**
 * A clan profile: who they are (rank, time in the clan, Discord), Clan credit, EHB and EHP, their clan rankings, events,
 * personal bests, collection log and pets, drops, deaths and handeggs. Used for the member's own profile and for
 * clanmates'. Sections the website doesn't send are left out. Swing thread.
 */
final class ProfileTabs
{
	@Value
	static class Input
	{
		String rsn;
		/** Null while loading, or when the website has no profile for them. */
		ClanData.Me me;
		/** The member's own profile. */
		boolean own;
		boolean loading;
		/** The website doesn't have clanmates' profiles yet. */
		boolean unsupported;
		/** Null when offline. */
		Integer world;
		String activity;
		/** Leaderboard places this month. */
		List<ClanData.ProfileRank> rankings;
		/** Their latest drops from the clan feed. */
		List<ClanData.StatEntry> recentDrops;
		Instant now;
	}

	interface Actions
	{
		/** Opens the Leaderboard on this board, this month. */
		void openBoard(String board);

		/** Puts the boss's icon on the label. */
		void bossIcon(JLabel label, String boss);
	}

	private ProfileTabs()
	{
	}

	static void draw(JPanel column, Input in, Actions actions)
	{
		ClanData.Me me = in.getMe();
		addCard(column, identity(in));
		if (me == null)
		{
			Card card = new Card(null);
			card.add(small(in.isLoading() ? "Loading their profile…"
				: in.isUnsupported() ? "Full profiles of clanmates aren't on the clan website yet."
				: "No clan profile yet.", QUIET));
			addCard(column, card);
			return;
		}

		// Discord
		Card discord = new Card(null);
		discord.setBorder(new EmptyBorder(6, 8, 6, 8));
		discord.addWide(PanelParts.spread(PanelParts.plain("Discord", QUIET),
			PanelParts.plain(me.isDiscordLinked() ? "Linked" : "Not linked", me.isDiscordLinked() ? GOOD : QUIET)));
		String help = in.isOwn() ? ProfileText.discordHelp(me) : null;
		if (help != null)
		{
			discord.add(small(help, TODO));
		}
		addCard(column, discord);

		// Clan credit, EHB, EHP
		JPanel boxes = new JPanel(new GridLayout(1, 3, 4, 0));
		boxes.setOpaque(false);
		boxes.add(statBox(me.getSplitCredit() != null ? ProfileText.amount(me.getSplitCredit()) : "–", "Clan credit", HIGHLIGHT));
		boxes.add(statBox(me.getEhb() != null ? whole(me.getEhb()) : "–", "EHB", HIGHLIGHT));
		boxes.add(statBox(me.getEhp() != null ? whole(me.getEhp()) : "–", "EHP", HIGHLIGHT));
		boxes.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, boxes.getPreferredSize().height));
		addCard(column, boxes);

		String next = in.isOwn() ? ProfileText.nextRank(me) : null;
		if (next != null || me.isHeld())
		{
			Card rank = new Card(null);
			if (next != null)
			{
				rank.add(small(next, TODO));
			}
			if (me.isHeld())
			{
				rank.add(PanelParts.status(BAD, "Your account is held for staff review."));
			}
			addCard(column, rank);
		}

		// Clan rankings
		List<ClanData.ProfileRank> ranks = in.getRankings() != null && !in.getRankings().isEmpty() ? in.getRankings() : me.getRankings();
		if (ranks != null && !ranks.isEmpty())
		{
			section(column, "Clan rankings · this month");
			for (ClanData.ProfileRank r : ranks)
			{
				if (r == null || r.getBoard() == null)
				{
					continue;
				}
				Card row = new Card(null);
				row.setBorder(new EmptyBorder(4, 8, 4, 8));
				JPanel line = new JPanel(new BorderLayout(6, 0));
				line.setOpaque(false);
				line.add(PanelParts.cut(r.getBoard(), TEXT), BorderLayout.CENTER);
				JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
				right.setOpaque(false);
				if (r.getRank() != null)
				{
					right.add(PanelParts.bold("#" + r.getRank(), r.getRank() == 1 ? GOLD : HIGHLIGHT));
				}
				if (r.getValue() != null)
				{
					right.add(PanelParts.plain(r.getValue(), QUIET));
				}
				line.add(right, BorderLayout.EAST);
				row.addWide(line);
				row.onClick(() -> actions.openBoard(r.getBoard()), "Open the " + r.getBoard() + " board");
				gap(column, 4);
				row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				column.add(row);
			}
		}

		// Events
		if (me.getEvents() != null && !me.getEvents().isEmpty())
		{
			section(column, "Events");
			for (ClanData.EventResult e : me.getEvents())
			{
				if (e == null || e.getName() == null)
				{
					continue;
				}
				Card card = new Card(null);
				card.setBorder(new EmptyBorder(5, 8, 5, 8));
				card.add(PanelParts.bold(e.getName(), TEXT));
				if (e.getResult() != null)
				{
					card.add(PanelParts.plain(e.getResult(), e.isWon() ? GOLD : QUIET));
				}
				gap(column, 4);
				card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				column.add(card);
			}
		}

		// Personal bests
		if (me.getPersonalBests() != null && !me.getPersonalBests().isEmpty())
		{
			section(column, "Personal bests");
			Card card = new Card(null);
			card.setBorder(new EmptyBorder(5, 8, 5, 8));
			int shown = 0;
			for (ClanData.StatEntry pb : me.getPersonalBests())
			{
				if (pb == null || pb.getBoss() == null || shown++ >= 8)
				{
					continue;
				}
				JPanel line = new JPanel(new BorderLayout(6, 0));
				line.setOpaque(false);
				line.setBorder(new EmptyBorder(1, 0, 1, 0));
				JLabel icon = new JLabel();
				icon.setPreferredSize(new java.awt.Dimension(20, 20));
				actions.bossIcon(icon, pb.getBoss());
				line.add(icon, BorderLayout.WEST);
				line.add(PanelParts.cut(StatsTabs.name(pb, StatsTabs.PBS), TEXT), BorderLayout.CENTER);
				line.add(PanelParts.plain(StatsTabs.right(pb, StatsTabs.PBS), HIGHLIGHT), BorderLayout.EAST);
				card.addWide(line);
			}
			addCard(column, card);
		}

		// Collection log and pets
		if (me.getClogObtained() != null || (me.getPets() != null && !me.getPets().isEmpty()))
		{
			section(column, "Collection log");
			Card card = new Card(null);
			if (me.getClogObtained() != null)
			{
				if (me.getClogTotal() != null && me.getClogTotal() > 0)
				{
					card.addWide(PanelParts.progress(me.getClogObtained(), me.getClogTotal(),
						String.format("%,d / %,d", me.getClogObtained(), me.getClogTotal())));
				}
				else
				{
					card.add(PanelParts.plain(String.format("%,d slots", me.getClogObtained()), TEXT));
				}
			}
			if (me.getPets() != null && !me.getPets().isEmpty())
			{
				card.gap();
				JPanel pets = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 0));
				pets.setOpaque(false);
				pets.add(PanelParts.plain("Pets", QUIET));
				for (ClanData.ProfileItem pet : me.getPets().subList(0, Math.min(8, me.getPets().size())))
				{
					JLabel box = PanelParts.itemBox(pet.getItemId(), 20);
					box.setToolTipText(pet.getName());
					pets.add(box);
				}
				card.addWide(pets);
			}
			addCard(column, card);
		}

		// Drops record
		if (me.getDrops() != null || me.getDropValue() != null)
		{
			section(column, "Drops record");
			JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
			row.setOpaque(false);
			row.add(statBox(me.getDrops() != null ? String.format("%,d", me.getDrops()) : "–", "Logged", TEXT));
			row.add(statBox(me.getDropValue() != null ? ProfileText.amount(me.getDropValue()) : "–", "Total value", GOLD));
			row.add(statBox(me.getPets() != null ? String.valueOf(me.getPets().size()) : "–", "Pets", TEXT));
			row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			gap(column, 4);
			row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			column.add(row);
		}

		// Deaths
		if (me.getDeaths() != null)
		{
			section(column, "Deaths");
			Card card = new Card(null);
			JPanel top = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
			top.setOpaque(false);
			top.add(PanelParts.bold(String.format("%,d", me.getDeaths()), BAD));
			top.add(PanelParts.plain(" deaths", QUIET));
			card.addWide(top);
			if (me.getLastDeathBy() != null)
			{
				String ago = me.lastDeath() != null ? " · " + ClanView.ago(me.lastDeath(), in.getNow()) : "";
				card.add(PanelParts.plain("Last: " + me.getLastDeathBy() + ago, TEXT));
			}
			if (me.getTopKiller() != null)
			{
				card.add(PanelParts.plain("Top killer: " + me.getTopKiller(), QUIET));
			}
			addCard(column, card);
		}

		// Handeggs
		if (me.getEggsThrown() != null || me.getEggsReceived() != null)
		{
			section(column, "Handeggs");
			Card card = new Card(null);
			card.setBorder(new EmptyBorder(6, 8, 6, 8));
			card.addWide(PanelParts.spread(
				PanelParts.plain("Egged " + (me.getEggsThrown() != null ? me.getEggsThrown() : 0), TEXT),
				PanelParts.plain("Got egged " + (me.getEggsReceived() != null ? me.getEggsReceived() : 0), QUIET)));
			addCard(column, card);
		}

		// Recent drops
		if (in.getRecentDrops() != null && !in.getRecentDrops().isEmpty())
		{
			section(column, "Recent drops");
			for (ClanData.StatEntry e : in.getRecentDrops().subList(0, Math.min(5, in.getRecentDrops().size())))
			{
				Card card = new Card(null);
				card.setBorder(new EmptyBorder(5, 6, 5, 8));
				JPanel line = new JPanel(new BorderLayout(6, 0));
				line.setOpaque(false);
				line.add(PanelParts.itemBox(e.getItemId(), 28), BorderLayout.WEST);
				JPanel words = new JPanel();
				words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
				words.setOpaque(false);
				JPanel top = new JPanel(new BorderLayout(4, 0));
				top.setOpaque(false);
				top.add(PanelParts.cut(StatsTabs.name(e, StatsTabs.DROPS), TEXT), BorderLayout.CENTER);
				String value = StatsTabs.right(e, StatsTabs.DROPS);
				if (!value.isEmpty())
				{
					top.add(PanelParts.plain(value, GOLD), BorderLayout.EAST);
				}
				top.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				words.add(top);
				JLabel when = PanelParts.cut((e.getSource() != null ? e.getSource() + " · " : "") + ClanView.ago(e.time(), in.getNow()), QUIET);
				when.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				words.add(when);
				line.add(words, BorderLayout.CENTER);
				card.addWide(line);
				gap(column, 4);
				card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				column.add(card);
			}
		}
	}

	/** Picture, name and rank, where they are, and how long they've been in the clan. */
	private static Card identity(Input in)
	{
		ClanData.Me me = in.getMe();
		Card card = new Card(null).outlined(in.isOwn() ? new Color(0x6E, 0x37, 0x15) : PanelParts.LINE, PanelParts.CARD);
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		Integer picture = ActivityTracker.icon(in.getActivity());
		if (picture == null && me != null && me.getPets() != null && !me.getPets().isEmpty())
		{
			picture = me.getPets().get(0).getItemId();
		}
		JLabel avatar = PanelParts.itemBox(picture, 40);
		if (picture == null)
		{
			avatar.setIcon(new PersonIcon());
		}
		row.add(avatar, BorderLayout.WEST);
		JPanel words = new JPanel();
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		words.setOpaque(false);
		JPanel name = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		name.setOpaque(false);
		name.add(PanelParts.bold(in.getRsn(), TEXT));
		if (me != null && me.getRank() != null)
		{
			name.add(Box.createHorizontalStrut(5));
			name.add(PanelParts.tag(ProfileText.rank(me.getRank()), GOLD));
		}
		name.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		words.add(name);
		JPanel status = new JPanel(new BorderLayout(4, 0));
		status.setOpaque(false);
		boolean online = in.getWorld() != null;
		status.add(PanelParts.dotIcon(online ? GOOD : PanelParts.LINE), BorderLayout.WEST);
		String where = online ? "W" + in.getWorld() + (in.getActivity() != null ? " · " + in.getActivity() : "") : "Offline";
		status.add(PanelParts.cut(where, online ? TEXT : QUIET), BorderLayout.CENTER);
		status.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		words.add(status);
		if (me != null && me.getDaysInClan() != null)
		{
			JLabel time = PanelParts.cut("In clan " + ProfileText.time(me.getDaysInClan()), QUIET);
			time.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			words.add(time);
		}
		row.add(words, BorderLayout.CENTER);
		card.addWide(row);
		return card;
	}

	/** A number with its name under it, on the panel's background. */
	static JPanel statBox(String value, String label, Color colour)
	{
		JPanel box = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				PanelParts.roundBox(g, getWidth(), getHeight(), PanelParts.CARD, null);
				super.paintComponent(g);
			}
		};
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setOpaque(false);
		box.setBorder(new EmptyBorder(6, 2, 6, 2));
		JLabel v = new JLabel(value, JLabel.CENTER);
		v.setFont(FontManager.getRunescapeBoldFont());
		v.setForeground(colour);
		v.setAlignmentX(0.5f);
		JLabel l = new JLabel(label, JLabel.CENTER);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(QUIET);
		l.setAlignmentX(0.5f);
		box.add(v);
		box.add(l);
		return box;
	}

	private static String whole(double v)
	{
		return String.format("%,d", Math.round(v));
	}

	private static void section(JPanel column, String text)
	{
		column.add(Box.createVerticalStrut(PanelParts.GAP + 2));
		JLabel label = PanelParts.section(text);
		label.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		column.add(label);
	}

	private static void gap(JPanel column, int h)
	{
		column.add(Box.createVerticalStrut(h));
	}

	/** A head and shoulders in orange, for someone with no picture. */
	private static class PersonIcon implements javax.swing.Icon
	{
		@Override
		public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y)
		{
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(HIGHLIGHT);
			g2.setStroke(new java.awt.BasicStroke(2f));
			g2.drawOval(x + 7, y + 1, 10, 10);
			g2.drawArc(x + 1, y + 13, 22, 20, 0, 180);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 24;
		}

		@Override
		public int getIconHeight()
		{
			return 24;
		}
	}
}
