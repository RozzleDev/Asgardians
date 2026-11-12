package com.asgardians;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Clanmates on the world map: a dot and their name where they last were, for clanmates who share their location. Each
 * clanmate's own client sends only their own position; this only draws what the website hands back. By default only
 * clanmates on the member's own world show, so a busy clan doesn't cover the map. Client thread.
 */
final class ClanMap
{
	@Value
	static class Pin
	{
		String name;
		/** 0 when not known. */
		int world;
		WorldPoint at;
		/** What they're doing, or null. */
		String activity;
	}

	private static final Color SAME_WORLD = new Color(0x3F, 0xB9, 0x50);
	private static final Color OTHER_WORLD = PanelParts.HIGHLIGHT;
	private static final Color PILL = new Color(0x1E, 0x1E, 0x1E, 225);
	private static final int DOT = 8;

	private final WorldMapPointManager manager;
	private final Map<String, BufferedImage> images = new HashMap<>();
	private final List<WorldMapPoint> points = new ArrayList<>();

	ClanMap(WorldMapPointManager manager)
	{
		this.manager = manager;
	}

	/** The pins to draw: not the member themself, and only their own world when that's chosen. */
	static List<Pin> shown(List<Pin> all, String me, int myWorld, boolean onlyMyWorld)
	{
		List<Pin> out = new ArrayList<>();
		for (Pin p : all)
		{
			if (p == null || p.getAt() == null || p.getName() == null || (me != null && DropRecorder.sameRsn(p.getName(), me)))
			{
				continue;
			}
			if (onlyMyWorld && (myWorld <= 0 || p.getWorld() != myWorld))
			{
				continue;
			}
			out.add(p);
		}
		return out;
	}

	/** Shows these clanmates on the map in place of the last lot. */
	void show(List<Pin> pins, int myWorld)
	{
		clear();
		for (Pin p : pins)
		{
			boolean same = p.getWorld() > 0 && p.getWorld() == myWorld;
			BufferedImage image = images.computeIfAbsent(p.getName() + same, k -> marker(p.getName(), same ? SAME_WORLD : OTHER_WORLD));
			WorldMapPoint point = new WorldMapPoint(p.getAt(), image);
			point.setImagePoint(new Point(DOT / 2, image.getHeight() / 2));
			point.setName(p.getName());
			point.setTooltip(tooltip(p));
			manager.add(point);
			points.add(point);
		}
	}

	void clear()
	{
		for (WorldMapPoint point : points)
		{
			manager.remove(point);
		}
		points.clear();
	}

	static String tooltip(Pin p)
	{
		return p.getName() + (p.getWorld() > 0 ? " · W" + p.getWorld() : "") + (p.getActivity() != null ? " · " + p.getActivity() : "");
	}

	/** A coloured dot with the name in a dark pill beside it. */
	static BufferedImage marker(String name, Color dot)
	{
		java.awt.Font font = FontManager.getRunescapeSmallFont();
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		FontMetrics fm = pg.getFontMetrics(font);
		pg.dispose();
		int textW = fm.stringWidth(name);
		int h = Math.max(DOT, fm.getAscent() + 4);
		BufferedImage img = new BufferedImage(DOT + 3 + textW + 6, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(PILL);
		g.fillRoundRect(DOT + 1, 0, textW + 8, h, 6, 6);
		g.setColor(Color.BLACK);
		g.fillOval(0, (h - DOT) / 2, DOT, DOT);
		g.setColor(dot);
		g.fillOval(1, (h - DOT) / 2 + 1, DOT - 2, DOT - 2);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString(name, DOT + 5, (h + fm.getAscent()) / 2 - 1);
		g.dispose();
		return img;
	}
}
