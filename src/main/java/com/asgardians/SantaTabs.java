package com.asgardians;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.ZoneId;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.client.ui.FontManager;

/**
 * The Santa Claws page: the clan's Christmas co-op raid. From the top: the event and its clock, the line-up of bosses
 * (wrapped until the clan reaches them), the boss being fought with its HP and bonus, the attacks it's charging and how
 * to block them (with a Defend! button for attacks the clan blocks by rallying), the debuffs on the clan, the damage
 * board, and the drops that hurt the current boss, folded by category. Swing thread only.
 */
final class SantaTabs
{
	static final Color SANTA_RED = new Color(0xD8, 0x26, 0x1F);
	static final Color HOLLY = new Color(0x2A, 0xB0, 0x48);
	static final Color ICE = new Color(0xBF, 0xE3, 0xF2);
	private static final Color RED_CARD = new Color(0x3B, 0x17, 0x14);
	private static final Color GOLD_CARD = new Color(0x3A, 0x2C, 0x12);
	private static final Color GREEN_CARD = new Color(0x1D, 0x33, 0x20);
	private static final Color ORANGE_CARD = new Color(0x3D, 0x22, 0x12);
	/** Throws Shade: lilac on near-black, like the website's shade. */
	private static final Color SHADE = new Color(0xD9, 0xCF, 0xE6);
	private static final Color SHADE_CARD = new Color(0x1A, 0x10, 0x1F);

	/** Damage dice rarities, the same names and colours as the website's drop board. */
	private static final String[] RARITY = {"Common", "Uncommon", "Rare", "Epic", "Legendary"};
	private static final Color[] RARITY_COLOUR = {
		new Color(0x5A, 0xD9, 0xE7), new Color(0x54, 0xC2, 0x64), new Color(0xEC, 0x37, 0xDA),
		new Color(0xA4, 0x6B, 0xFF), new Color(0xF0, 0xB4, 0x3C)};

	private static final Map<String, String> MINIGAMES = Map.of(
		"wintertodt", "Wintertodt", "tempoross", "Tempoross", "guardians_of_the_rift", "Guardians of the Rift");


	interface Actions
	{
		/** Presses Defend! on a live attack, as the logged-in member. */
		void defend(long attackId);

		/** Opens or folds a drop category. */
		void fold(String ropeKey);

		/** Spends one of the clan's skips to cancel a live attack (moderators only; asks first). */
		void skip(long attackId, String attackName);

		/** Votes for one of a question attack's options, as the logged-in member (once per attack). */
		void vote(long attackId, String option);
	}

	@Value
	static class Input
	{
		/** Null until the first load (or when there's no event). */
		SantaData.Page page;
		/** Why it can't load at all (not logged in, not in the clan), or null. */
		String notConnected;
		/** The last load failed; the page keeps showing what it had. */
		String error;
		Instant now;
		ZoneId zone;
		/** Attacks with a Defend! press on its way. */
		Set<Long> defending;
		/** What the last Defend! press said, by attack. */
		Map<Long, String> messages;
		/** Question attacks this member has voted on: the option they picked, or "" when only the website knows. */
		Map<Long, String> voted;
		/** Drop categories folded open. */
		Set<String> open;
		/** Loads the boss artwork (and, outside the game, drop pictures). */
		SantaPictures.Images images;
	}

	private SantaTabs()
	{
	}

	static void draw(JPanel tab, Input in, Actions actions)
	{
		tab.removeAll();
		if (in.getNotConnected() != null)
		{
			note(tab, in.getNotConnected());
			return;
		}
		SantaData.Page page = in.getPage();
		SantaData.Board board = page != null ? page.getBoard() : null;
		if (board == null || board.getEvent() == null)
		{
			note(tab, in.getError() != null ? in.getError() : page == null ? "Loading Santa Claws…" : "There's no Santa Claws event right now.");
			return;
		}
		if (in.getError() != null)
		{
			PanelParts.addCard(tab, PanelParts.status(PanelParts.BAD, in.getError()));
		}

		// While it's live the page's header already shows the name and time left, so the box only comes back for the
		// intro picture before the start and the victory line at the end.
		if (board.getEvent().isUpcoming() || board.isComplete() || "complete".equals(board.getEvent().getStatus()))
		{
			PanelParts.addCard(tab, eventCard(board, in));
		}
		// At a glance: one line each, with its bar; click a line for the details. The boss comes first (open until
		// folded), with the line-up of presents under it.
		SantaData.Encounter enc = board.active();
		boolean fighting = board.getEvent().isLive() && enc != null && enc.isRevealed();
		if (!fighting)
		{
			PanelParts.addCard(tab, actsRow(board, in.getImages()));
		}
		if (fighting)
		{
			// "NOW FIGHTING" with the six acts as tiny presents beside it (beaten green, the current one bright).
			JPanel heading = new JPanel(new BorderLayout());
			heading.setOpaque(false);
			heading.add(PanelParts.section("Now fighting"), BorderLayout.WEST);
			heading.add(tinyActs(board), BorderLayout.EAST);
			PanelParts.addCard(tab, heading);
			PanelParts.addCard(tab, bossLine(enc, board, page.getMe(), in, actions));

			// Active debuffs (red), only while there are any: one line each, opening for what it does.
			List<Debuff> debuffs = debuffs(enc);
			if (!debuffs.isEmpty())
			{
				PanelParts.addCard(tab, heading("Active debuffs", PanelParts.BAD));
				for (Debuff d : debuffs)
				{
					PanelParts.addCard(tab, glance("debuff:" + d.getTitle(), d.getTitle(), d.getEffect(), null, PanelParts.BAD, RED_CARD,
						in, actions, () -> debuffDetails(d)));
				}
			}

			// Charging attacks (orange), each opening for the details, Defend! and Skip.
			PanelParts.addCard(tab, heading("Charging attacks", PanelParts.HIGHLIGHT));
			boolean any = false;
			for (SantaData.Pending p : new SantaData.Pending[]{enc.getPendingAttack(), enc.getPendingMinionAttack()})
			{
				if (p != null)
				{
					any = true;
					PanelParts.addCard(tab, attackLine(p, enc, in, actions));
				}
			}
			if (!any)
			{
				PanelParts.addCard(tab, glance("attack", "No attack charging", nextAttack(enc, in), null, null, null, in, actions,
					() -> quietAttackCard(enc, in)));
			}

			if (shaded(enc))
			{
				// Throws Shade: the bonus and leaderboards would give the hidden HP away, so only the latest hit shows.
				PanelParts.addCard(tab, heading("Throwing shade", SHADE));
				PanelParts.addCard(tab, shadeCard(enc, board, in));
			}
			// The bonus (yellow, green while it's on): its bar at a glance, opening for what it does.
			else if (enc.getBonus() != null && enc.getBonus().getSkill() != null)
			{
				SantaData.Bonus b = enc.getBonus();
				PanelParts.addCard(tab, heading("Bonus", b.isActive() ? PanelParts.GOOD : PanelParts.GOLD));
				PanelParts.addCard(tab, bonusLine(b, in, actions));
			}
		}

		boolean shaded = fighting && shaded(enc);
		List<SantaData.Player> top = SantaData.orEmpty(board.getTopDamage());
		if (!shaded && (!top.isEmpty() || page.getMe() != null || (page.getSkillers() != null && enc != null)))
		{
			PanelParts.addCard(tab, PanelParts.section("Leaderboards"));
		}
		if (!shaded && (!top.isEmpty() || page.getMe() != null))
		{
			SantaData.Me me = page.getMe();
			PanelParts.addCard(tab, glance("damage", "Damage board",
				me != null && me.getRank() != null ? "You #" + me.getRank() : top.isEmpty() ? "None yet" : "Top " + Math.min(5, top.size()),
				null, null, null, in, actions, () -> damageCard(top, me)));
		}

		// The boss's skilling board (XP in its skills since it came up, counted by the clan plugin).
		SantaData.Skillers skillers = page.getSkillers();
		if (!shaded && skillers != null && enc != null)
		{
			List<SantaData.Skiller> skilling = SantaData.orEmpty(skillers.getTop());
			PanelParts.addCard(tab, glance("skilling", "Skilling board",
				skillers.getMyRank() != null ? "You #" + skillers.getMyRank() : skilling.isEmpty() ? "None yet" : "Top " + Math.min(10, skilling.size()),
				null, null, null, in, actions, () -> skillingCard(skillers, enc, page.getMe())));
		}

		List<SantaData.Rope> ropes = SantaData.orEmpty(page.getDrops());
		if (!ropes.isEmpty() && enc != null)
		{
			PanelParts.addCard(tab, PanelParts.section("Drops that hurt " + name(enc)));
			for (SantaData.Rope rope : ropes)
			{
				PanelParts.addCard(tab, ropeCard(rope, in.getOpen().contains(rope.getRopeKey()), actions, in.getImages()));
			}
		}
		tab.revalidate();
		tab.repaint();
	}

