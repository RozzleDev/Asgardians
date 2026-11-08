package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.plain;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.client.util.QuantityFormatter;

/**
 * Builds the Stats tab: the clan's latest drops (all, 1m+, 10m+ or 100m+), new collection log slots, pets and PBs, from
 * the website's {@link ClanData.StatsFeed}. Swing thread only.
 */
final class StatsTabs
{
	static final String DROPS = "drops";
	static final String CLOGS = "clogs";
	static final String PBS = "pbs";
	static final String PETS = "pets";
	/** The pages, in the order of their buttons. */
	static final List<String> PAGES = List.of(DROPS, CLOGS, PETS, PBS);
	private static final List<String> PAGE_LABELS = List.of("Drops", "Clogs", "Pets", "PBs");

	static final String ALL = "all";
	static final String ONE_M = "1m";
	static final String TEN_M = "10m";
	static final String HUNDRED_M = "100m";
	/** The drop value filters, in the order of their buttons. */
	static final List<String> MINIMUMS = List.of(ALL, ONE_M, TEN_M, HUNDRED_M);
	private static final List<String> MINIMUM_LABELS = List.of("All", "1m+", "10m+", "100m+");
	/** The Drops page's type drop-down: drops, pets or new collection log slots. */
	static final List<String> TYPES = List.of(DROPS, PETS, CLOGS);
	private static final List<String> TYPE_LABELS = List.of("All drops", "Pets", "Collection log");
	private static final List<String> VALUE_LABELS = List.of("Any value", "1M+", "10M+", "100M+");
	/** Drops worth this much get an orange edge. */
	static final long BIG_DROP = 100_000_000L;
	private static final java.awt.Color PET = new java.awt.Color(0xE0, 0x6C, 0xC0);

	/** The most rows a list shows. */
	static final int LIMIT = 25;
	/** Item pictures from RuneLite are 36 by 32. */
	private static final Dimension ICON = new Dimension(36, 32);
	/** Boss icons are drawn at 20 by 20, as in RuneLite's Hiscore panel. */
	private static final Dimension BOSS_ICON = new Dimension(22, 22);

	/**
	 * Which of RuneLite's hiscore names a PB's boss is, or null. RuneLite stores PBs under lowercase names, and some
	 * with a mode ("tombs of amascut entry mode"), so names are compared without case, spaces or punctuation, then by
	 * the longest hiscore name the boss starts with.
	 */
	static String hiscoreName(String boss, java.util.Collection<String> names)
	{
		String key = bossKey(boss);
		if (key.isEmpty())
		{
			return null;
		}
		String best = null;
		int bestLength = 0;
		for (String name : names)
		{
			String k = bossKey(name);
			if (k.isEmpty())
			{
				continue;
			}
			if (k.equals(key) || k.equals(key + "s"))
			{
				return name;
			}
			if (key.startsWith(k) && k.length() > bestLength)
			{
				best = name;
				bestLength = k.length();
			}
		}
		return best;
	}

	private static String bossKey(String name)
	{
		if (name == null)
		{
			return "";
		}
		String k = name.toLowerCase(Locale.UK).trim();
		// "The Gauntlet" and "gauntlet", but not "Theatre of Blood".
		k = k.startsWith("the ") ? k.substring(4) : k;
		return k.replaceAll("[^a-z0-9]", "");
	}

	@Value
	static class Input
	{
		/** Null until the first load. */
		ClanData.StatsFeed feed;
		/** The website doesn't have clan stats yet. */
		boolean unsupported;
		/** Null when connected; otherwise why the tab can't load. */
		String notConnected;
		/** The last load failed, with this reason; the lists shown are from before. */
		String error;
		Instant fetchedAt;
		Instant now;
		ZoneId zone;
	}

	/** The member's choices, kept by the panel between redraws. */
	@Value
	static class Ui
	{
		String page;
		String minimum;
	}

	/** What the panel's own buttons do. Swing thread. */
	interface UiActions
	{
		void page(String page);

