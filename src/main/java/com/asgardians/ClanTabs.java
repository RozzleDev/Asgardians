package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.TODO;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.badge;
import static com.asgardians.PanelParts.button;
import static com.asgardians.PanelParts.dotRow;
import static com.asgardians.PanelParts.heading;
import static com.asgardians.PanelParts.small;
import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JPanel;
import lombok.Value;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/** Builds the Events and News tabs from a {@link ClanView}. Swing thread only. */
final class ClanTabs
{
	/** What the two tabs need besides the view itself. */
	@Value
	static class Input
	{
		ClanView view;
		/** Online clan members: lowercased RSN to world. */
		Map<String, Integer> online;
		long lastReadAnnouncementId;
		/** Null when connected; otherwise why the tabs can't load. */
		String notConnected;
		ZoneId zone;
		Instant now;
		/** The API address, whose domain links must be on. */
		String address;
		/** Sign-up lists loaded so far, by event ID. */
		Map<String, ClanData.SignupList> signupLists;
		/** Events whose "Who's signed up" list is open. */
		java.util.Set<String> expanded;
		/** Events with a sign-up or withdraw request in flight. */
		java.util.Set<String> busy;
	}

	/** What the sign-up buttons do. Called on the Swing thread. */
	interface Actions
	{
		void signUp(ClanData.Event event);

		void withdraw(ClanData.Event event);

		void toggleSignups(ClanData.Event event);
	}

	/**
	 * Links come from the server, so only http(s) links on the clan website's own domain (or its subdomains) are opened,
	 * never anything else a compromised or mistaken server might send.
	 */
	static boolean safeLink(String url, String address)
	{
		okhttp3.HttpUrl link = url != null ? okhttp3.HttpUrl.parse(url) : null;
		okhttp3.HttpUrl api = address != null ? okhttp3.HttpUrl.parse(address) : null;
		if (link == null || api == null)
		{
			return false;
		}
		String site = siteDomain(api.host());
		String host = link.host();
		return host.equals(site) || host.endsWith("." + site);
	}

	/** "dev.asgardiansclan.com" and "asgardiansclan.com" both give "asgardiansclan.com"; IPs and localhost stay as they are. */
	static String siteDomain(String host)
	{
		if (host.matches("[0-9.]+") || host.contains(":") || !host.contains("."))
		{
			return host;
		}
		String[] parts = host.split("\\.");
		return parts[parts.length - 2] + "." + parts[parts.length - 1];
	}

	/** A link to a page on the clan website, or null when it isn't one the plugin will open. */
	private static javax.swing.JLabel websiteLink(String text, String url, Input in)
	{
		return safeLink(url, in.getAddress()) ? PanelParts.link(text, () -> LinkBrowser.browse(url)) : null;
	}

	private static final float LEFT = java.awt.Component.LEFT_ALIGNMENT;

	private ClanTabs()
	{
	}