	// ---- at a glance ----

	/**
	 * A one-line card: a fold arrow and title with a short value on the right, a thin bar under it, and the details
	 * below while it's open. {@code key} remembers whether it's open.
	 */
	private static JComponent glance(String key, String title, String right, JComponent bar, Color edge, Color fill, Input in,
		Actions actions, java.util.function.Supplier<JComponent> details)
	{
		return glance(key, title, right, null, bar, edge, fill, in, actions, details);
	}

	/** The same, with {@code badge} (e.g. the skips) on the right of the title instead of text. */
	private static JComponent glance(String key, String title, String right, JComponent badge, JComponent bar, Color edge, Color fill,
		Input in, Actions actions, java.util.function.Supplier<JComponent> details)
	{
		PanelParts.Card card = new PanelParts.Card(null);
		if (edge != null)
		{
			card.outlined(edge, fill);
		}
		boolean open = in.getOpen().contains(key);
		JPanel header = PanelParts.foldHeader(title, open, right, () -> actions.fold(key));
		if (badge != null)
		{
			JPanel holder = new JPanel(new java.awt.GridBagLayout());
			holder.setOpaque(false);
			holder.add(badge);
			header.add(holder, BorderLayout.EAST);
		}
		card.add(header);
		if (bar != null)
		{
			card.add(Box.createVerticalStrut(4));
			card.add(bar);
		}
		if (open)
		{
			card.add(Box.createVerticalStrut(8));
			JComponent d = details.get();
			d.setAlignmentX(JComponent.LEFT_ALIGNMENT);
			// A stack lines its rows up by their own alignment, so a centred one (a plain panel's default) pushes
			// the rest across: line them all up on the left.
			for (java.awt.Component c : d.getComponents())
			{
				if (c instanceof JComponent)
				{
					((JComponent) c).setAlignmentX(JComponent.LEFT_ALIGNMENT);
				}
			}
			card.add(d);
		}
		return card;
	}

	/** The boss's HP at a glance; open for the picture, the blurb and every minion's bar. */
	private static JComponent bossLine(SantaData.Encounter e, SantaData.Board board, SantaData.Me me, Input in, Actions actions)
	{
		boolean blind = e.getEffects() != null && e.getEffects().isBlind();
		SantaData.Unit boss = e.boss();
		String title = (e.getActLabel() != null ? actTitle(e.getActLabel()) + ": " : "") + name(e);
		// One bar: whoever the clan's drops hit right now (a decoy, then each minion in turn, then the boss).
		SantaData.Unit target = target(e);
		int left = 0;
		for (SantaData.Unit u : SantaData.orEmpty(e.getUnits()))
		{
			left += !"boss".equals(u.getKey()) && !u.isDecoy() && (u.getHp() == null || u.getHp() > 0) ? 1 : 0;
		}
		JPanel bars = PanelParts.column();
		if (target != null)
		{
			// A decoy (Seeing Double) says so, in place of "Now attacking".
			String who = target == boss ? "Now attacking: " + name(e)
				: target.isDecoy() ? "Seeing double: " + target.getName() : "Now attacking: " + target.getName();
			JComponent line = PanelParts.spread(PanelParts.plain(who, target.isDecoy() ? PanelParts.GOLD : PanelParts.QUIET),
				PanelParts.plain(left > 0 ? left + " minion" + (left == 1 ? "" : "s") + " left" : "", PanelParts.QUIET));
			// Lined up on the left like the bar, or the stack pushes the bar across.
			line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
			bars.add(line);
			bars.add(Box.createVerticalStrut(2));
			bars.add(SantaPictures.hpBar(blind ? null : target.getHp(), target.getMaxHp(), 14));
		}
		// The clan's banked skips, just so everyone can see them (staff spend them from an attack's card).
		JComponent skips = board.getSkipsLeft() > 0 ? skipBadge(board.getSkipsLeft()) : null;
		return glance("boss", title, null, skips, bars, null, null, in, actions,
			() -> bossCard(e, board, me, in, actions));
	}