		void minimum(String minimum);
	}

	/** What the plugin does for the tab. */
	interface Actions
	{
		/** Reload now. */
		void refresh();

		/** The member opened the tab; reload if the lists are a minute old. */
		void opened();

		/** Puts the item's picture on the label, once RuneLite has it. */
		void icon(JLabel label, int itemId, int quantity);

		/** Puts the boss's icon from RuneLite's Hiscore panel on the label, when there is one. */
		void bossIcon(JLabel label, String boss);
	}

	private StatsTabs()
	{
	}

	/** A known page, or Drops. Null-safe, like {@link ClanTabs#period}. */
	static String page(String saved)
	{
		return saved != null && PAGES.contains(saved) ? saved : DROPS;
	}

	/** A known drop filter, or All. */
	static String minimum(String saved)
	{
		return saved != null && MINIMUMS.contains(saved) ? saved : ALL;
	}

	/** The list for a page and drop filter, at most {@link #LIMIT} long; null when the website doesn't send it. */
	static List<ClanData.StatEntry> list(ClanData.StatsFeed feed, String page, String minimum)
	{
		if (feed == null)
		{
			return null;
		}
		List<ClanData.StatEntry> list;
		switch (page(page))
		{
			case CLOGS:
				list = feed.getClogs();
				break;
			case PBS:
				list = feed.getPbs();
				break;
			case PETS:
				list = feed.getPets();
				break;
			default:
				String min = minimum(minimum);
				list = min.equals(HUNDRED_M) ? feed.getDrops100m() : min.equals(TEN_M) ? feed.getDrops10m()
					: min.equals(ONE_M) ? feed.getDrops1m() : feed.getDrops();
		}
		if (list == null)
		{
			return null;
		}
		boolean pbs = page(page).equals(PBS);
		java.util.Map<String, Double> best = pbs ? bestTimes(list) : java.util.Map.of();
		java.util.Set<String> kept = new java.util.HashSet<>();
		List<ClanData.StatEntry> shown = new ArrayList<>();
		for (ClanData.StatEntry e : list)
		{
			if (e == null || shown.size() >= LIMIT)
			{
				continue;
			}
			if (pbs)
			{
				// A member who beat their own PB again shows once, with the better time.
				String key = pbKey(e);
				if (kept.contains(key) || !java.util.Objects.equals(seconds(e), best.get(key)))
				{
					continue;
				}
				kept.add(key);
			}
			shown.add(e);
		}
		return shown;
	}

	/** The same member, boss and team size. */
	private static String pbKey(ClanData.StatEntry e)
	{
		String rsn = e.getRsn() != null ? e.getRsn().toLowerCase(Locale.UK).replace(' ', ' ').trim() : "";
		return rsn + "|" + bossKey(e.getBoss()) + "|" + (e.getTeamSize() != null ? e.getTeamSize() : "");
	}

	/** A PB's time, or the worst possible when it has none. */
	private static double seconds(ClanData.StatEntry e)
	{
		return e.getSeconds() != null && e.getSeconds() > 0 ? e.getSeconds() : Double.MAX_VALUE;
	}

	private static java.util.Map<String, Double> bestTimes(List<ClanData.StatEntry> list)
	{
		java.util.Map<String, Double> best = new java.util.HashMap<>();
		for (ClanData.StatEntry e : list)
		{
			if (e != null)
			{
				best.merge(pbKey(e), seconds(e), Math::min);
			}
		}
		return best;
	}