	static void events(JPanel column, Input in, Runnable refresh, Actions actions)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("Events");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		ClanView view = in.getView();
		for (String group : ClanView.GROUPS)
		{
			List<ClanView.EventView> events = view.inGroup(group);
			if (events.isEmpty())
			{
				continue;
			}
			addCard(column, PanelParts.section(ClanView.groupLabel(group)));
			// Anything with something to do or see gets its own card; the rest is a schedule.
			List<ClanView.EventView> schedule = new ArrayList<>();
			for (ClanView.EventView event : events)
			{
				boolean competition = "botw".equals(group) || "sotw".equals(group);
				if (event.getEvent().getSignup() != null || (event.getEvent().isLive() && !competition))
				{
					addCard(column, eventCard(event, in, actions));
				}
				else
				{
					schedule.add(event);
				}
			}
			if (!schedule.isEmpty())
			{
				addCard(column, schedule(schedule, in));
			}
		}
		if (view.getFetchedAt() != null && view.getEvents().isEmpty())
		{
			Card card = new Card("Events");
			card.add(small("No events running or coming up right now.", QUIET));
			addCard(column, card);
		}
		addCard(column, footer(view, in, refresh));
	}

	static void news(JPanel column, Input in, Runnable refresh)
	{
		column.removeAll();
		if (in.getNotConnected() != null)
		{
			Card card = new Card("News");
			card.add(small(in.getNotConnected(), QUIET));
			addCard(column, card);
			return;
		}
		List<ClanData.Announcement> items = new ArrayList<>(in.getView().getAnnouncements());
		items.sort((a, b) -> Long.compare(b.getId(), a.getId()));
		for (ClanData.Announcement a : items)
		{
			Card card = new Card(a.getTitle());
			if (a.getId() > in.getLastReadAnnouncementId())
			{
				card.add(badge("NEW", HIGHLIGHT));
			}
			String by = a.getPostedBy() != null ? " by " + a.getPostedBy() : "";
			card.add(small(ClanView.localTime(a.posted(), in.getZone()) + by, QUIET));
			card.gap();
			card.add(small(a.getBody(), TEXT));
			javax.swing.JLabel more = websiteLink("Read more", a.getUrl(), in);
			if (more != null)
			{
				card.gap();
				card.addWide(PanelParts.spread(null, more));
			}
			addCard(column, card);
		}
		if (in.getView().getFetchedAt() != null && items.isEmpty())
		{
			Card card = new Card("News");
			card.add(small("No announcements yet.", QUIET));
			addCard(column, card);
		}
		addCard(column, footer(in.getView(), in, refresh));
	}

	// ---- events ----

	private static Card eventCard(ClanView.EventView live, Input in, Actions actions)
	{
		ClanData.Event event = live.getEvent();
		Card card = new Card(event.getName());
		addWhen(card, event, in);
		String note = event.getSignup() != null ? event.getSignup().getNote() : null;
		if (event.getSummary() != null && !event.getSummary().equals(note))
		{
			card.add(small(event.getSummary(), TEXT));
		}
		if (event.getCodeword() != null)
		{
			card.gap();
			card.add(small("Codeword: " + event.getCodeword(), HIGHLIGHT));
		}
		signups(card, event, in, actions);

		ClanData.Team team = live.getTeam();
		if (team != null)
		{
			card.gap();
			card.add(heading("Your team: " + team.getName()));
			String standing = standing(team);
			if (!standing.isEmpty())
			{
				card.add(small(standing, QUIET));
			}
			for (String member : ClanView.orEmpty(team.getMembers()))
			{
				Integer world = in.getOnline().get(member.toLowerCase(Locale.UK));
				String name = member + (member.equalsIgnoreCase(team.getCaptain()) ? " ★" : "");
				card.addWide(dotRow(world != null ? GOOD : QUIET, name, world != null ? "W" + world : "offline",
					world != null ? "Online on world " + world : "Offline"));
			}
		}
		else if (!event.isIn() && event.getSignup() == null)
		{
			card.gap();
			card.add(small("You're not signed up for this one.", QUIET));
		}

		if (live.getStandings().size() > 1)
		{
			card.gap();
			card.add(heading("Standings"));
			List<ClanData.Team> sorted = new ArrayList<>(live.getStandings());
			sorted.sort((a, b) -> Integer.compare(a.getRank() != null ? a.getRank() : 999, b.getRank() != null ? b.getRank() : 999));
			for (ClanData.Team t : sorted)
			{
				boolean mine = team != null && t.getId() != null && t.getId().equals(team.getId());
				String rank = t.getRank() != null ? "#" + t.getRank() + " " : "";
				card.addWide(dotRow(colour(t.getColour(), mine ? HIGHLIGHT : QUIET), rank + t.getName(), points(t), null));
			}
		}

		ClanData.Board board = live.getBoard();
		if (board != null && board.getTiles() != null && !board.getTiles().isEmpty())
		{
			card.gap();
			String progress = board.getDone() != null && board.getTotal() != null
				? " · " + board.getDone() + " / " + board.getTotal() : "";
			card.add(heading("Your board" + progress));
			addTiles(card, board.getTiles());
			javax.swing.JLabel legend = new javax.swing.JLabel(PanelParts.wrap(PanelParts.dot(GOOD) + " done &nbsp;"
				+ PanelParts.dot(TODO) + " pending &nbsp;" + PanelParts.dot(BAD) + " declined &nbsp;"
				+ PanelParts.dot(QUIET) + " needed"));
			legend.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			legend.setForeground(QUIET);
			card.add(legend);
		}

		if (event.getSignup() == null)
		{
			card.gap();
			card.addWide(PanelParts.spread(null, websiteLink("Open on website", event.getUrl(), in)));
		}
		return card;
	}

	/** The countdown, with the exact time on its own line so it doesn't wrap mid-time. */
	private static void addWhen(Card card, ClanData.Event event, Input in)
	{
		card.add(PanelParts.status(event.isLive() ? GOOD : QUIET, event.isLive()
			? "Live, ends in " + ClanView.countdown(in.getNow(), event.end())
			: "Starts in " + ClanView.countdown(in.getNow(), event.start())));
		card.add(small(ClanView.localTime(event.isLive() ? event.end() : event.start(), in.getZone()), QUIET));
	}

	/** "Live, ends in 1d 7h (Tue 3 Nov 00:00)" or "Starts in 4d 7h (Fri 6 Nov 00:00)". */
	private static String when(ClanData.Event event, Input in)
	{
		return event.isLive()
			? "Live, ends in " + ClanView.countdown(in.getNow(), event.end()) + " (" + ClanView.localTime(event.end(), in.getZone()) + ")"
			: "Starts in " + ClanView.countdown(in.getNow(), event.start()) + " (" + ClanView.localTime(event.start(), in.getZone()) + ")";
	}

	private static void addTiles(Card card, List<ClanData.Tile> tiles)
	{
		Map<String, List<ClanData.Tile>> groups = new LinkedHashMap<>();
		for (ClanData.Tile tile : tiles)
		{
			groups.computeIfAbsent(tile.getGroup() != null ? tile.getGroup() : "", g -> new ArrayList<>()).add(tile);
		}
		groups.forEach((group, list) ->
		{
			if (!group.isEmpty())
			{
				card.add(small(group, TEXT));
			}
			for (ClanData.Tile tile : list)
			{
				String tip = tile.getName() + " — " + tile.getState()
					+ (tile.getDescription() != null ? "\n" + tile.getDescription() : "");
				String right = tile.getPoints() != null ? "+" + trim(tile.getPoints()) : null;
				card.addWide(dotRow(tileColour(tile.getState()), tile.getName(), right, tip));
			}
		});
	}

	/** A compact list: what's running now, then what's next, with dates on the right. Hover for the full time. */
	private static Card schedule(List<ClanView.EventView> events, Input in)
	{
		Card card = new Card(null);
		for (ClanView.EventView view : events)
		{
			ClanData.Event event = view.getEvent();
			String tip = when(event, in);
			if (event.isLive())
			{
				card.add(heading(event.getName()));
				addWhen(card, event, in);
				card.gap();
			}
			else
			{
				String name = event.getName() + (event.isIn() ? " ✔" : "");
				card.addWide(dotRow(event.isIn() ? GOOD : QUIET, name, ClanView.shortDate(event.start(), in.getZone()),
					tip + (event.isIn() ? ". You'll get a reminder 15 minutes before." : "")));
			}
		}
		return card;
	}

	/** Sign-up state, the member's own status, Sign up / Withdraw, and the list of who's signed up. */
	private static void signups(Card card, ClanData.Event event, Input in, Actions actions)
	{
		ClanData.Signup s = event.getSignup();
		if (s == null)
		{
			return;
		}
		card.gap();
		boolean full = s.getCap() != null && s.getCount() != null && s.getCount() >= s.getCap();
		String state = !s.isOpen() ? "Sign-ups closed"
			: s.closes() != null ? "Sign-ups close in " + ClanView.countdown(in.getNow(), s.closes()) : "Sign-ups open";
		card.add(PanelParts.status(s.isOpen() ? GOOD : QUIET, state));
		if (s.getCount() != null)
		{
			card.add(small(s.getCount() + (s.getCap() != null ? " of " + s.getCap() : "") + " signed up", QUIET));
		}
		if (s.getNote() != null)
		{
			card.add(small(s.getNote(), TEXT));
		}

		if (s.isSignedUp())
		{
			String status = "waitlist".equals(s.getMyStatus()) ? "You're on the waitlist"
				: "confirmed".equals(s.getMyStatus()) ? "You're confirmed" : "You're signed up";
			card.add(small("✔ " + status, GOOD));
			if (s.getPaid() != null)
			{
				card.add(small(s.getPaid() ? "Buy-in paid" : "Buy-in not paid yet", s.getPaid() ? GOOD : TODO));
			}
		}

		if (in.getBusy().contains(event.getId()))
		{
			card.add(small("Sending…", QUIET));
		}
		else if (s.isOpen())
		{
			card.gap();
			if (s.isSignedUp())
			{
				card.addWide(button("Withdraw", () -> actions.withdraw(event)));
			}
			else
			{
				card.addWide(button(full ? "Join the waitlist" : "Sign up", () -> actions.signUp(event)));
			}
		}

		boolean open = in.getExpanded().contains(event.getId());
		String count = s.getCount() != null ? " (" + s.getCount() + ")" : "";
		card.gap();
		card.addWide(PanelParts.spread(
			PanelParts.link((open ? "Hide sign-ups" : "See who's signed up") + count, () -> actions.toggleSignups(event)),
			websiteLink("Website", event.getUrl(), in)));
		if (open)
		{
			ClanData.SignupList list = in.getSignupLists().get(event.getId());
			if (list == null)
			{
				card.add(small("Loading…", QUIET));
			}
			else if (ClanView.orEmpty(list.getSignups()).isEmpty())
			{
				card.add(small("Nobody yet.", QUIET));
			}
			else
			{
				for (ClanData.SignupEntry entry : list.getSignups())
				{
					Color dot = entry.getPaid() == null ? QUIET : entry.getPaid() ? GOOD : TODO;
					String name = entry.getRsn() + (entry.getTeam() != null ? " · " + entry.getTeam() : "");
					String tip = entry.getPaid() == null ? null : entry.getPaid() ? "Buy-in paid" : "Buy-in not paid yet";
					card.addWide(dotRow(dot, name, statusLabel(entry.getStatus()), tip));
				}
			}
		}
	}

	private static String statusLabel(String status)
	{
		if ("waitlist".equals(status))
		{
			return "waitlist";
		}
		return "confirmed".equals(status) ? "confirmed" : "";
	}

	/** One quiet line under the cards: when it was last loaded and a Refresh link, plus any problem loading. */
	private static JPanel footer(ClanView view, Input in, Runnable refresh)
	{
		JPanel footer = PanelParts.column();
		if (view.getError() != null)
		{
			footer.add(small(view.getError(), BAD));
		}
		String updated = view.getFetchedAt() != null
			? "Updated " + ClanView.clock(view.getFetchedAt(), in.getZone())
			: "Loading…";
		JPanel row = PanelParts.spread(PanelParts.plain(updated, QUIET), PanelParts.link("Refresh", refresh));
		row.setAlignmentX(LEFT);
		row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		footer.add(row);
		return footer;
	}

	private static String standing(ClanData.Team team)
	{
		StringBuilder out = new StringBuilder();
		if (team.getRank() != null)
		{
			out.append("Rank #").append(team.getRank());
		}
		String points = points(team);
		if (points != null)
		{
			out.append(out.length() > 0 ? " · " : "").append(points);
		}
		return out.toString();
	}

	private static String points(ClanData.Team team)
	{
		if (team.getPoints() == null)
		{
			return null;
		}
		String value = team.getPoints() >= 100_000
			? QuantityFormatter.quantityToStackSize(Math.round(team.getPoints()))
			: trim(team.getPoints());
		return value + (team.getPointsLabel() != null ? " " + team.getPointsLabel() : "");
	}

	private static String trim(double value)
	{
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private static Color tileColour(String state)
	{
		if (state == null)
		{
			return QUIET;
		}
		switch (state)
		{
			case "accepted":
				return GOOD;
			case "pending":
				return TODO;
			case "declined":
				return BAD;
			default:
				return QUIET;
		}
	}

	private static Color colour(String hex, Color fallback)
	{
		try
		{
			return hex != null ? Color.decode(hex) : fallback;
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}