	/** The boss has thrown shade (the clan is blinded): HP, hits, the bonus and the boards are hidden. */
	static boolean shaded(SantaData.Encounter e)
	{
		return e != null && e.getEffects() != null && e.getEffects().isBlind();
	}

	/** "HP hidden · clears in 3h 12m" (or just "HP hidden"). */
	static String shadeNote(SantaData.Encounter e, Instant now)
	{
		Instant until = e.getEffects() != null ? ClanData.parse(e.getEffects().getBlindUntil()) : null;
		return until != null ? "HP hidden · clears in " + ClanView.countdown(now, until) : "HP hidden";
	}

	/**
	 * In place of the bonus and leaderboards while the boss throws shade: when it clears, and the latest hit (who and
	 * what, no damage) so the clan can see drops are still landing.
	 */
	private static JComponent shadeCard(SantaData.Encounter e, SantaData.Board board, Input in)
	{
		PanelParts.Card card = new PanelParts.Card(null).outlined(SHADE, SHADE_CARD);
		Instant until = e.getEffects() != null ? ClanData.parse(e.getEffects().getBlindUntil()) : null;
		card.add(PanelParts.small("Bonus and leaderboards hidden" + (until != null ? " · clears in " + ClanView.countdown(in.getNow(), until) : "") + ".", SHADE));
		SantaData.LogItem latest = null;
		for (SantaData.LogItem r : SantaData.orEmpty(board.getRecent()))
		{
			if ("hit".equals(r.getKind()))
			{
				latest = r;
				break;
			}
		}
		card.add(Box.createVerticalStrut(5));
		card.add(PanelParts.small(latest != null
			? "Latest hit: " + nz(latest.getRsn()) + " · " + nz(latest.getItemName()) + " (damage hidden)"
			: "No hits since the shade. Keep the drops coming: they still count.", PanelParts.TEXT));
		return card;
	}

	/** A section heading in a colour, e.g. "ACTIVE DEBUFFS" in red. */
	private static JComponent heading(String text, Color colour)
	{
		JLabel label = PanelParts.section(text);
		label.setForeground(colour);
		return label;
	}

	/** The bonus at a glance: its name and state, the XP bar with what's left; open for what it does. Gold, green while on. */
	private static JComponent bonusLine(SantaData.Bonus b, Input in, Actions actions)
	{
		Color colour = b.isActive() ? PanelParts.GOOD : b.isFrozen() ? ICE : PanelParts.GOLD;
		String state = b.isActive() ? "×" + b.getMultiplier() + " ON" : b.isFrozen() ? "Frozen"
			: (b.getTarget() > 0 ? Math.min(99, b.getProgress() * 100 / b.getTarget()) : 0) + "%";
		JComponent bar = SantaPictures.thinBar(b.getProgress(), b.getTarget(), colour,
			b.isActive() ? "×" + b.getMultiplier() + " damage is ON" : b.isFrozen() ? "Frozen for 4 hours"
				: b.getTarget() <= 0 ? null : fmtXp(Math.max(0, b.getTarget() - b.getProgress())) + " " + nz(b.getSkill()) + " XP left");
		return glance("bonus", b.getName() != null ? b.getName() : nz(b.getSkill()), state, bar,
			b.isActive() ? PanelParts.GOOD : PanelParts.GOLD, b.isActive() ? GREEN_CARD : GOLD_CARD, in, actions, () ->
			{
				JPanel card = PanelParts.column();
				card.add(PanelParts.small(b.getDescription() != null ? b.getDescription()
					: "Gain " + fmtXp(b.getTarget()) + " " + nz(b.getSkill()) + " XP as a clan to switch it on.", PanelParts.QUIET));
				return card;
			});
	}

	/** A debuff on the clan: its name, a short effect for the line, and what it does when opened. */
	@Value
	static class Debuff
	{
		String title;
		String effect;
		String about;
	}

	private static JComponent debuffDetails(Debuff d)
	{
		JPanel card = PanelParts.column();
		// One row per line (a curse on several members lists each of them).
		for (String line : d.getAbout().split("\n"))
		{
			card.add(PanelParts.small(line, PanelParts.TEXT));
		}
		return card;
	}

	/** An attack charging: its name, the time left, and how close the clan is to blocking it; open for the rest and Defend!. */
	private static JComponent attackLine(SantaData.Pending p, SantaData.Encounter e, Input in, Actions actions)
	{
		Color edge = p.isDefended() ? PanelParts.GOOD : PanelParts.HIGHLIGHT;
		Color fill = p.isDefended() ? GREEN_CARD : ORANGE_CARD;
		SantaData.Defence d = p.getDefence();
		String right = p.isDefended() ? "Blocked" : ClanView.countdown(in.getNow(), ClanData.parse(p.getDefendBy()));
		JComponent bar;
		if (d != null && "vote".equals(d.getKind()) && !p.isDefended())
		{
			// A question: a full bar, red until this member has voted, then green.
			boolean voted = in.getVoted() != null && in.getVoted().containsKey(p.getId());
			bar = SantaPictures.thinBar(1, 1, voted ? PanelParts.GOOD : PanelParts.BAD, voteLabel(p, in));
		}
		else
		{
			bar = d != null ? SantaPictures.thinBar(p.getProgress(), d.getAmount(), p.isDefended() ? PanelParts.GOOD : ICE,
				p.isDefended() ? null : remainingText(d, p.getProgress())) : null;
		}
		return glance("attack:" + p.getId(), (p.isMinion() ? "Minions: " : "") + p.getName(), right, bar, edge, fill, in, actions,
			() -> attackCard(p, e, in, actions));
	}

	private static String nextAttack(SantaData.Encounter e, Input in)
	{
		Instant next = ClanData.parse(e.getNextAttackAt());
		return next != null ? "next ~" + ClanView.countdown(in.getNow(), next) : null;
	}

