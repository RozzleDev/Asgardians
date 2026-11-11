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
		/** The "Group alerts" card; null leaves it out. */
		Bells bells;
	}

	/** Which activities ring for new groups, and whether the card is open. */
	@Value
	static class Bells
	{
		/** The member's own choices, by activity ID. */
		Map<String, Boolean> choices;
		/** The "New groups in chat" setting. */
		boolean alertsOn;
		boolean open;
		/** The member's screen icons, by activity ID. */
		Map<String, Boolean> screens;
		/** The "New groups on screen" setting. */
		boolean screenOn;
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

		/** Opens the group's Discord voice channel. */
		default void joinVoice(ClanData.Group group)
		{
		}

		/** The host lets a waiting player into the group. */
		default void accept(ClanData.Group group, String rsn)
		{
		}

		/** The host turns a waiting player away. */
		default void decline(ClanData.Group group, String rsn)
		{
		}

		/** The member takes back their request to join. */
		default void cancel(ClanData.Group group)
		{
		}

		/** The member rang or muted the bell for an activity. */
		default void bell(String activityId, boolean on)
		{
		}

		/** The member turned the on-screen alert for an activity on or off. */
		default void screen(String activityId, boolean on)
		{
		}

		/** The "Group alerts" card was folded or opened. */
		default void foldBells()
		{
		}
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
			online, in.isInParty(), in.isBusy(), minute, in.getBells());
	}

	static void draw(JPanel column, Input in, GroupForm form, Actions actions)
	{
		ClanData.GroupList shown = in.getNotConnected() == null && !in.isUnsupported() ? in.getGroups() : null;
		boolean formShown = shown != null && shown.mineGroup() == null;
		Card formCard = form.card();
		if (formShown && column.getComponentCount() > 0 && column.getComponent(0) == formCard)
		{
			// The form stays where it is and only the cards below it are rebuilt, so a member typing in it or
			// picking from it carries on undisturbed.
			while (column.getComponentCount() > 1)
			{
				column.remove(column.getComponentCount() - 1);
			}
		}
		else
		{
			column.removeAll();
		}
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
			form.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, form.getPreferredSize().height));
			if (column.getComponentCount() == 0)
			{
				addCard(column, formCard);
			}
		}

		List<ClanData.Group> others = new ArrayList<>();
		for (ClanData.Group g : ClanView.orEmpty(list.getGroups()))
		{
			if (g != null && g.getId() != null && (mine == null || !g.getId().equals(mine.getId())))
			{
				// The group the member is waiting to get into goes first.
				if (g.getId().equals(list.getWaiting()))
				{
					others.add(0, g);
				}
				else
				{
					others.add(g);
				}
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
			addCard(column, groupCard(g, mine != null, g.getId().equals(list.getWaiting()), in, actions));
		}

		if (in.getBells() != null && !ClanView.orEmpty(in.getActivities()).isEmpty())
		{
			addCard(column, bellsCard(in.getActivities(), in.getBells(), actions));
		}

		JLabel updated = small(in.getFetchedAt() != null ? "Updated " + TIME.withZone(in.getZone()).format(in.getFetchedAt()) : "", QUIET);
		addCard(column, PanelParts.spread(updated, PanelParts.link("Refresh", actions::refresh)));
	}

	/**
	 * Every activity with a bell: ringing ones are announced in chat when a clanmate starts a group. Clan staff can
	 * send some to everyone, and those bells can't be muted.
	 */
	static Card bellsCard(List<ClanData.GroupActivity> activities, Bells bells, Actions actions)
	{
		Card card = new Card(null);
		int ringing = 0;
		for (ClanData.GroupActivity a : activities)
		{
			if (a != null && GroupBells.rings(a, bells.getChoices(), bells.isAlertsOn()))
			{
				ringing++;
			}
		}
		card.addWide(PanelParts.foldHeader("Group alerts", bells.isOpen(), bells.isOpen() ? null : ringing + " on", actions::foldBells));
		if (!bells.isOpen())
		{
			return card;
		}
		card.add(Box.createVerticalStrut(4));
		card.add(small(bells.isAlertsOn()
			? "Ring a bell to get a chat message when a clan member starts a group for it."
			: "\"New groups in chat\" is off in the plugin settings, so only the gold bells (sent to everyone by clan staff) ring.", QUIET));
		card.add(small(bells.isScreenOn()
			? "The screen icon also shows it on screen for a few seconds."
			: "\"New groups on screen\" is off in the plugin settings, so nothing shows on screen.", QUIET));
		card.gap();
		for (ClanData.GroupActivity a : activities)
		{
			if (a == null || a.getId() == null)
			{
				continue;
			}
			boolean locked = GroupBells.ALWAYS.equals(GroupBells.mode(a));
			boolean on = GroupBells.bell(a, bells.getChoices());
			boolean rings = GroupBells.rings(a, bells.getChoices(), bells.isAlertsOn());
			JLabel name = PanelParts.plain(a.getName() != null ? a.getName() : a.getId(), rings ? TEXT : QUIET);
			JButton bell = new JButton(new GroupBells.BellIcon(on, locked));
			bell.setFocusable(false);
			bell.setBorderPainted(false);
			bell.setContentAreaFilled(false);
			bell.setMargin(new java.awt.Insets(0, 0, 0, 0));
			bell.setPreferredSize(new Dimension(22, 18));
			if (locked)
			{
				bell.setToolTipText("Clan staff send these to every member");
				bell.setEnabled(true);
			}
			else
			{
				bell.setToolTipText(on ? "Ringing: click to mute" : "Muted: click to ring");
				bell.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
				bell.addActionListener(e -> actions.bell(a.getId(), !on));
			}
			bell.getAccessibleContext().setAccessibleName((a.getName() != null ? a.getName() : a.getId()) + " alerts "
				+ (locked ? "always on" : on ? "on" : "off"));

			boolean usable = bells.isScreenOn() && rings;
			boolean screenOn = GroupBells.screen(a, bells.getScreens());
			JButton screen = new JButton(new GroupBells.ScreenIcon(screenOn, usable));
			screen.setFocusable(false);
			screen.setBorderPainted(false);
			screen.setContentAreaFilled(false);
			screen.setMargin(new java.awt.Insets(0, 0, 0, 0));
			screen.setPreferredSize(new Dimension(22, 18));
			screen.getAccessibleContext().setAccessibleName((a.getName() != null ? a.getName() : a.getId()) + " on screen "
				+ (screenOn ? "on" : "off"));
			if (!usable)
			{
				screen.setToolTipText(!bells.isScreenOn() ? "\"New groups on screen\" is off in the plugin settings"
					: "Ring the bell to show these on screen");
			}
			else
			{
				screen.setToolTipText(screenOn ? "Shown on screen: click to stop" : "Not shown on screen: click to show");
				screen.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
				screen.addActionListener(e -> actions.screen(a.getId(), !screenOn));
			}
			JPanel icons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
			icons.setOpaque(false);
			icons.add(screen);
			icons.add(bell);
			card.addWide(PanelParts.spread(name, icons));
		}
		return card;
	}

	/** The member's own group, with members, the party, and leave, close and kick. */
	private static Card mineCard(ClanData.Group g, Input in, Actions actions)
	{
		boolean host = DropRecorder.sameRsn(g.getHost(), in.getMe());
		Card card = new Card(null).outlined(PanelParts.HIGHLIGHT, PanelParts.CARD);
		card.add(PanelParts.section("Your group"));
		card.add(Box.createVerticalStrut(3));
		card.addWide(header(g, in));
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
		List<ClanData.GroupMember> asking = new ArrayList<>();
		for (ClanData.GroupMember m : ClanView.orEmpty(g.getRequests()))
		{
			if (m != null && m.getRsn() != null && !g.has(m.getRsn()))
			{
				asking.add(m);
			}
		}
		if (!asking.isEmpty() && host)
		{
			card.gap();
			card.add(heading("Asking to join (" + asking.size() + ")"));
			for (ClanData.GroupMember m : asking)
			{
				Integer world = in.getOnline().get(m.getRsn().toLowerCase(Locale.UK));
				JLabel who = PanelParts.status(world != null ? GOOD : QUIET, m.getRsn() + (m.getKc() != null ? " · " + m.getKc() + " kc" : ""));
				who.setToolTipText(world != null ? "Online on world " + world : "Offline");
				JPanel links = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
				links.setOpaque(false);
				if (!in.isBusy())
				{
					if (!g.isFull())
					{
						links.add(PanelParts.link("Accept", () -> actions.accept(g, m.getRsn())));
					}
					links.add(PanelParts.link("Decline", () -> actions.decline(g, m.getRsn())));
				}
				card.addWide(PanelParts.spread(who, links));
			}
			if (g.isFull())
			{
				card.add(small("The group is full, so no one else can be accepted.", QUIET));
			}
		}
		else if (!asking.isEmpty())
		{
			card.add(small(asking.size() + (asking.size() == 1 ? " player is" : " players are") + " asking to join. The host picks.", QUIET));
		}
		if (Groups.voiceLink(g) != null)
		{
			card.gap();
			card.addWide(PanelParts.spread(small("Discord voice channel", QUIET), PanelParts.link("Join voice", () -> actions.joinVoice(g))));
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

	/**
	 * Another member's group, with a Join button, or Ask to join when the host picks who joins.
	 *
	 * @param waiting the member has asked to join this group and is waiting for the host
	 */
	private static Card groupCard(ClanData.Group g, boolean inAGroup, boolean waiting, Input in, Actions actions)
	{
		Card card = waiting ? new Card(null).outlined(PanelParts.HIGHLIGHT, PanelParts.CARD) : new Card(null);
		card.addWide(header(g, in));
		if (g.getNote() != null && !g.getNote().trim().isEmpty())
		{
			card.add(small(g.getNote(), TEXT));
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
		if (waiting)
		{
			card.add(PanelParts.status(PanelParts.HIGHLIGHT, "Asked to join: waiting for " + (g.getHost() != null ? g.getHost() : "the host") + "."));
		}
		else if (g.isApproval())
		{
			card.add(small("The host accepts who joins.", QUIET));
		}
		card.gap();
		JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
		buttons.setOpaque(false);
		JButton join;
		if (waiting)
		{
			join = button("Take back request", () -> actions.cancel(g));
			join.setEnabled(!in.isBusy());
		}
		else
		{
			// Asking doesn't take the member out of their own group: that only happens once the host accepts them.
			String label = g.isFull() ? "Full" : g.isApproval() ? "Ask to join" : inAGroup ? "Join (leaves yours)" : "Join";
			join = button(label, () -> actions.join(g));
			join.setEnabled(!g.isFull() && !in.isBusy());
			if (g.isApproval() && inAGroup)
			{
				join.setToolTipText("You stay in your group until the host accepts you");
			}
		}
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

	/**
	 * A group's top: the activity's picture, its title with a seats badge, then the host, world (green when it's the
	 * member's own) and loot rules.
	 */
	static JPanel header(ClanData.Group g, Input in)
	{
		JPanel row = new JPanel(new java.awt.BorderLayout(7, 0));
		row.setOpaque(false);
		ClanData.GroupActivity activity = activity(g, in.getActivities());
		row.add(PanelParts.itemBox(activity != null ? activity.getIconItemId() : null, 32), java.awt.BorderLayout.WEST);
		JPanel words = PanelParts.column();
		JPanel title = new JPanel(new java.awt.BorderLayout(5, 0));
		title.setOpaque(false);
		title.add(PanelParts.bold(Groups.shortTitle(g, activity), TEXT), java.awt.BorderLayout.CENTER);
		int have = ClanView.orEmpty(g.getMembers()).size();
		boolean nearlyFull = g.getSize() != null && have >= g.getSize() - 1;
		title.add(PanelParts.tag(g.seats(), g.isFull() ? QUIET : nearlyFull ? PanelParts.HIGHLIGHT : GOOD), java.awt.BorderLayout.EAST);
		title.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		words.add(title);
		Integer mine = in.getMe() != null ? in.getOnline().get(in.getMe().toLowerCase(Locale.UK)) : null;
		JPanel line = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		line.setOpaque(false);
		if (g.getHost() != null)
		{
			line.add(PanelParts.plain(g.getHost() + " · ", QUIET));
		}
		if (g.getWorld() != null)
		{
			line.add(PanelParts.plain("W" + g.getWorld(), g.getWorld().equals(mine) ? GOOD : QUIET));
		}
		String loot = "ffa".equalsIgnoreCase(g.getLoot()) ? " · FFA" : "split".equalsIgnoreCase(g.getLoot()) ? " · Split" : "";
		if (!loot.isEmpty())
		{
			line.add(PanelParts.plain(loot, QUIET));
		}
		line.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		words.add(line);
		row.add(words, java.awt.BorderLayout.CENTER);
		return row;
	}

	/** The activity a group is for, from the website's list. */
	static ClanData.GroupActivity activity(ClanData.Group g, List<ClanData.GroupActivity> activities)
	{
		for (ClanData.GroupActivity a : ClanView.orEmpty(activities))
		{
			if (a != null && a.getId() != null && a.getId().equals(g.getActivity()))
			{
				return a;
			}
		}
		return null;
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
			/** Ask the website to make a Discord voice channel for the group. */
			boolean voice;
			/** Lock the voice channel to the group's size. Only matters with voice on and a set size. */
			boolean voiceLimit;
			/** The host picks who joins: others ask, and the host accepts or declines. */
			boolean approval;
		}

		/** The "Start a group" card holding this form, kept so redraws can leave it in place. */
		private Card card;
		private final JComboBox<String> activity = new JComboBox<>();
		private final JComboBox<String> mode = new JComboBox<>();
		private final JComboBox<String> size = new JComboBox<>();
		private final JCheckBox ffa = new JCheckBox("Free for all (FFA)");
		private final JCheckBox voice = new JCheckBox("Discord voice channel", true);
		private final JCheckBox voiceLimit = new JCheckBox("Lock to group size", true);
		private final JCheckBox approval = new JCheckBox("I accept who joins", true);
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
			voice.setOpaque(false);
			voice.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			voice.setForeground(TEXT);
			voice.setToolTipText("Make a voice channel for the group in the clan Discord. It goes when the group closes.");
			voiceLimit.setOpaque(false);
			voiceLimit.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			voiceLimit.setForeground(TEXT);
			voiceLimit.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 16, 0, 0));
			voiceLimit.setToolTipText("Only as many people as the group's size can be in the voice channel. Unticked, anyone can join it.");
			voice.addItemListener(e -> voiceLimit.setEnabled(voice.isSelected()));
			approval.setOpaque(false);
			approval.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			approval.setForeground(TEXT);
			approval.setToolTipText("Players ask to join and wait in a lobby until you accept them. Unticked, anyone can join straight away.");
			note.setToolTipText("Optional, e.g. learner friendly, gear or KC needed");
			add(label("Activity"));
			add(wide(activity));
			add(modeLabel);
			add(wide(mode));
			add(label("Size"));
			add(wide(size));
			add(wide(ffa));
			add(wide(approval));
			add(wide(voice));
			add(wide(voiceLimit));
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
			JLabel hint = small("Your world is filled in from where you are. Clan members with the plugin can ask to join, or join straight away with \"I accept who joins\" unticked.", QUIET);
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

		Card card()
		{
			if (card == null)
			{
				card = new Card("Start a group");
				card.addWide(this);
			}
			return card;
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
				a.isLoot() ? (ffa.isSelected() ? "ffa" : "split") : null, text.isEmpty() ? null : text, voice.isSelected(),
				voice.isSelected() && voiceLimit.isSelected(), approval.isSelected());
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
