package com.asgardians;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import lombok.Value;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows new groups on screen for a short while, for the activities the member has the screen icon on for in the
 * Groups tab's "Group alerts". The newest is at the top, and only a few show at once.
 */
class GroupAlertOverlay extends OverlayPanel
{
	static final long SHOW_MILLIS = 20_000;
	static final int MAX_SHOWN = 3;

	@Value
	static class Alert
	{
		String host;
		String title;
		String seats;
		String world;
		long until;
	}

	private final BooleanSupplier enabled;
	private final LongSupplier clock;
	private final Deque<Alert> alerts = new ArrayDeque<>();

	GroupAlertOverlay(AsgardiansPlugin plugin, BooleanSupplier enabled, LongSupplier clock)
	{
		super(plugin);
		this.enabled = enabled;
		this.clock = clock;
		setPosition(OverlayPosition.TOP_CENTER);
		// Wide enough for "Theatre of Blood (Hard mode)" on one line; RuneLite's default wraps it.
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	static final int WIDTH = 210;

	synchronized void add(ClanData.Group g)
	{
		add(g, g.getTitle());
	}

	/** @param title the group's title, short where the website gives short names */
	synchronized void add(ClanData.Group g, String title)
	{
		alerts.addFirst(new Alert(g.getHost() != null ? g.getHost() : "Someone", title, g.seats(), Groups.world(g),
			clock.getAsLong() + SHOW_MILLIS));
		while (alerts.size() > MAX_SHOWN)
		{
			alerts.removeLast();
		}
	}

	synchronized void clear()
	{
		alerts.clear();
	}

	/** The alerts still showing, newest first. */
	synchronized List<Alert> showing()
	{
		long now = clock.getAsLong();
		alerts.removeIf(a -> a.getUntil() <= now);
		return new ArrayList<>(alerts);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<Alert> shown = showing();
		if (!enabled.getAsBoolean() || shown.isEmpty())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(shown.size() == 1 ? "New clan group" : "New clan groups").color(PanelParts.HIGHLIGHT).build());
		for (Alert a : shown)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(a.getTitle()).right(a.getSeats())
				.rightColor(PanelParts.HIGHLIGHT).build());
			panelComponent.getChildren().add(LineComponent.builder().left(a.getHost()).right(a.getWorld())
				.leftColor(PanelParts.QUIET).rightColor(PanelParts.QUIET).build());
		}
		panelComponent.getChildren().add(LineComponent.builder().left("Join from the Groups tab").leftColor(PanelParts.QUIET).build());
		return super.render(graphics);
	}
}