	/** The acts as one row of presents: a picture once reached (green edge when beaten, red while fought), wrapped before. */
	private static JComponent actsRow(SantaData.Board board, SantaPictures.Images images)
	{
		PanelParts.Card card = new PanelParts.Card(null);
		List<SantaData.Encounter> encounters = SantaData.orEmpty(board.getEncounters());
		JPanel row = new JPanel(new java.awt.GridLayout(1, Math.max(1, encounters.size()), 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		for (SantaData.Encounter e : encounters)
		{
			boolean current = e.isActive() && e.isRevealed();
			JComponent tile = e.isRevealed()
				? SantaPictures.thumb(SantaPictures.pluginPicture(SantaPictures.current(e)), e.getArt() != null ? e.getArt().getAspect() : null, images, 30)
				: wrapped(30);
			JPanel cell = new JPanel(new BorderLayout());
			cell.setOpaque(false);
			Color edge = e.isDefeated() ? HOLLY : current ? SANTA_RED : null;
			cell.setBorder(edge != null ? javax.swing.BorderFactory.createLineBorder(edge, 2) : new EmptyBorder(2, 2, 2, 2));
			cell.add(tile, BorderLayout.CENTER);
			String tip = (e.getActLabel() != null ? e.getActLabel() + ": " : "") + (e.isRevealed() ? name(e) : "still wrapped")
				+ (e.isDefeated() ? " (beaten)" : current ? " (fighting now)" : "")
				+ (e.getPrizeText() != null ? ". Unlocks " + e.getPrizeText() : "");
			cell.setToolTipText(tip);
			tile.setToolTipText(tip);
			row.add(cell);
		}
		card.add(row);
		SantaData.Encounter now = board.active();
		if (now != null && now.isRevealed() && now.getPrizeText() != null && !board.isComplete())
		{
			card.add(Box.createVerticalStrut(4));
			card.add(PanelParts.small((now.getActLabel() != null ? now.getActLabel() + " unlocks " : "Unlocks ") + now.getPrizeText(), PanelParts.QUIET));
		}
		return card;
	}

	// ---- details ----

	private static void note(JPanel tab, String text)
	{
		PanelParts.Card card = new PanelParts.Card("Santa Claws");
		card.add(PanelParts.small(text, PanelParts.QUIET));
		PanelParts.addCard(tab, card);
		tab.revalidate();
		tab.repaint();
	}

	/**
	 * Only before the start (the intro picture, as every boss is still wrapped) and at the end (the victory line). The
	 * page's header shows the name and the time left.
	 */
	private static JComponent eventCard(SantaData.Board board, Input in)
	{
		PanelParts.Card card = new PanelParts.Card(null).outlined(SANTA_RED, RED_CARD);
		if (board.getEvent().isUpcoming())
		{
			card.add(SantaPictures.picture(SantaPictures.pluginPicture("/img/santa-claws/intro.webp"), null, in.getImages(), null, null, false));
		}
		else
		{
			card.add(PanelParts.small(clockLine(board, in.getNow()), PanelParts.GOLD));
		}
		return card;
	}

	/** A small gold skip (⏭) badge with the count on it; hovering says what it's for. */
	private static JComponent skipBadge(int skips)
	{
		String text = String.valueOf(skips);
		java.awt.Font font = FontManager.getRunescapeSmallFont();
		JComponent badge = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int h = getHeight();
				g2.setColor(PanelParts.GOLD);
				g2.fillRoundRect(0, 0, getWidth(), h, h, h);
				g2.setColor(Color.BLACK);
				int top = 3;
				int bottom = h - 3;
				int mid = h / 2;
				g2.fillPolygon(new int[]{5, 9, 5}, new int[]{top, mid, bottom}, 3);
				g2.fillPolygon(new int[]{9, 13, 9}, new int[]{top, mid, bottom}, 3);
				g2.fillRect(13, top, 2, bottom - top);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setFont(font);
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(text, 18, (h + fm.getAscent() - fm.getDescent()) / 2);
				g2.dispose();
			}
		};
		Dimension d = new Dimension(18 + badge.getFontMetrics(font).stringWidth(text) + 6, 14);
		badge.setPreferredSize(d);
		badge.setMinimumSize(d);
		badge.setMaximumSize(d);
		badge.setToolTipText(skips + " skip" + (skips == 1 ? "" : "s") + " banked: staff can cancel any attack");
		return badge;
	}

	/** "Starts in 3d 4h", "Ends in 6d 2h" or the victory line. */
	static String clockLine(SantaData.Board board, Instant now)
	{
		SantaData.Event ev = board.getEvent();
		if (board.isComplete() || "complete".equals(ev.getStatus()))
		{
			return "Every boss is down. Merry Christmas, Asgardians!";
		}
		if (ev.isUpcoming())
		{
			return ev.start() != null ? "Starts in " + ClanView.countdown(now, ev.start()) : "Starting soon";
		}
		return ev.end() != null ? "Live · ends in " + ClanView.countdown(now, ev.end()) : "Live now";
	}

