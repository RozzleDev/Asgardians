package com.asgardians;

import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.button;
import static com.asgardians.PanelParts.dotRow;
import static com.asgardians.PanelParts.heading;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import lombok.Value;

/** Builds the Groups tab: the member's own group, a form to start one, and the open groups. Swing thread only. */
final class GroupTabs
{
	private GroupTabs()
	{
	}

	/** What the tab needs. */
	@Value
	static class Input
	{
		/** Null until loaded. */
		ClanData.GroupList groups;
		List<ClanData.GroupActivity> activities;
		/** The website doesn't have groups yet. */
		boolean unsupported;
		/** A reason the tab can't load, such as not being logged in; null when it can. */
		String notConnected;
		String me;
		/** Online clan members: lowercase RSN to world. */
		Map<String, Integer> online;
		/** The member is in the group's RuneLite party. */
		boolean inParty;
		/** An action is being sent, so its buttons are disabled. */
		boolean busy;
		Instant fetchedAt;
		ZoneId zone;
	}

	/** What the buttons do. Swing thread. */
	interface Actions
	{
		void create(GroupForm.Values values);

		void join(ClanData.Group group);

		void leave(ClanData.Group group);

		void close(ClanData.Group group);

		void kick(ClanData.Group group, String rsn);

		void joinParty(ClanData.Group group);

		void refresh();
	}

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

	/**
	 * What the tab shows, for deciding whether a redraw is needed: everything except the exact fetch time (only its
	 * minute is shown) and clan members who aren't in any group (they hop worlds all the time).
	 */
	static Object redrawKey(Input in)
	{
		Map<String, Integer> online = new java.util.TreeMap<>();
		ClanData.GroupList list = in.getGroups();
		if (list != null)
		{
			for (ClanData.Group g : ClanView.orEmpty(list.getGroups()))
			{
				for (ClanData.GroupMember m : g != null ? ClanView.orEmpty(g.getMembers()) : List.<ClanData.GroupMember>of())
				{
					if (m != null && m.getRsn() != null)
					{
						String rsn = m.getRsn().toLowerCase(Locale.UK);
						online.put(rsn, in.getOnline().get(rsn));
					}
				}
			}
		}
		String minute = in.getFetchedAt() != null ? TIME.withZone(in.getZone()).format(in.getFetchedAt()) : null;
		return java.util.Arrays.asList(list, in.getActivities(), in.isUnsupported(), in.getNotConnected(), in.getMe(),
			online, in.isInParty(), in.isBusy(), minute);
	}

	static void draw(JPanel column, Input in, GroupForm form, Actions actions)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Groups");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		if (in.isUnsupported())
		{
			Card card = new Card("Groups");
			card.add(small("Find or start a group for raids, bosses and other group content. The clan website doesn't have groups yet, so check back soon.", QUIET));
			addCard(column, card);
			return;
		}
		ClanData.GroupList list = in.getGroups();
		if (list == null)
		{
			Card card = new Card("Groups");
			card.add(small("Loading…", QUIET));
			addCard(column, card);
			return;
		}
		ClanData.Group mine = list.mineGroup();
		if (mine != null)
		{
			addCard(column, mineCard(mine, in, actions));
		}
		else
		{
			form.setActivities(in.getActivities());
			form.setBusy(in.isBusy());
			Card card = new Card("Start a group");
			card.addWide(form);
			addCard(column, card);
		}

		List<ClanData.Group> others = new ArrayList<>();
		for (ClanData.Group g : ClanView.orEmpty(list.getGroups()))
		{
			if (g != null && g.getId() != null && (mine == null || !g.getId().equals(mine.getId())))
			{
				others.add(g);
			}
		}
		addCard(column, PanelParts.section("Open groups" + (others.isEmpty() ? "" : " (" + others.size() + ")")));
		if (others.isEmpty())
		{
			Card card = new Card(null);
			card.add(small(mine != null ? "No other groups right now." : "No open groups right now. Start one above.", QUIET));
			addCard(column, card);
		}
		for (ClanData.Group g : others)
		{
			addCard(column, groupCard(g, mine != null, in, actions));
		}

