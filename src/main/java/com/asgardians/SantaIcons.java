package com.asgardians;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Tiny goofy icons for the Santa Claws acts once they're unlocked (a snowman for Frosty, a lipstick for Slay…), drawn
 * by hand since the game's fonts have no emoji. Picked by the boss's name, so an admin rename still finds one; any
 * boss it doesn't know gets a gold star.
 */
final class SantaIcons
{
	enum Kind
	{
		SNOWMAN, LIPSTICK, NUTCRACKER, REINDEER, GINGERBREAD, SANTA_HAT, STAR
	}

	private static final Color WHITE = new Color(0xF4, 0xF7, 0xFA);
	private static final Color BLACK = new Color(0x1A, 0x14, 0x12);
	private static final Color ORANGE = new Color(0xF0, 0x8A, 0x24);
	private static final Color RED = new Color(0xD8, 0x26, 0x1F);
	private static final Color PINK = new Color(0xF0, 0x4E, 0xA8);
	private static final Color GOLD = new Color(0xF0, 0xB4, 0x3C);
	private static final Color SKIN = new Color(0xF2, 0xC9, 0xA0);
	private static final Color BROWN = new Color(0x9A, 0x5B, 0x2E);
	private static final Color GINGER = new Color(0xC2, 0x74, 0x34);
	private static final Color ANTLER = new Color(0xD9, 0xB8, 0x80);

	private SantaIcons()
	{
	}

	/** Which icon a boss gets, from its short name or full name. */
	static Kind kind(String shortName, String name)
	{
		String n = ((shortName != null ? shortName : "") + " " + (name != null ? name : "")).toLowerCase(Locale.UK);
		if (n.contains("frost") || n.contains("snow"))
		{
			return Kind.SNOWMAN;
		}
		if (n.contains("slay") || n.contains("drag"))
		{
			return Kind.LIPSTICK;
		}
		if (n.contains("nut"))
		{
			return Kind.NUTCRACKER;
		}
		if (n.contains("olph") || n.contains("rein") || n.contains("rude"))
		{
			return Kind.REINDEER;
		}
		if (n.contains("ginger"))
		{
			return Kind.GINGERBREAD;
		}
		if (n.contains("santa") || n.contains("claus"))
		{
			return Kind.SANTA_HAT;
		}
		return Kind.STAR;
	}

	/** The icon, {@code size} pixels square; {@code faded} for a beaten act. */
	static JComponent icon(Kind kind, int size, boolean faded)
	{
		JComponent box = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				if (faded)
				{
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
				}
				// Drawn on a 13 × 13 grid, scaled to the box.
				double s = Math.min(getWidth(), getHeight()) / 13.0;
				g2.translate((getWidth() - 13 * s) / 2, (getHeight() - 13 * s) / 2);
				g2.scale(s, s);
				draw(g2, kind);
				g2.dispose();
			}
		};
		Dimension d = new Dimension(size, size);
		box.setPreferredSize(d);
		box.setMinimumSize(d);
		box.setMaximumSize(d);
		return box;
	}

	private static void draw(Graphics2D g, Kind kind)
	{
		switch (kind)
		{
			case SNOWMAN:
				g.setColor(WHITE);
				g.fillOval(2, 6, 9, 7);
				g.fillOval(3, 2, 7, 6);
				g.setColor(BLACK);
				g.fillRect(3, 2, 7, 1);
				g.fillRect(4, 0, 5, 2);
				g.fillRect(5, 4, 1, 1);
				g.fillRect(7, 4, 1, 1);
				g.fillRect(6, 8, 1, 1);
				g.fillRect(6, 10, 1, 1);
				g.setColor(ORANGE);
				g.fillPolygon(new int[]{6, 9, 6}, new int[]{5, 6, 7}, 3);
				break;
			case LIPSTICK:
				g.setColor(PINK);
				g.fillPolygon(new int[]{4, 9, 9, 4}, new int[]{5, 1, 6, 6}, 4);
				g.setColor(GOLD);
				g.fillRect(3, 6, 7, 2);
				g.setColor(new Color(0x2B, 0x22, 0x2E));
				g.fillRect(3, 8, 7, 5);
				g.setColor(GOLD);
				g.fillRect(3, 10, 7, 1);
				break;
			case NUTCRACKER:
				g.setColor(BLACK);
				g.fillRect(4, 0, 5, 4);
				g.setColor(GOLD);
				g.fillRect(4, 3, 5, 1);
				g.setColor(SKIN);
				g.fillRect(4, 4, 5, 3);
				g.setColor(WHITE);
				g.fillRect(4, 6, 5, 1);
				g.setColor(BLACK);
				g.fillRect(5, 5, 1, 1);
				g.fillRect(7, 5, 1, 1);
				g.setColor(RED);
				g.fillRect(3, 7, 7, 4);
				g.setColor(GOLD);
				g.fillRect(6, 7, 1, 4);
				g.setColor(BLACK);
				g.fillRect(4, 11, 2, 2);
				g.fillRect(7, 11, 2, 2);
				break;
			case REINDEER:
				g.setColor(ANTLER);
				g.fillRect(2, 0, 1, 4);
				g.fillRect(1, 1, 1, 1);
				g.fillRect(3, 2, 2, 1);
				g.fillRect(10, 0, 1, 4);
				g.fillRect(11, 1, 1, 1);
				g.fillRect(8, 2, 2, 1);
				g.setColor(BROWN);
				g.fillOval(2, 3, 9, 9);
				g.setColor(BLACK);
				g.fillRect(4, 6, 1, 1);
				g.fillRect(8, 6, 1, 1);
				g.setColor(RED);
				g.fillOval(5, 8, 4, 4);
				break;
			case GINGERBREAD:
				g.setColor(GINGER);
				g.fillOval(4, 0, 5, 5);
				g.fillRect(4, 4, 5, 5);
				g.fillRect(1, 5, 11, 2);
				g.fillRect(4, 9, 2, 4);
				g.fillRect(7, 9, 2, 4);
				g.setColor(WHITE);
				g.fillRect(5, 2, 1, 1);
				g.fillRect(7, 2, 1, 1);
				g.fillRect(6, 6, 1, 1);
				g.fillRect(6, 8, 1, 1);
				g.fillRect(1, 5, 1, 2);
				g.fillRect(11, 5, 1, 2);
				break;
			case SANTA_HAT:
				g.setColor(RED);
				g.fillPolygon(new int[]{2, 7, 11, 11}, new int[]{9, 1, 3, 9}, 4);
				g.setColor(WHITE);
				g.fillRect(1, 9, 11, 3);
				g.fillOval(10, 1, 3, 3);
				break;
			default:
				g.setColor(GOLD);
				g.fillPolygon(new int[]{6, 8, 12, 9, 10, 6, 2, 3, 0, 4}, new int[]{0, 4, 5, 8, 12, 10, 12, 8, 5, 4}, 10);
				break;
		}
	}
}
