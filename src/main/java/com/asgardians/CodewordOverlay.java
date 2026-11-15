package com.asgardians;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.awt.Color;
import lombok.Value;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * While a clan event the member is in is live, shows its codeword and the current UTC time; the detailed box also
 * shows every team's score and the time left. Overlays are part of
 * RuneLite screenshots, so staff can check a drop screenshot was taken during the event.
 */
class CodewordOverlay extends OverlayPanel
{
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("d MMM HH:mm:ss 'UTC'", Locale.UK)
		.withZone(ZoneOffset.UTC);

	private final AsgardiansConfig config;
	private final Supplier<ClanView> view;

	CodewordOverlay(AsgardiansPlugin plugin, AsgardiansConfig config, Supplier<ClanView> view)
	{
		super(plugin);
		this.config = config;
		this.view = view;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showCodeword())
		{
			return null;
		}
		// The detailed box keeps RuneLite's usual overlay width.
		panelComponent.setPreferredSize(new Dimension(ComponentConstants.STANDARD_WIDTH, 0));
		ClanView current = view.get();
		if (!config.codewordDetailed())
		{
			return simple(graphics, current);
		}
		boolean any = false;
		for (ClanView.EventView live : current.live())
		{
			ClanData.Event event = live.getEvent();
			if (event.getCodeword() == null || !event.isIn())
			{
				continue;
			}
			if (!any)
			{
				panelComponent.getChildren().add(TitleComponent.builder().text("Asgardians").color(PanelParts.HIGHLIGHT).build());
				any = true;
			}
			panelComponent.getChildren().add(LineComponent.builder().left(event.getName()).build());
			panelComponent.getChildren().add(LineComponent.builder().left("Codeword:").right(event.getCodeword())
				.rightColor(PanelParts.HIGHLIGHT).build());
			List<Score> scores = scores(live);
			for (Score s : scores)
			{
				panelComponent.getChildren().add(LineComponent.builder().left(s.getName()).leftColor(s.getColour())
					.right(s.getPoints()).rightColor(s.isMine() ? PanelParts.HIGHLIGHT : java.awt.Color.WHITE).build());
			}
			if (!scores.isEmpty())
			{
				panelComponent.setPreferredSize(new Dimension(SCORES_WIDTH, 0));
			}
			if (event.end() != null && (event.isHasBoard() || !scores.isEmpty()))
			{
				panelComponent.getChildren().add(LineComponent.builder().left("Ends in:")
					.right(ClanView.countdown(Instant.now(), event.end())).build());
			}
		}
		if (!any)
		{
			return null;
		}
		panelComponent.getChildren().add(LineComponent.builder().left(UTC.format(Instant.now())).build());
		return super.render(graphics);
	}

	/** Just the codeword (one line per live event) and the time, in a box only as wide as they need. */
	private Dimension simple(Graphics2D graphics, ClanView current)
	{
		java.util.List<String> words = codewords(current);
		if (words.isEmpty())
		{
			return null;
		}
		String time = UTC.format(Instant.now());
		java.awt.FontMetrics fm = graphics.getFontMetrics();
		int width = fm.stringWidth(time);
		for (String w : words)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(w).leftColor(PanelParts.HIGHLIGHT).build());
			width = Math.max(width, fm.stringWidth(w));
		}
		panelComponent.getChildren().add(LineComponent.builder().left(time).build());
		panelComponent.setPreferredSize(new Dimension(width + 10, 0));
		return super.render(graphics);
	}

	/** Wide enough for a team name and its points. */
	static final int SCORES_WIDTH = 165;
	/** The most teams listed; the member's own team always makes it in. */
	static final int MAX_TEAMS = 5;

	/** One team's line in the detailed box. */
	@Value
	static class Score
	{
		String name;
		String points;
		Color colour;
		boolean mine;
	}

	/**
	 * Every team's score for a live event, best first, in the team's colour, the member's own one highlighted. Empty
	 * when the website sends no points.
	 */
	static List<Score> scores(ClanView.EventView live)
	{
		List<ClanData.Team> teams = new ArrayList<>(live.teams());
		teams.removeIf(t -> t.getPoints() == null);
		if (teams.isEmpty())
		{
			return List.of();
		}
		Map<String, Color> colours = ClanTabs.teamColours(live.teams());
		ClanData.Team own = ClanTabs.ownTeam(live);
		String ownId = own != null ? own.getId() : null;
		teams.sort(Comparator.comparing((ClanData.Team t) -> -t.getPoints())
			.thenComparing(t -> t.getRank() != null ? t.getRank() : Integer.MAX_VALUE));
		int ownAt = -1;
		for (int i = 0; i < teams.size(); i++)
		{
			ownAt = ownId != null && ownId.equals(teams.get(i).getId()) ? i : ownAt;
		}
		List<Score> out = new ArrayList<>();
		for (int i = 0; i < teams.size() && out.size() < MAX_TEAMS; i++)
		{
			// Past the cut, the member's own team takes the last place in the list.
			int at = out.size() == MAX_TEAMS - 1 && ownAt > i ? ownAt : i;
			ClanData.Team t = teams.get(at);
			Color c = colours.getOrDefault(t.getId(), Color.WHITE);
			out.add(new Score((at + 1) + ". " + (t.getName() != null ? t.getName() : "Team"), points(t.getPoints()),
				ClanTabs.readable(c), at == ownAt));
		}
		return out;
	}

	private static String points(double p)
	{
		if (p >= 100_000)
		{
			return net.runelite.client.util.QuantityFormatter.quantityToStackSize(Math.round(p));
		}
		return p == Math.floor(p) ? String.valueOf((long) p) : String.valueOf(p);
	}

	/** The codewords of live events the member is in, in order. */
	static java.util.List<String> codewords(ClanView current)
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		for (ClanView.EventView live : current.live())
		{
			ClanData.Event event = live.getEvent();
			if (event.getCodeword() != null && event.isIn())
			{
				out.add(event.getCodeword());
			}
		}
		return out;
	}
}