		JLabel updated = small(in.getFetchedAt() != null ? "Updated " + TIME.withZone(in.getZone()).format(in.getFetchedAt()) : "", QUIET);
		addCard(column, PanelParts.spread(updated, PanelParts.link("Refresh", actions::refresh)));
	}

	/** The member's own group, with members, the party, and leave, close and kick. */
	private static Card mineCard(ClanData.Group g, Input in, Actions actions)
	{
		boolean host = DropRecorder.sameRsn(g.getHost(), in.getMe());
		Card card = new Card("Your group: " + g.getTitle());
		card.add(small(details(g), g.isFull() ? GOOD : TEXT));
		if (g.getNote() != null && !g.getNote().trim().isEmpty())
		{
			card.add(small(g.getNote(), QUIET));
		}
		if (g.isFull())
		{
			card.add(PanelParts.status(GOOD, "Full: hop to " + Groups.world(g) + "."));
		}
		card.gap();
		card.add(heading("Members"));
		for (ClanData.GroupMember m : ClanView.orEmpty(g.getMembers()))
		{
			if (m == null || m.getRsn() == null)
			{
				continue;
			}
			Integer world = in.getOnline().get(m.getRsn().toLowerCase(Locale.UK));
			String name = m.getRsn() + (DropRecorder.sameRsn(m.getRsn(), g.getHost()) ? " ★" : "")
				+ (m.getKc() != null ? " · " + m.getKc() + " kc" : "");
			boolean canKick = host && !in.isBusy() && !DropRecorder.sameRsn(m.getRsn(), in.getMe());
			if (canKick)
			{
				JLabel who = PanelParts.status(world != null ? GOOD : QUIET, name);
				card.addWide(PanelParts.spread(who, PanelParts.link("Kick", () -> actions.kick(g, m.getRsn()))));
			}
			else
			{
				card.addWide(dotRow(world != null ? GOOD : QUIET, name, world != null ? "W" + world : "offline",
					world != null ? "Online on world " + world : "Offline"));
			}
		}
		if (g.getParty() != null && !g.getParty().trim().isEmpty())
		{
			card.gap();
			if (in.isInParty())
			{
				card.add(PanelParts.status(GOOD, "You're in the group's party: " + g.getParty().trim()));
			}
			else
			{
				card.add(small("Party: " + g.getParty().trim() + ", for sharing health, prayer and pings.", QUIET));
				card.addWide(PanelParts.spread(null, PanelParts.link("Join the party", () -> actions.joinParty(g))));
			}
		}
		card.gap();
		JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
		buttons.setOpaque(false);
		JButton leave = button("Leave", () -> actions.leave(g));
		leave.setEnabled(!in.isBusy());
		buttons.add(leave);
		if (host || Boolean.TRUE.equals(g.getCanClose()))
		{
			JButton close = button("Close", () -> actions.close(g));
			close.setEnabled(!in.isBusy());
			buttons.add(close);
		}
		card.addWide(buttons);
		if (host)
		{
			card.add(small("If you leave, the next player to have joined becomes host.", QUIET));
		}
		return card;
	}

	/** Another member's group, with a Join button. */
	private static Card groupCard(ClanData.Group g, boolean inAGroup, Input in, Actions actions)
	{
		Card card = new Card(g.getTitle());
		card.add(small((g.getHost() != null ? "Host: " + g.getHost() + " · " : "") + details(g), TEXT));
		if (g.getNote() != null && !g.getNote().trim().isEmpty())
		{
			card.add(small(g.getNote(), QUIET));
		}
		if (g.getParty() != null && !g.getParty().trim().isEmpty())
		{
			card.add(small("Party: " + g.getParty().trim(), QUIET));
		}
		List<String> names = new ArrayList<>();
		int online = 0;
		for (ClanData.GroupMember m : ClanView.orEmpty(g.getMembers()))
		{
			if (m != null && m.getRsn() != null)
			{
				names.add(m.getRsn());
				online += in.getOnline().containsKey(m.getRsn().toLowerCase(Locale.UK)) ? 1 : 0;
			}
		}
		if (!names.isEmpty())
		{
			card.add(small(String.join(", ", names) + " · " + online + " online", QUIET));
		}
		card.gap();
		JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
		buttons.setOpaque(false);
		JButton join = button(g.isFull() ? "Full" : inAGroup ? "Join (leaves yours)" : "Join", () -> actions.join(g));
		join.setEnabled(!g.isFull() && !in.isBusy());
		buttons.add(join);
		if (Boolean.TRUE.equals(g.getCanClose()))
		{
			JButton remove = button("Remove", () -> actions.close(g));
			remove.setEnabled(!in.isBusy());
			buttons.add(remove);
		}
		card.addWide(buttons);
		return card;
	}

	/** "3/5 · W416 · Split". */
	static String details(ClanData.Group g)
	{
		List<String> parts = new ArrayList<>();
		parts.add(g.seats());
		if (g.getWorld() != null)
		{
			parts.add("W" + g.getWorld());
		}
		if ("ffa".equalsIgnoreCase(g.getLoot()))
		{
			parts.add("FFA");
		}
		else if ("split".equalsIgnoreCase(g.getLoot()))
		{
			parts.add("Split");
		}
		return String.join(" · ", parts);
	}

	/**
	 * The "Start a group" form. Kept by the panel and put back on each redraw, so what the member has picked or typed
	 * survives the list refreshing every few seconds.
	 */
	static class GroupForm extends JPanel
	{
		/** More than eight: no seat limit. */
		static final String MASS = "8+";
		/** No preference on size: anyone can join, and the group never fills. */
		static final String ANY = "Any";
		static final int MAX_SEATS = 8;
		private static final int NOTE_LIMIT = 80;

		/** What the member picked. */
		@Value
		static class Values
		{
			ClanData.GroupActivity activity;
			String mode;
			/** Null for 8+ (mass) or Any. */
			Integer size;
			boolean mass;
			/** split or ffa; null when the activity has no choice. */
			String loot;
			String note;
		}

		private final JComboBox<String> activity = new JComboBox<>();
		private final JComboBox<String> mode = new JComboBox<>();
		private final JComboBox<String> size = new JComboBox<>();
		private final JCheckBox ffa = new JCheckBox("Free for all (FFA)");
		private final JTextField note = new JTextField();
		private final JLabel modeLabel = label("Mode");
		private final JButton create;
		private List<ClanData.GroupActivity> activities = List.of();
		private boolean filling;

		GroupForm(java.util.function.Consumer<Values> onCreate)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			ffa.setOpaque(false);
			ffa.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			ffa.setForeground(TEXT);
			ffa.setToolTipText("Unticked means Split");
			note.setToolTipText("Optional, e.g. learner friendly, gear or KC needed");
			add(label("Activity"));
			add(wide(activity));
			add(modeLabel);
			add(wide(mode));
			add(label("Size"));
			add(wide(size));
			add(wide(ffa));
			add(label("Note (optional)"));
			add(wide(note));
			add(Box.createVerticalStrut(6));
			create = button("Start group", () ->
			{
				Values v = values();
				if (v != null)
				{
					onCreate.accept(v);
				}
			});
			add(wide(create));
			JLabel hint = small("Your world is filled in from where you are. Anyone in the clan with the plugin can join.", QUIET);
			add(hint);
			activity.addActionListener(e ->
			{
				if (!filling)
				{
					fillChoices();
				}
			});
			setActivities(List.of());
		}

		void setActivities(List<ClanData.GroupActivity> list)
		{
			List<ClanData.GroupActivity> fresh = list != null ? list : List.of();
			if (sameIds(fresh, activities))
			{
				return;
			}
			String picked = (String) activity.getSelectedItem();
			activities = fresh;
			filling = true;
			activity.removeAllItems();
			for (ClanData.GroupActivity a : activities)
			{
				activity.addItem(a.getName() != null ? a.getName() : a.getId());
			}
			if (picked != null)
			{
				activity.setSelectedItem(picked);
			}
			filling = false;
			fillChoices();
		}

		/** A drop-down list is open, so the tab mustn't redraw under it. */
		boolean isPicking()
		{
			return activity.isPopupVisible() || mode.isPopupVisible() || size.isPopupVisible();
		}

		void setBusy(boolean busy)
		{
			create.setEnabled(!busy && !activities.isEmpty());
			create.setText(busy ? "Starting…" : activities.isEmpty() ? "Loading activities…" : "Start group");
		}

		/** Mode and size choices for the picked activity. */
		private void fillChoices()
		{
			ClanData.GroupActivity a = selected();
			mode.removeAllItems();
			size.removeAllItems();
			boolean hasModes = a != null && a.getModes() != null && !a.getModes().isEmpty();
			modeLabel.setVisible(hasModes);
			mode.setVisible(hasModes);
			if (hasModes)
			{
				a.getModes().forEach(mode::addItem);
			}
			// Any, 2 to 8, then 8+. A cap of 8 or less from the website (TFA: 8, Royal Titans: 2) removes 8+ unless the
			// activity is marked as a mass, and Any then fills at the cap.
			int min = a != null && a.getMinSize() != null ? Math.min(Math.max(a.getMinSize(), 2), MAX_SEATS) : 2;
			boolean capped = capped(a);
			int max = a != null && a.getMaxSize() != null && a.getMaxSize() <= MAX_SEATS ? Math.max(a.getMaxSize(), min) : MAX_SEATS;
			if (max > min)
			{
				size.addItem(ANY);
			}
			for (int i = min; i <= max; i++)
			{
				size.addItem(String.valueOf(i));
			}
			if (!capped)
			{
				size.addItem(MASS);
			}
			if (a != null && a.getDefaultSize() != null)
			{
				size.setSelectedItem(String.valueOf(a.getDefaultSize()));
			}
			ffa.setVisible(a != null && a.isLoot());
			ffa.setSelected(false);
			revalidate();
		}

		private ClanData.GroupActivity selected()
		{
			int i = activity.getSelectedIndex();
			return i >= 0 && i < activities.size() ? activities.get(i) : null;
		}

		/** The picked values, or null when no activity is picked. */
		Values values()
		{
			ClanData.GroupActivity a = selected();
			if (a == null)
			{
				return null;
			}
			String s = (String) size.getSelectedItem();
			boolean mass = MASS.equals(s);
			Integer seats = null;
			if (ANY.equals(s))
			{
				// No preference, but never past the content's cap.
				seats = capped(a) ? a.getMaxSize() : null;
			}
			else if (!mass && s != null)
			{
				seats = Integer.valueOf(s);
			}
			String text = note.getText().trim();
			if (text.length() > NOTE_LIMIT)
			{
				text = text.substring(0, NOTE_LIMIT);
			}
			return new Values(a, mode.isVisible() ? (String) mode.getSelectedItem() : null, seats, mass,
				a.isLoot() ? (ffa.isSelected() ? "ffa" : "split") : null, text.isEmpty() ? null : text);
		}

		/** The activity can't go past its maxSize: it has one of 8 or less, and isn't marked as a mass. */
		private static boolean capped(ClanData.GroupActivity a)
		{
			return a != null && !a.isMass() && a.getMaxSize() != null && a.getMaxSize() <= MAX_SEATS;
		}

		/** Empties the note after a group is started. */
		void reset()
		{
			note.setText("");
			ffa.setSelected(false);
		}

		private static boolean sameIds(List<ClanData.GroupActivity> a, List<ClanData.GroupActivity> b)
		{
			if (a.size() != b.size())
			{
				return false;
			}
			for (int i = 0; i < a.size(); i++)
			{
				if (!Objects.equals(a.get(i).getId(), b.get(i).getId()) || !Objects.equals(a.get(i).getName(), b.get(i).getName()))
				{
					return false;
				}
			}
			return true;
		}

		private static JLabel label(String text)
		{
			JLabel l = PanelParts.plain(text, QUIET);
			l.setBorder(new javax.swing.border.EmptyBorder(4, 0, 2, 0));
			l.setAlignmentX(LEFT_ALIGNMENT);
			return l;
		}

		private static javax.swing.JComponent wide(javax.swing.JComponent c)
		{
			c.setAlignmentX(LEFT_ALIGNMENT);
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
			return c;
		}
	}
}
