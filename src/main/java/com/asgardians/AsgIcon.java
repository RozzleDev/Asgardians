package com.asgardians;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws the asgardiansclan.com favicon (asg-favicon.svg): "ASG" in orange serif small caps on a dark rounded square.
 * Java can't read SVG, so it is redrawn at 128px and scaled down smoothly.
 */
final class AsgIcon
{
	private static final Color BACKGROUND = new Color(0x24, 0x1C, 0x18);
	private static final Color TEXT = new Color(0xE8, 0x76, 0x3F);
	private static final int MASTER = 128;

	private AsgIcon()
	{
	}

	static BufferedImage create(int size)
	{
		if (size >= 48)
		{
			return scaled(draw(MASTER, 0.84f), size);
		}
		// Downscaling blurs the letters at sidebar size, so draw them straight onto the small square, filling more of it.
		return draw(size, 0.96f);
	}

	private static BufferedImage draw(int size, float textWidth)
	{
		BufferedImage master = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = master.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setColor(BACKGROUND);
		// rx 97.28 on a 512 square
		int arc = Math.round(size * 97.28f / 512f * 2);
		g.fillRoundRect(0, 0, size, size, arc, arc);

		// Small caps: all three letters as capitals of one height, sized to fill the width like the SVG.
		Font font = serifBold(size);
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		font = font.deriveFont(font.getSize2D() * size * textWidth / fm.stringWidth("ASG"));
		g.setFont(font);
		fm = g.getFontMetrics();
		float x = (size - fm.stringWidth("ASG")) / 2f;
		// Centre the capitals themselves (cap height ~ 0.7 of the font size), not the line box.
		float y = (size + font.getSize2D() * 0.68f) / 2f;
		g.setColor(TEXT);
		g.drawString("ASG", x, y);
		g.dispose();
		return master;
	}

	private static BufferedImage scaled(BufferedImage master, int size)
	{
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D o = out.createGraphics();
		o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		o.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		o.drawImage(master.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
		o.dispose();
		return out;
	}

	private static Font serifBold(int size)
	{
		for (String name : new String[]{"Palatino Linotype", "Palatino", "Georgia", "Times New Roman"})
		{
			Font font = new Font(name, Font.BOLD, size);
			if (name.equals(font.getFamily()))
			{
				return font;
			}
		}
		return new Font(Font.SERIF, Font.BOLD, size);
	}
}