	/** A wrapped present: red paper, gold ribbon. Bosses the clan hasn't reached stay a surprise. */
	private static JComponent wrapped(int size)
	{
		JComponent box = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				int s = Math.min(getWidth(), getHeight()) - 2;
				int x = (getWidth() - s) / 2;
				int y = (getHeight() - s) / 2 + 2;
				g2.setColor(SANTA_RED);
				g2.fillRoundRect(x, y + 3, s, s - 4, 4, 4);
				g2.setColor(PanelParts.GOLD);
				g2.fillRect(x + s / 2 - 2, y + 3, 4, s - 4);
				g2.fillRect(x, y + s / 2, s, 3);
				g2.fillOval(x + s / 2 - 6, y - 1, 6, 6);
				g2.fillOval(x + s / 2, y - 1, 6, 6);
				g2.dispose();
			}
		};
		java.awt.Dimension d = new java.awt.Dimension(33, size);
		box.setPreferredSize(d);
		box.setMinimumSize(d);
		box.setMaximumSize(d);
		return box;
	}

	/**
	 * Who the clan's drops hit now, in the website's damage order: a standing decoy, then the living minions in turn,
	 * then the boss. Null when there are no units.
	 */
	static SantaData.Unit target(SantaData.Encounter e)
	{
		List<SantaData.Unit> units = SantaData.orEmpty(e.getUnits());
		for (SantaData.Unit u : units)
		{
			if (u.isDecoy() && (u.getHp() == null || u.getHp() > 0))
			{
				return u;
			}
		}
		for (SantaData.Unit u : units)
		{
			if (!"boss".equals(u.getKey()) && !u.isDecoy() && (u.getHp() == null || u.getHp() > 0))
			{
				return u;
			}
		}
		return e.boss();
	}

	/** "ACT I" → "Act I", "FINALE" → "Finale". */
	static String actTitle(String label)
	{
		String t = label.trim();
		if (t.toUpperCase(Locale.UK).startsWith("ACT "))
		{
			return "Act " + t.substring(4).trim();
		}
		return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.UK) + t.substring(1).toLowerCase(Locale.UK);
	}

	/** The acts as a row of tiny presents for the section heading: green once beaten, bright for the current one. */
	private static JComponent tinyActs(SantaData.Board board)
	{
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 3, 0));
		row.setOpaque(false);
		for (SantaData.Encounter e : SantaData.orEmpty(board.getEncounters()))
		{
			boolean current = e.isActive() && e.isRevealed();
			// Unlocked acts get their boss's goofy icon (faded once beaten); the rest stay wrapped.
			JComponent present = e.isRevealed() && (current || e.isDefeated())
				? SantaIcons.icon(SantaIcons.kind(e.getShortName(), e.getName()), 13, e.isDefeated())
				: tinyPresent(11, new Color(0x8A, 0x2E, 0x26), new Color(0x9A, 0x80, 0x48));
			present.setToolTipText((e.getActLabel() != null ? actTitle(e.getActLabel()) + ": " : "")
				+ (e.isRevealed() ? name(e) : "still wrapped") + (e.isDefeated() ? " (beaten)" : current ? " (fighting now)" : "")
				+ (e.getPrizeText() != null && e.isRevealed() ? ". Unlocks " + e.getPrizeText() : ""));
			row.add(present);
		}
		return row;
	}

	private static JComponent tinyPresent(int size, Color body, Color ribbon)
	{
		JComponent box = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				int w = getWidth();
				int h = getHeight();
				int lid = Math.max(2, h / 4);
				g2.setColor(body);
				g2.fillRect(0, lid, w, h - lid);
				g2.setColor(ribbon);
				g2.fillRect(w / 2 - 1, lid, 2, h - lid);
				g2.fillRect(0, lid + (h - lid) / 2 - 1, w, 2);
				// The bow.
				g2.fillRect(w / 2 - 3, 0, 2, lid);
				g2.fillRect(w / 2 + 1, 0, 2, lid);
				g2.dispose();
			}
		};
		Dimension d = new Dimension(size, size);
		box.setPreferredSize(d);
		box.setMinimumSize(d);
		box.setMaximumSize(d);
		return box;
	}

	private static JComponent bossCard(SantaData.Encounter e, SantaData.Board board, SantaData.Me me, Input in, Actions actions)
	{
		SantaPictures.Images images = in.getImages();
		JPanel card = PanelParts.column();
		boolean blind = e.getEffects() != null && e.getEffects().isBlind();
		// The artwork, with the boss's live HP painted over the bar drawn into it (like the website).
		String picture = SantaPictures.pluginPicture(SantaPictures.current(e));
		if (picture != null)
		{
			card.add(SantaPictures.picture(picture, e.getArt() != null ? e.getArt().getAspect() : null, images, e.getArt(), e.getUnits(), blind,
				blind ? shadeNote(e, in.getNow()) : null));
			card.add(Box.createVerticalStrut(6));
		}
		if (e.getBlurb() != null)
		{
			// The blurb folds away under a small "About Frosty" line, closed until clicked.
			boolean open = in.getOpen().contains("blurb");
			JLabel about = PanelParts.plain((open ? "▾ " : "▸ ") + "About " + name(e), PanelParts.QUIET);
			about.setAlignmentX(JComponent.LEFT_ALIGNMENT);
			about.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			about.setToolTipText(open ? "Click to fold away" : "Click to read about " + name(e));
			about.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent ev)
				{
					actions.fold("blurb");
				}
			});
			card.add(about);
			if (open)
			{
				card.add(Box.createVerticalStrut(3));
				card.add(PanelParts.small(e.getBlurb(), PanelParts.QUIET));
			}
			card.add(Box.createVerticalStrut(4));
		}
		return card;
	}

	/**
	 * For members who may spend the clan's skips: a Skip button on a live attack, with how many are banked (the website
	 * checks the rank again). Others see nothing.
	 */
	private static void skipButton(JPanel card, SantaData.Pending p, Input in, Actions actions)
	{
		SantaData.Page page = in.getPage();
		SantaData.Me me = page != null ? page.getMe() : null;
		int left = page != null && page.getBoard() != null ? page.getBoard().getSkipsLeft() : 0;
		if (me == null || !me.isCanSkip() || left <= 0 || p.isDefended())
		{
			return;
		}
		card.add(Box.createVerticalStrut(6));
		boolean busy = in.getDefending().contains(p.getId());
		JButton skip = PanelParts.button(busy ? "Skipping…" : "Skip it  (" + left + " banked)", false, () -> actions.skip(p.getId(), p.getName()));
		skip.setEnabled(!busy);
		skip.setToolTipText("Staff: spend one of the clan's " + left + " skip" + (left == 1 ? "" : "s") + " to cancel this attack.");
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		holder.add(skip, BorderLayout.CENTER);
		card.add(holder);
	}

	static String hpText(SantaData.Unit u, boolean blind)
	{
		return blind || u.getHp() == null ? "? / ?" : u.getHp() + " / " + u.getMaxHp();
	}

	private static JComponent attackCard(SantaData.Pending p, SantaData.Encounter e, Input in, Actions actions)
	{
		JPanel card = PanelParts.column();
		// The countdown sits beside the attack's name in the card header, so it isn't repeated here.
		if (p.isDefended())
		{
			card.add(PanelParts.small("Blocked. Well defended!", PanelParts.GOOD));
		}
		SantaData.Defence d = p.getDefence();
		// A question attack leads with the question and its answers: that's what people are here to press.
		boolean question = d != null && "vote".equals(d.getKind()) && !p.isDefended() && p.getQuestion() != null;
		if (question)
		{
			voteButtons(card, p, in, actions);
			card.add(Box.createVerticalStrut(6));
		}
		if (p.getLine() != null && !p.isDefended())
		{
			card.add(PanelParts.small("\"" + p.getLine() + "\"", PanelParts.QUIET));
		}
		if (!p.isDefended())
		{
			card.add(Box.createVerticalStrut(4));
			card.add(PanelParts.small("If it lands: " + p.getDescription(), PanelParts.TEXT));
		}
		if (d != null)
		{
			card.add(Box.createVerticalStrut(4));
			card.add(PanelParts.small("To block: " + defenceText(d), PanelParts.GOLD));
			card.add(Box.createVerticalStrut(3));
			if (!"vote".equals(d.getKind()))
			{
				card.add(PanelParts.progress(p.getProgress(), d.getAmount(), progressText(d, p.getProgress())));
			}
			if ("challenge".equals(d.getKind()) && !p.isDefended() && p.getChallenge() != null)
			{
				card.add(Box.createVerticalStrut(6));
				card.add(PanelParts.small(p.getChallenge(), new Color(0xFF, 0xE9, 0xA8)));
				card.add(PanelParts.small("Screenshot it, then /santa proof in Discord"
					+ (p.getWaiting() > 0 ? " · " + p.getWaiting() + " waiting for staff" : ""), PanelParts.QUIET));
			}
			if ("rally".equals(d.getKind()) && !p.isDefended())
			{
				card.add(Box.createVerticalStrut(6));
				boolean busy = in.getDefending().contains(p.getId());
				JButton defend = PanelParts.button(busy ? "Defending…" : "Defend!  " + p.getProgress() + " / " + d.getAmount(), true,
					() -> actions.defend(p.getId()));
				defend.setEnabled(!busy);
				defend.setToolTipText("Counts once per member. Enough clanmates and the attack is blocked.");
				JPanel holder = new JPanel(new BorderLayout());
				holder.setOpaque(false);
				holder.setAlignmentX(JComponent.LEFT_ALIGNMENT);
				holder.add(defend, BorderLayout.CENTER);
				card.add(holder);
			}
		}
		skipButton(card, p, in, actions);
		String said = in.getMessages().get(p.getId());
		if (said != null)
		{
			card.add(Box.createVerticalStrut(4));
			card.add(PanelParts.small(said, ICE));
		}
		return card;
	}

	/**
	 * A question vote: the question, then a button per option with its votes so far. Each member votes once; nothing is
	 * decided until time's up, when the most-voted option wins (right answer blocks it, wrong or a tie lets it land).
	 */
	private static void voteButtons(JPanel card, SantaData.Pending p, Input in, Actions actions)
	{
		SantaData.Question q = p.getQuestion();
		card.add(PanelParts.small(nz(q.getQuestion()), new Color(0xFF, 0xE9, 0xA8)));
		boolean busy = in.getDefending().contains(p.getId());
		// Once this member has voted (here, or anywhere else the website told us about), the buttons just show the
		// counts: one vote each, so there's nothing left to press.
		String mine = in.getVoted() != null ? in.getVoted().get(p.getId()) : null;
		List<String> options = SantaData.orEmpty(q.getOptions());
		List<Integer> votes = SantaData.orEmpty(q.getVotes());
		for (int i = 0; i < options.size(); i++)
		{
			String option = options.get(i);
			int n = i < votes.size() && votes.get(i) != null ? votes.get(i) : 0;
			boolean picked = mine != null && option.equalsIgnoreCase(mine);
			card.add(Box.createVerticalStrut(4));
			JButton b = PanelParts.button((picked ? "Your vote: " : "") + option + "  (" + n + ")", picked, () -> actions.vote(p.getId(), option));
			b.setEnabled(!busy && mine == null);
			b.setToolTipText(mine != null ? "You've voted. The most-voted answer when time's up decides it."
				: "Vote " + option + ". Once each: the most-voted answer when time's up decides it.");
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.setAlignmentX(JComponent.LEFT_ALIGNMENT);
			holder.add(b, BorderLayout.CENTER);
			card.add(holder);
		}
	}

	private static JComponent quietAttackCard(SantaData.Encounter e, Input in)
	{
		JPanel card = PanelParts.column();
		Instant next = ClanData.parse(e.getNextAttackAt());
		if (next != null)
		{
			card.add(PanelParts.small(name(e) + " attacks again in about " + ClanView.countdown(in.getNow(), next) + ".", PanelParts.QUIET));
		}
		Instant minions = ClanData.parse(e.getNextMinionAttackAt());
		if (minions != null)
		{
			card.add(PanelParts.small("The minions in about " + ClanView.countdown(in.getNow(), minions) + ".", PanelParts.QUIET));
		}
		return card;
	}

	/**
	 * Every debuff on the clan right now: a name, a short effect for its line, and what's happening, in plain words (who
	 * and what, e.g. "Frigg Rune's next drop is halved") rather than the attack's blurb.
	 */
	static List<Debuff> debuffs(SantaData.Encounter e)
	{
		List<Debuff> out = new java.util.ArrayList<>();
		SantaData.Effects fx = e.getEffects();
		if (fx != null)
		{
			for (SantaData.DropMod m : SantaData.orEmpty(fx.getDropMods()))
			{
				// Worded like the website's debuff list.
				int n = m.getRemaining();
				String left = n + " drop" + (n == 1 ? "" : "s");
				String does = "half".equals(m.getMode()) ? "half damage" : "lose-highest".equals(m.getMode()) ? "lose the highest die"
					: "every die −" + m.getAmount();
				out.add(new Debuff(m.getAttackName(), left, "Next " + left + " " + does));
			}
			SantaData.MinionHit mh = fx.getMinionHit();
			if (mh != null)
			{
				out.add(new Debuff(mh.getAttackName(), "1 drop", "Next drop hit ×" + mh.getStrength()));
			}
			// One line per attack, one sentence per member it hit (Nut Shot hits the top 3 at once).
			java.util.Map<String, List<String>> cursed = new java.util.LinkedHashMap<>();
			for (SantaData.Curse c : SantaData.orEmpty(fx.getCurses()))
			{
				cursed.computeIfAbsent(nz(c.getAttackName()), k -> new java.util.ArrayList<>())
					.add(c.getRsn() + "'s next drop " + ("half".equals(c.getMode()) ? "does half" : "is nullified"));
			}
			cursed.forEach((attack, lines) -> out.add(new Debuff(attack, lines.size() == 1 ? "1 member" : lines.size() + " members",
				String.join("\n", lines))));
			if (fx.getHasteRemaining() > 0)
			{
				int n = fx.getHasteRemaining();
				out.add(new Debuff("Haste", n + " attack" + (n == 1 ? "" : "s"), "Next " + n + " attack" + (n == 1 ? "" : "s")
					+ " come " + fx.getHasteFactor() + "× faster"));
			}
			if (fx.isBlind())
			{
				out.add(new Debuff("Blinded", "HP hidden", "HP and the log are hidden for 4 hours"));
			}
		}
		// Seeing Double: a fake boss soaks drops until it's gone. Named after the attack that brought it, like the rest.
		for (SantaData.Unit u : SantaData.orEmpty(e.getUnits()))
		{
			if (u.isDecoy() && (u.getHp() == null || u.getHp() > 0))
			{
				String attack = "Seeing double";
				List<SantaData.Attack> all = new java.util.ArrayList<>(SantaData.orEmpty(e.getAttacks()));
				if (e.getMinionAttack() != null)
				{
					all.add(e.getMinionAttack());
				}
				for (SantaData.Attack a : all)
				{
					if ("decoy".equals(a.getEffectType()) && a.getName() != null)
					{
						attack = a.getName();
						break;
					}
				}
				String hp = u.getHp() == null ? "? / ?" : u.getHp() + " / " + u.getMaxHp();
				out.add(new Debuff(attack, "fake " + hp, "Drops hit the fake " + u.getName() + " first (" + hp + " HP left)"));
			}
		}
		if (e.getBonus() != null && e.getBonus().isFrozen())
		{
			// Named after the attack that froze it (e.g. "Frozen Assets"), like the other debuffs.
			String name = e.getBonus().getName() != null ? e.getBonus().getName() : "The bonus";
			SantaData.Attack freezer = null;
			List<SantaData.Attack> all = new java.util.ArrayList<>(SantaData.orEmpty(e.getAttacks()));
			if (e.getMinionAttack() != null)
			{
				all.add(e.getMinionAttack());
			}
			for (SantaData.Attack a : all)
			{
				if ("bonus-off".equals(a.getEffectType()))
				{
					freezer = a;
					break;
				}
			}
			String off = name + " switched off for 4 hours";
			out.add(new Debuff(freezer != null && freezer.getName() != null ? freezer.getName() : name + " frozen", "bonus off", off));
		}
		return out;
	}

	private static JComponent damageCard(List<SantaData.Player> top, SantaData.Me me)
	{
		JPanel card = PanelParts.column();
		String mine = me != null ? me.getRsn() : null;
		int shown = 0;
		for (SantaData.Player p : top)
		{
			if (shown++ >= 5)
			{
				break;
			}
			card.add(PanelParts.rankRow(shown, p.getRsn(), String.format(Locale.UK, "%,d", p.getDamage()),
				mine != null && DropRecorder.sameRsn(mine, p.getRsn())));
		}
		if (top.isEmpty())
		{
			card.add(PanelParts.small("No damage yet. Go get those drops!", PanelParts.QUIET));
		}
		if (me != null)
		{
			card.add(Box.createVerticalStrut(4));
			card.add(PanelParts.small(me.getDamage() != null
				? "You: #" + me.getRank() + " with " + String.format(Locale.UK, "%,d", me.getDamage()) + " damage"
				: "You're not in the top 10 yet.", PanelParts.GOLD));
		}
		return card;
	}

	/** The top 10 skillers for this boss, the member highlighted, and their own place when they're further down. */
	private static JComponent skillingCard(SantaData.Skillers s, SantaData.Encounter e, SantaData.Me me)
	{
		JPanel card = PanelParts.column();
		List<String> skills = SantaData.orEmpty(s.getSkills());
		if (!skills.isEmpty())
		{
			card.add(PanelParts.small(String.join(" + ", skills) + " XP since " + name(e) + " came up", PanelParts.QUIET));
			card.add(Box.createVerticalStrut(4));
		}
		String mine = me != null ? me.getRsn() : null;
		int rank = 0;
		for (SantaData.Skiller p : SantaData.orEmpty(s.getTop()))
		{
			rank++;
			card.add(PanelParts.rankRow(rank, p.getRsn(), fmtXp(p.getXp()), mine != null && DropRecorder.sameRsn(mine, p.getRsn())));
		}
		if (SantaData.orEmpty(s.getTop()).isEmpty())
		{
			card.add(PanelParts.small("No XP yet. Get skilling!", PanelParts.QUIET));
		}
		card.add(Box.createVerticalStrut(4));
		card.add(PanelParts.small(s.getMyXp() != null ? "You: #" + s.getMyRank() + " with " + fmtXp(s.getMyXp()) + " XP"
			: "You haven't gained any yet.", PanelParts.GOLD));
		card.add(PanelParts.small("Counted live by the clan plugin, so only members running it show up.", PanelParts.QUIET));
		return card;
	}

	private static JComponent ropeCard(SantaData.Rope rope, boolean open, Actions actions, SantaPictures.Images images)
	{
		PanelParts.Card card = new PanelParts.Card(null);
		List<SantaData.Drop> drops = SantaData.orEmpty(rope.getDrops());
		card.add(PanelParts.foldHeader(ropeTitle(rope), open, String.valueOf(drops.size()),
			() -> actions.fold(rope.getRopeKey())));
		if (open)
		{
			card.add(Box.createVerticalStrut(4));
			for (SantaData.Drop d : drops)
			{
				card.add(dropRow(d, images));
			}
		}
		return card;
	}

	private static JComponent dropRow(SantaData.Drop d, SantaPictures.Images images)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		Integer itemId = d.getItemId() != null && d.getItemId() > 0 ? d.getItemId() : PanelParts.itemNamed(d.getItemName());
		if (itemId != null)
		{
			row.add(PanelParts.itemBox(itemId, 22), BorderLayout.WEST);
		}
		else
		{
			// Outside the game (previews): the wiki's picture.
			JLabel icon = new JLabel();
			icon.setPreferredSize(new java.awt.Dimension(22, 22));
			icon.setHorizontalAlignment(JLabel.CENTER);
			images.item(d, img -> icon.setIcon(new javax.swing.ImageIcon(PanelParts.fit(img, 22, 22))));
			row.add(icon, BorderLayout.WEST);
		}
		JLabel name = PanelParts.plain(d.getItemName(), PanelParts.TEXT);
		name.setToolTipText(d.getItemName() + (d.getBoss() != null ? " · " + d.getBoss() : ""));
		name.setPreferredSize(new java.awt.Dimension(0, name.getPreferredSize().height));
		row.add(name, BorderLayout.CENTER);
		int dice = Math.max(1, Math.min(5, d.getDice()));
		JLabel tag = PanelParts.tag(dice + "d20", RARITY_COLOUR[dice - 1]);
		tag.setToolTipText(RARITY[dice - 1] + ": " + dice + " d20 damage dice");
		row.add(tag, BorderLayout.EAST);
		return row;
	}

	// ---- text ----

	static String name(SantaData.Encounter e)
	{
		return e.getShortName() != null ? e.getShortName() : e.getName() != null ? e.getName() : "the boss";
	}

	/** "The Fractured Archive (raid)"; no kind added when the name already says it ("Beginner clues"). */
	static String ropeTitle(SantaData.Rope rope)
	{
		String name = rope.getName() != null ? rope.getName() : "Drops";
		String kind = kindLabel(rope.getKind());
		return kind.isEmpty() || name.toLowerCase(Locale.UK).contains(rope.getKind().toLowerCase(Locale.UK)) ? name : name + kind;
	}

	static String kindLabel(String kind)
	{
		if (kind == null)
		{
			return "";
		}
		switch (kind)
		{
			case "raid":
				return " (raid)";
			case "group":
				return " (group)";
			case "solo":
				return " (solo/duo)";
			case "clue":
				return " (clues)";
			default:
				return "";
		}
	}

	/** How to block an attack, e.g. "Gain 250k Thieving XP as a clan". Matches the website's wording. */
	static String defenceText(SantaData.Defence d)
	{
		long n = d.getAmount();
		String s = n == 1 ? "" : "s";
		String kind = d.getKind() != null ? d.getKind() : "";
		switch (kind)
		{
			case "xp":
				return "Gain " + fmtXp(n) + " " + nz(d.getSkill()) + " XP as a clan";
			case "kc":
				return "Get " + n + " " + minigame(d.getSkill()) + " kill" + s + " as a clan";
			case "items":
				return "Find " + n + " skilling drop" + s + " (no damage, they only block the attack): "
					+ String.join(", ", SantaData.orEmpty(d.getItems()));
			case "rally":
				return "Get " + n + " clanmate" + s + " to press Defend!";
			case "vote":
				return "Vote for the right answer: the most votes when time's up decides it";
			case "challenge":
				return (n == 1 ? "A clanmate completes" : n + " clanmates complete") + " the challenge, with a screenshot";
			default:
				return "Land " + n + " confirmed drop" + s;
		}
	}

	/** Progress towards blocking, e.g. "120k / 250k XP" or "7 / 25 defending". */
	static String progressText(SantaData.Defence d, long progress)
	{
		String kind = d.getKind() != null ? d.getKind() : "";
		switch (kind)
		{
			case "xp":
				return fmtXp(progress) + " / " + fmtXp(d.getAmount()) + " XP";
			case "kc":
				return progress + " / " + d.getAmount() + " kills";
			case "items":
				return progress + " / " + d.getAmount() + " found";
			case "rally":
				return progress + " / " + d.getAmount() + " defending";
			case "vote":
				return progress + " vote" + (progress == 1 ? "" : "s");
			case "challenge":
				return progress + " / " + d.getAmount() + " approved";
			default:
				return progress + " / " + d.getAmount() + " drops";
		}
	}

	/**
	 * A question attack's bar: "Voted" once this member's vote is in (here, or the website said so after a press),
	 * else "Vote now". The counts are on the answer buttons inside.
	 */
	static String voteLabel(SantaData.Pending p, Input in)
	{
		return in.getVoted() != null && in.getVoted().containsKey(p.getId()) ? "Voted" : "Vote now";
	}

	/** What's still needed to block, short enough for the bar, e.g. "82k Firemaking XP left" or "3 more defenders". */
	static String remainingText(SantaData.Defence d, long progress)
	{
		long left = Math.max(0, d.getAmount() - progress);
		String s = left == 1 ? "" : "s";
		String kind = d.getKind() != null ? d.getKind() : "";
		switch (kind)
		{
			case "xp":
				return fmtXp(left) + " " + nz(d.getSkill()) + " XP left";
			case "kc":
				return left + " " + minigame(d.getSkill()) + " kill" + s + " left";
			case "items":
				return left + " more skilling drop" + s;
			case "rally":
				return left + " more defender" + s;
			case "vote":
				return "Vote now";
			case "challenge":
				return left + " more screenshot" + s;
			default:
				return left + " more drop" + s;
		}
	}

	static String minigame(String metric)
	{
		return metric == null ? "" : MINIGAMES.getOrDefault(metric.toLowerCase(Locale.UK), metric);
	}

	/** 1250000 → "1.25m", 250000 → "250k". */
	static String fmtXp(long n)
	{
		if (n >= 1_000_000)
		{
			return trim(String.format(Locale.UK, "%.2f", n / 1_000_000.0)) + "m";
		}
		if (n >= 1_000)
		{
			return trim(String.format(Locale.UK, "%.1f", n / 1_000.0)) + "k";
		}
		return String.valueOf(n);
	}

	private static String trim(String number)
	{
		return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
	}

	private static String nz(String s)
	{
		return s != null ? s : "";
	}

	/** The Home tile's line: what's happening right now. */
	static String tileLine(SantaData.Page page, Instant now)
	{
		SantaData.Board board = page != null ? page.getBoard() : null;
		if (board == null || board.getEvent() == null)
		{
			return null;
		}
		if (board.isComplete())
		{
			return "Every boss down!";
		}
		if (board.getEvent().isUpcoming())
		{
			return board.getEvent().start() != null ? "Starts in " + ClanView.countdown(now, board.getEvent().start()) : "Starting soon";
		}
		SantaData.Encounter e = board.active();
		if (e == null || !e.isRevealed())
		{
			return "Live";
		}
		if ((e.getPendingAttack() != null && !e.getPendingAttack().isDefended())
			|| (e.getPendingMinionAttack() != null && !e.getPendingMinionAttack().isDefended()))
		{
			return "Attack incoming!";
		}
		return (e.getActLabel() != null ? e.getActLabel() + " · " : "") + name(e);
	}

	/** True while an attack the clan hasn't blocked is charging (the tile goes red). */
	static boolean underAttack(SantaData.Page page)
	{
		SantaData.Encounter e = page != null && page.getBoard() != null ? page.getBoard().active() : null;
		return e != null && ((e.getPendingAttack() != null && !e.getPendingAttack().isDefended())
			|| (e.getPendingMinionAttack() != null && !e.getPendingMinionAttack().isDefended()));
	}

	/**
	 * The website's reason for refusing a Defend! press ("You've already pressed Defend! on this one (3 / 25).", "Link
	 * your RSN first"), from its problem JSON; the raw text when it isn't JSON.
	 */
	static String problemText(com.google.gson.Gson gson, String error)
	{
		if (error == null || error.isEmpty())
		{
			return "That didn't work. Try again.";
		}
		try
		{
			com.google.gson.JsonObject json = gson.fromJson(error, com.google.gson.JsonObject.class);
			if (json != null && json.has("detail") && !json.get("detail").isJsonNull())
			{
				return json.get("detail").getAsString();
			}
			if (json != null && json.has("title") && !json.get("title").isJsonNull())
			{
				return json.get("title").getAsString();
			}
		}
		catch (com.google.gson.JsonParseException | IllegalStateException | UnsupportedOperationException ignored)
		{
			// Not JSON: show it as it came.
		}
		return error;
	}
}