	static void draw(JPanel column, Input in, Ui ui, UiActions uiActions, Actions actions)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Clan stats");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		String page = page(ui.getPage());
		String minimum = minimum(ui.getMinimum());
		if (!page.equals(PBS))
		{
			// Drops, pets and collection log slots share a page, picked with a drop-down; drops also have a value one.
			javax.swing.JComponent type = PanelParts.dropdown(TYPE_LABELS, TYPES.indexOf(page), i -> uiActions.page(TYPES.get(i)));
			javax.swing.JComponent value = page.equals(DROPS)
				? PanelParts.dropdown(VALUE_LABELS, MINIMUMS.indexOf(minimum), i -> uiActions.minimum(MINIMUMS.get(i))) : null;
			addCard(column, PanelParts.dropdowns(type, value));
		}
		List<ClanData.StatEntry> list = list(in.getFeed(), page, minimum);
		String problem = in.isUnsupported() ? "Clan stats aren't on the website yet."
			: in.getFeed() == null ? (in.getError() != null ? "Couldn't load clan stats." : "Loading…")
			: list == null ? "The website doesn't send these yet."
			: list.isEmpty() ? empty(page, minimum) : null;
		if (problem != null)
		{
			Card card = new Card(title(page, minimum));
			card.add(small(problem, QUIET));
			addCard(column, card);
		}
		else
		{
			column.add(javax.swing.Box.createVerticalStrut(PanelParts.GAP));
			JLabel heading = plain(title(page, minimum).toUpperCase(Locale.UK), QUIET);
			heading.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			column.add(heading);
			for (ClanData.StatEntry e : list)
			{
				column.add(javax.swing.Box.createVerticalStrut(5));
				Card card = new Card(null);
				boolean big = DROPS.equals(page) && e.getValue() != null && e.getValue() >= BIG_DROP;
				if (big)
				{
					card.outlined(PanelParts.HIGHLIGHT, PanelParts.CARD_WARM);
				}
				card.setBorder(new EmptyBorder(6, 6, 6, 8));
				card.addWide(row(e, page, in, actions));
				card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				column.add(card);
			}
		}
		addCard(column, footer(in, actions));
	}

	/**
	 * Tags for a card: the raid it came from ("CoX", "ToB", "ToA" and "Raid"), "Pet" and "Log". Read from the source
	 * name, since the website doesn't send categories yet.
	 */
	static List<String> tags(ClanData.StatEntry e, String page)
	{
		List<String> tags = new ArrayList<>();
		String source = e.getSource() != null ? e.getSource().toLowerCase(Locale.UK) : "";
		String raid = source.contains("chambers of xeric") ? "CoX" : source.contains("theatre of blood") ? "ToB"
			: source.contains("tombs of amascut") ? "ToA" : null;
		if (raid != null)
		{
			tags.add(raid);
			if (DROPS.equals(page))
			{
				tags.add("Raid");
			}
		}
		if (PETS.equals(page))
		{
			tags.add("Pet");
		}
		if (CLOGS.equals(page))
		{
			tags.add("Log");
		}
		return tags;
	}

	private static java.awt.Color tagColour(String tag)
	{
		switch (tag)
		{
			case "Pet":
				return PET;
			case "Log":
				return PanelParts.GOOD;
			case "Raid":
				return PanelParts.GOLD;
			default:
				return PanelParts.HIGHLIGHT;
		}
	}

	// ---- PBs page: the clan's records ----

	/** The member's picks on the PBs page. */
	@Value
	static class PbUi
	{
		String category;
		/** The Bosses group, e.g. "Slayer"; null elsewhere. */
		String group;
		String boss;
		String teamSize;
	}

	interface PbActions
	{
		void pick(String category, String group, String boss, String teamSize);

		void openProfile(String rsn);
	}

	/** "Solo", "4 players". */
	static String sizeLabel(String teamSize)
	{
		return teamSize == null || teamSize.isEmpty() || teamSize.equals("1") ? "Solo" : teamSize + " players";
	}

	/**
	 * The PBs page. With the website's records: drop-downs for category (Raids, Bosses, Skilling, Minigames), the
	 * Bosses group (God Wars, Slayer, Wilderness...), the boss and team size, the clan record in gold, then the rest of
	 * the top ten. Without them: the newest PBs, as before.
	 */
	static void pbs(JPanel column, Input in, PbUi ui, PbActions pbActions, UiActions uiActions, Actions actions, String me)
	{
		List<ClanData.PbRecord> records = in.getFeed() != null ? in.getFeed().getRecords() : null;
		if (in.getNotConnected() != null || records == null || records.isEmpty())
		{
			draw(column, in, new Ui(PBS, null), uiActions, actions);
			if (in.getNotConnected() == null && in.getFeed() != null)
			{
				Card note = new Card(null);
				note.add(small("Clan records by boss and team size show here once the clan website sends them.", QUIET));
				addCard(column, note);
			}
			return;
		}
		column.removeAll();
		// Category, then group ("" when the category has none), then boss, then its records by team size. The website's
		// category and group come first; the plugin sorts the rest itself.
		java.util.Map<String, java.util.Map<String, java.util.Map<String, List<ClanData.PbRecord>>>> tree = new java.util.LinkedHashMap<>();
		for (ClanData.PbRecord r : records)
		{
			if (r == null || r.getBoss() == null || r.getEntries() == null || r.getEntries().isEmpty())
			{
				continue;
			}
			String[] where = BossGroups.place(r.getBoss(), r.getCategory(), r.getGroup());
			tree.computeIfAbsent(where[0], c -> new java.util.LinkedHashMap<>())
				.computeIfAbsent(where[1] != null ? where[1] : "", g -> new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER))
				.computeIfAbsent(r.getBoss(), b -> new ArrayList<>()).add(r);
		}
		if (tree.isEmpty())
		{
			draw(column, in, new Ui(PBS, null), uiActions, actions);
			return;
		}
		// A category with some grouped bosses puts the rest under Other.
		for (java.util.Map<String, java.util.Map<String, List<ClanData.PbRecord>>> groupsOf : tree.values())
		{
			if (groupsOf.size() > 1 && groupsOf.containsKey(""))
			{
				java.util.Map<String, List<ClanData.PbRecord>> loose = groupsOf.remove("");
				java.util.Map<String, List<ClanData.PbRecord>> other = groupsOf.computeIfAbsent(BossGroups.OTHER,
					g -> new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER));
				loose.forEach((b, list) -> other.computeIfAbsent(b, x -> new ArrayList<>()).addAll(list));
			}
		}
		List<String> cats = new ArrayList<>(tree.keySet());
		List<String> seen = new ArrayList<>(cats);
		cats.sort(java.util.Comparator.comparingInt((String c) -> BossGroups.categoryOrder(c)).thenComparingInt(seen::indexOf));
		String cat = ui.getCategory() != null && cats.contains(ui.getCategory()) ? ui.getCategory() : cats.get(0);
		java.util.Map<String, java.util.Map<String, List<ClanData.PbRecord>>> groups = tree.get(cat);
		List<String> groupNames = new ArrayList<>(groups.keySet());
		List<String> groupSeen = new ArrayList<>(groupNames);
		groupNames.sort(java.util.Comparator.comparingInt((String g) -> BossGroups.groupOrder(g)).thenComparingInt(groupSeen::indexOf));
		String group = ui.getGroup() != null && groupNames.contains(ui.getGroup()) ? ui.getGroup() : groupNames.get(0);
		java.util.Map<String, List<ClanData.PbRecord>> bosses = groups.get(group);
		List<String> bossNames = new ArrayList<>(bosses.keySet());
		String boss = ui.getBoss() != null && bossNames.contains(ui.getBoss()) ? ui.getBoss() : bossNames.get(0);
		List<ClanData.PbRecord> sizes = bosses.get(boss);
		sizes.sort((a, b) -> Integer.compare(sizeOrder(a.getTeamSize()), sizeOrder(b.getTeamSize())));
		ClanData.PbRecord record = sizes.get(0);
		for (ClanData.PbRecord r : sizes)
		{
			if (java.util.Objects.equals(r.getTeamSize(), ui.getTeamSize()))
			{
				record = r;
			}
		}
		String groupPick = group.isEmpty() ? null : group;

		javax.swing.JComponent catBox = PanelParts.dropdown(cats, cats.indexOf(cat), i -> pbActions.pick(cats.get(i), null, null, null));
		javax.swing.JComponent groupBox = groupNames.size() > 1 || !group.isEmpty()
			? PanelParts.dropdown(groupNames, groupNames.indexOf(group), i -> pbActions.pick(cat, groupNames.get(i), null, null)) : null;
		javax.swing.JComponent sizeBox = null;
		if (sizes.size() > 1)
		{
			List<String> sizeLabels = new ArrayList<>();
			sizes.forEach(r -> sizeLabels.add(sizeLabel(r.getTeamSize())));
			sizeBox = PanelParts.dropdown(sizeLabels, sizes.indexOf(record), i -> pbActions.pick(cat, groupPick, boss, sizes.get(i).getTeamSize()));
		}
		// Category and group side by side, then the boss across the width; team size goes beside the category when
		// there's no group, otherwise on its own line.
		addCard(column, PanelParts.dropdowns(catBox, groupBox != null ? groupBox : sizeBox));
		column.add(javax.swing.Box.createVerticalStrut(6));
		javax.swing.JComponent bossBox = PanelParts.dropdowns(PanelParts.dropdown(bossNames, bossNames.indexOf(boss),
			i -> pbActions.pick(cat, groupPick, bossNames.get(i), null)), null);
		bossBox.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		column.add(bossBox);
		if (groupBox != null && sizeBox != null)
		{
			column.add(javax.swing.Box.createVerticalStrut(6));
			javax.swing.JComponent sizeRow = PanelParts.dropdowns(sizeBox, null);
			sizeRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			column.add(sizeRow);
		}

		List<ClanData.StatEntry> entries = new ArrayList<>();
		for (ClanData.StatEntry e : record.getEntries())
		{
			if (e != null && e.getSeconds() != null && e.getSeconds() > 0)
			{
				entries.add(e);
			}
		}
		entries.sort((a, b) -> Double.compare(a.getSeconds(), b.getSeconds()));
		if (entries.isEmpty())
		{
			return;
		}
		ClanData.StatEntry best = entries.get(0);
		JLabel icon = PanelParts.placeBox(1, 38);
		icon.setText("");
		actions.bossIcon(icon, boss);
		String when = best.time() != null ? " · " + ClanView.ago(best.time(), in.getNow()) : "";
		boolean bestMine = me != null && DropRecorder.sameRsn(best.getRsn(), me);
		Card top = PanelParts.firstPlace(icon, "CLAN RECORD · " + sizeLabel(record.getTeamSize()).toUpperCase(Locale.UK),
			time(best.getSeconds()), (best.getRsn() != null ? best.getRsn() : "?") + (bestMine ? "  (you)" : "") + when);
		if (best.getRsn() != null)
		{
			top.onClick(() -> pbActions.openProfile(best.getRsn()), "See " + best.getRsn() + "'s profile");
		}
		column.add(javax.swing.Box.createVerticalStrut(PanelParts.GAP));
		column.add(top);
		if (entries.size() > 1)
		{
			column.add(javax.swing.Box.createVerticalStrut(PanelParts.GAP));
			JLabel heading = PanelParts.section("Top " + Math.min(10, entries.size()));
			heading.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			column.add(heading);
		}
		for (int i = 1; i < Math.min(10, entries.size()); i++)
		{
			ClanData.StatEntry e = entries.get(i);
			boolean mine = me != null && DropRecorder.sameRsn(e.getRsn(), me);
			Card card = PanelParts.placeCard(i + 1, e.getRsn(), time(e.getSeconds()), PanelParts.HIGHLIGHT, mine);
			if (e.getRsn() != null)
			{
				card.onClick(() -> pbActions.openProfile(e.getRsn()), "See " + e.getRsn() + "'s profile");
			}
			column.add(javax.swing.Box.createVerticalStrut(4));
			column.add(card);
		}
		addCard(column, footer(in, actions));
	}

	/** Solo first, then by size; ranges like "11-15" by their first number. */
	private static int sizeOrder(String teamSize)
	{
		if (teamSize == null || teamSize.isEmpty())
		{
			return 1;
		}
		try
		{
			return Integer.parseInt(teamSize.split("[^0-9]")[0]);
		}
		catch (NumberFormatException e)
		{
			return 99;
		}
	}

	static String title(String page, String minimum)
	{
		switch (page(page))
		{
			case CLOGS:
				return "New collection log slots";
			case PBS:
				return "New personal bests";
			case PETS:
				return "Recent pets";
			default:
				String min = minimum(minimum);
				return min.equals(ALL) ? "Recent drops" : "Recent drops: " + MINIMUM_LABELS.get(MINIMUMS.indexOf(min));
		}
	}

	private static String empty(String page, String minimum)
	{
		switch (page(page))
		{
			case CLOGS:
				return "No new collection log slots yet.";
			case PBS:
				return "No new PBs yet.";
			case PETS:
				return "No pets yet.";
			default:
				String min = minimum(minimum);
				return min.equals(ALL) ? "No drops yet." : "No drops worth " + MINIMUM_LABELS.get(MINIMUMS.indexOf(min)) + " yet.";
		}
	}

	/** The top line's name: "3 x Dragon bones", "Theatre of Blood (4 players)". */
	static String name(ClanData.StatEntry e, String page)
	{
		if (PBS.equals(page))
		{
			String boss = e.getBoss() != null ? e.getBoss() : "A boss";
			String team = e.getTeamSize();
			if (team == null || team.isEmpty())
			{
				return boss;
			}
			return boss + (team.equals("1") ? " (solo)" : " (" + team + " players)");
		}
		String item = e.getItem() != null ? e.getItem() : PETS.equals(page) ? "A pet" : "An item";
		return e.getQuantity() != null && e.getQuantity() > 1
			? QuantityFormatter.formatNumber(e.getQuantity()) + " x " + item : item;
	}

	/** The top line's right side: the value, the PB time, or "Duplicate" for a pet already owned. */
	static String right(ClanData.StatEntry e, String page)
	{
		if (PBS.equals(page))
		{
			return e.getSeconds() != null && e.getSeconds() > 0 ? time(e.getSeconds()) : "";
		}
		if (PETS.equals(page))
		{
			return Boolean.TRUE.equals(e.getDuplicate()) ? "Duplicate" : "";
		}
		return e.getValue() != null && e.getValue() > 0 ? QuantityFormatter.quantityToStackSize(e.getValue()) : "";
	}

	/** The second line: who, and what from or at what kill count. How long ago goes on its right. */
	static String detail(ClanData.StatEntry e, String page)
	{
		List<String> parts = new ArrayList<>();
		parts.add(e.getRsn() != null ? e.getRsn() : "Someone");
		if (!PBS.equals(page) && e.getSource() != null && !e.getSource().isEmpty())
		{
			parts.add(e.getSource());
		}
		if (e.getKc() != null && e.getKc() > 0)
		{
			parts.add(QuantityFormatter.formatNumber(e.getKc()) + " kc");
		}
		return String.join(" · ", parts);
	}

	/** "1:23.40", "12:05", "1:02:03". Whole seconds unless the time has hundredths. */
	static String time(double seconds)
	{
		int whole = (int) seconds;
		String time = whole >= 3600
			? String.format(Locale.UK, "%d:%02d:%02d", whole / 3600, whole / 60 % 60, whole % 60)
			: String.format(Locale.UK, "%d:%02d", whole / 60, whole % 60);
		long hundredths = Math.round((seconds - whole) * 100);
		return hundredths > 0 && hundredths < 100 ? time + String.format(Locale.UK, ".%02d", hundredths) : time;
	}

	/** The item's picture on the left, then the name and value, and who got it and when underneath. */
	private static JPanel row(ClanData.StatEntry e, String page, Input in, Actions actions)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		String name = name(e, page);
		String right = right(e, page);
		String detail = detail(e, page);
		String ago = ClanView.ago(e.time(), in.getNow());
		String tip = name + (right.isEmpty() ? "" : " · " + right) + " · " + detail + (ago.isEmpty() ? "" : " · " + ago);
		row.setToolTipText(tip);
		if (PBS.equals(page))
		{
			JLabel icon = new JLabel();
			icon.setPreferredSize(BOSS_ICON);
			icon.setMinimumSize(BOSS_ICON);
			icon.setHorizontalAlignment(JLabel.CENTER);
			icon.setVerticalAlignment(JLabel.TOP);
			icon.setToolTipText(tip);
			if (e.getBoss() != null)
			{
				actions.bossIcon(icon, e.getBoss());
			}
			row.add(icon, BorderLayout.WEST);
		}
		else
		{
			JLabel icon = new JLabel()
			{
				@Override
				protected void paintComponent(java.awt.Graphics g)
				{
					PanelParts.roundBox(g, getWidth(), getHeight(), PanelParts.BACKGROUND, null);
					super.paintComponent(g);
				}
			};
			icon.setPreferredSize(ICON);
			icon.setMinimumSize(ICON);
			icon.setHorizontalAlignment(JLabel.CENTER);
			icon.setToolTipText(tip);
			if (e.getItemId() != null && e.getItemId() > 0)
			{
				actions.icon(icon, e.getItemId(), e.getQuantity() != null ? Math.max(e.getQuantity(), 1) : 1);
			}
			row.add(icon, BorderLayout.WEST);
		}
		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		lines.setBorder(new EmptyBorder(PBS.equals(page) ? 0 : 2, 0, 0, 0));
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		JLabel title = cut(name, TEXT, tip);
		title.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
		top.add(title, BorderLayout.CENTER);
		if (!right.isEmpty())
		{
			top.add(plain(safe(right), PBS.equals(page) ? PanelParts.HIGHLIGHT : PanelParts.GOLD), BorderLayout.EAST);
		}
		top.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		lines.add(top);
		JPanel under = new JPanel(new BorderLayout(4, 0));
		under.setOpaque(false);
		under.add(cut(detail, QUIET, tip), BorderLayout.CENTER);
		if (!ago.isEmpty())
		{
			under.add(plain(ago, QUIET), BorderLayout.EAST);
		}
		under.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		lines.add(under);
		List<String> tags = tags(e, page);
		if (!tags.isEmpty())
		{
			JPanel tagRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
			tagRow.setOpaque(false);
			tagRow.setBorder(new EmptyBorder(2, 0, 0, 0));
			for (String t : tags)
			{
				tagRow.add(PanelParts.tag(t, tagColour(t)));
				tagRow.add(javax.swing.Box.createHorizontalStrut(3));
			}
			tagRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			lines.add(tagRow);
		}
		row.add(lines, BorderLayout.CENTER);
		return row;
	}

	/** One line of text that is cut short with "..." when it doesn't fit. */
	private static JLabel cut(String text, java.awt.Color colour, String tip)
	{
		JLabel label = plain(safe(text), colour);
		label.setToolTipText(tip);
		label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
		label.setPreferredSize(new Dimension(0, label.getPreferredSize().height));
		return label;
	}

	/** Names come from the website; a label starting with "<html>" would be drawn as HTML. */
	private static String safe(String text)
	{
		return text.regionMatches(true, 0, "<html", 0, 5) ? " " + text : text;
	}

	private static JPanel footer(Input in, Actions actions)
	{
		JPanel footer = PanelParts.column();
		if (in.getError() != null && !in.isUnsupported())
		{
			footer.add(small(in.getError(), BAD));
		}
		String updated = in.getFetchedAt() != null ? "Updated " + ClanView.clock(in.getFetchedAt(), in.getZone()) : "Loading…";
		JPanel row = PanelParts.spread(plain(updated, QUIET), PanelParts.link("Refresh", actions::refresh));
		row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		footer.add(row);
		return footer;
	}
}
