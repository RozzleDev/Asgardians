package com.asgardians;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * Santa Claws pictures and health bars: OSRS-style HP bars (green over red, like the game and the website), the boss's
 * artwork with its live HP bar painted over the one drawn into the picture, and which picture to show (the next one each
 * time a minion falls, then the beaten one).
 */
final class SantaPictures
{
	/** The game's (and the website's) HP bar colours. */
	static final Color HP_GREEN = new Color(0x2A, 0xDB, 0x48);
	static final Color HP_RED = new Color(0xD8, 0x26, 0x1F);
	static final Color HP_HIDDEN = new Color(0x5A, 0x53, 0x50);

	/** Where the website keeps JPEG copies of the artwork for the plugin (Java can't read the site's WebP). */
	private static final String SITE_FOLDER = "/img/santa-claws/";
	private static final String PLUGIN_FOLDER = "/img/santa-claws/plugin/";

	/** Loads pictures for the page. Callbacks run on the Swing thread, straight away when already loaded. */
	interface Images
	{
		/** A picture from the clan website, by path, e.g. /img/santa-claws/plugin/frosty.jpg. */
		void picture(String path, Consumer<BufferedImage> done);

		/**
		 * A drop's picture when the game can't give one (only previews outside RuneLite; in game the item's own icon
		 * is drawn instead).
		 */
		void item(SantaData.Drop drop, Consumer<BufferedImage> done);
	}

	static final Images NONE = new Images()
	{
		@Override
		public void picture(String path, Consumer<BufferedImage> done)
		{
		}

		@Override
		public void item(SantaData.Drop drop, Consumer<BufferedImage> done)
		{
		}
	};

	private SantaPictures()
	{
	}

	/** /img/santa-claws/frosty.webp → /img/santa-claws/plugin/frosty.jpg; null for anything else. */
	static String pluginPicture(String sitePath)
	{
		if (sitePath == null || !sitePath.startsWith(SITE_FOLDER) || sitePath.startsWith(PLUGIN_FOLDER))
		{
			return sitePath != null && sitePath.startsWith(PLUGIN_FOLDER) ? sitePath : null;
		}
		String file = sitePath.substring(SITE_FOLDER.length());
		int dot = file.lastIndexOf('.');
		return PLUGIN_FOLDER + (dot > 0 ? file.substring(0, dot) : file) + ".jpg";
	}

	/**
	 * The picture to show now, like the website: beaten once the boss is down, else one stage per minion down (the last
	 * stays once they run out), else the main picture. While the clan is blinded HP is hidden, so it doesn't move on.
	 */
	static String current(SantaData.Encounter e)
	{
		SantaData.Art art = e.getArt();
		SantaData.Unit boss = e.boss();
		if (art != null && art.getDefeated() != null && (e.isDefeated() || (boss != null && boss.getHp() != null && boss.getHp() <= 0)))
		{
			return art.getDefeated();
		}
		List<String> stages = art != null ? SantaData.orEmpty(art.getStages()) : java.util.Collections.emptyList();
		int down = 0;
		for (SantaData.Unit u : SantaData.orEmpty(e.getUnits()))
		{
			if (!"boss".equals(u.getKey()) && !u.isDecoy() && u.getHp() != null && u.getHp() <= 0)
			{
				down++;
			}
		}
		if (down > 0 && !stages.isEmpty())
		{
			return stages.get(Math.min(down, stages.size()) - 1);
		}
		return e.getImageUrl();
	}

	/** "1312/1199" → 1.094; square when missing. */
	static double aspect(String text)
	{
		if (text != null)
		{
			String[] parts = text.split("/");
			try
			{
				if (parts.length == 2)
				{
					double w = Double.parseDouble(parts[0].trim());
					double h = Double.parseDouble(parts[1].trim());
					if (w > 0 && h > 0)
					{
						return w / h;
					}
				}
			}
			catch (NumberFormatException ignored)
			{
				// Fall through to square.
			}
		}
		return 1;
	}

	/** An OSRS HP bar: green for what's left over red, with a thin dark edge and the numbers on top. */
	static JComponent hpBar(Integer hp, int max, int height)
	{
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				paintBar((Graphics2D) g, 0, 0, getWidth(), getHeight(), hp, max, height >= 12);
			}
		};
		bar.setPreferredSize(new Dimension(10, height));
		bar.setMinimumSize(new Dimension(10, height));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		bar.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return bar;
	}

	/** A slim progress bar for the one-line cards (bonus XP, an attack's defence), filled in {@code colour}. */
	static JComponent thinBar(double have, double need, Color colour)
	{
		return thinBar(have, need, colour, null);
	}

	/** The same, taller, with {@code label} (e.g. "82k Firemaking XP left") written across it. */
	static JComponent thinBar(double have, double need, Color colour, String label)
	{
		int height = label != null ? 13 : 6;
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				double share = need > 0 ? Math.max(0, Math.min(1, have / need)) : 0;
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(new Color(0x14, 0x0E, 0x0B));
				g2.fillRect(0, 0, getWidth(), getHeight());
				// A shade darker under writing, so the white text stays readable.
				g2.setColor(label != null ? colour.darker() : colour);
				g2.fillRect(0, 0, (int) Math.round(getWidth() * share), getHeight());
				if (label != null)
				{
					g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					g2.setFont(FontManager.getRunescapeSmallFont());
					FontMetrics fm = g2.getFontMetrics();
					int tx = (getWidth() - fm.stringWidth(label)) / 2;
					int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
					g2.setColor(Color.BLACK);
					g2.drawString(label, tx + 1, ty + 1);
					g2.setColor(Color.WHITE);
					g2.drawString(label, tx, ty);
				}
				g2.dispose();
			}
		};
		bar.setPreferredSize(new Dimension(10, height));
		bar.setMinimumSize(new Dimension(10, height));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		bar.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return bar;
	}

	/** Paints an HP bar into a box. {@code hp} null means hidden (blinded): grey, "? / ?". */
	static void paintBar(Graphics2D g, int x, int y, int w, int h, Integer hp, int max, boolean numbers)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (hp == null)
		{
			g2.setColor(HP_HIDDEN);
			g2.fillRect(x, y, w, h);
		}
		else
		{
			double share = max > 0 ? Math.max(0, Math.min(1, hp / (double) max)) : 0;
			int green = (int) Math.round(w * share);
			g2.setColor(HP_RED);
			g2.fillRect(x, y, w, h);
			g2.setColor(HP_GREEN);
			g2.fillRect(x, y, green, h);
		}
		if (numbers)
		{
			label(g2, hp == null ? "? / ?" : hp + " / " + max, x, y, w, h);
		}
		g2.dispose();
	}

	/** White text with a black shadow, centred on a bar (it may spill past a short bar's ends, like the website's). */
	static void label(Graphics2D g, String text, int x, int y, int w, int h)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g2.getFontMetrics();
		int tx = x + (w - fm.stringWidth(text)) / 2;
		int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
		g2.setColor(Color.BLACK);
		g2.drawString(text, tx + 1, ty + 1);
		g2.setColor(Color.WHITE);
		g2.drawString(text, tx, ty);
		g2.dispose();
	}

	/**
	 * A picture from the website, as wide as the card, keeping its shape. With {@code art} and a boss it paints the live
	 * HP bar over the bar drawn into the artwork, like the website does.
	 */
	static JComponent picture(String path, String aspectText, Images images, SantaData.Art art, List<SantaData.Unit> units, boolean blind)
	{
		return picture(path, aspectText, images, art, units, blind, null);
	}

	/** The same; while {@code blind} the boss is covered by the shade, with {@code shadeNote} under it. */
	static JComponent picture(String path, String aspectText, Images images, SantaData.Art art, List<SantaData.Unit> units, boolean blind,
		String shadeNote)
	{
		Picture pic = new Picture(aspectText != null ? aspect(aspectText) : 0, art, units, blind);
		pic.shadeNote = shadeNote;
		if (path != null)
		{
			images.picture(path, pic::show);
		}
		return pic;
	}

	/** A small picture for the line-up, {@code size} pixels tall. */
	static JComponent thumb(String path, String aspectText, Images images, int size)
	{
		double aspect = aspect(aspectText);
		Picture pic = new Picture(aspect, null, null, false);
		Dimension d = new Dimension((int) Math.round(size * aspect), size);
		pic.setPreferredSize(d);
		pic.setMinimumSize(d);
		pic.setMaximumSize(d);
		if (path != null)
		{
			images.picture(path, pic::show);
		}
		return pic;
	}

	private static final class Picture extends JComponent
	{
		/** Width over height; 0 until the picture arrives when the website didn't say. */
		private double aspect;
		private final SantaData.Art art;
		private final List<SantaData.Unit> units;
		private final boolean blind;
		/** Under "THROWING SHADE" while blind, e.g. "HP hidden · clears in 3h 12m". */
		private String shadeNote;
		private BufferedImage image;

		Picture(double aspect, SantaData.Art art, List<SantaData.Unit> units, boolean blind)
		{
			this.aspect = aspect;
			this.art = art;
			this.units = units != null ? units : java.util.Collections.emptyList();
			this.blind = blind;
			setAlignmentX(LEFT_ALIGNMENT);
		}

		void show(BufferedImage img)
		{
			image = img;
			if (aspect <= 0 && img.getHeight() > 0)
			{
				aspect = img.getWidth() / (double) img.getHeight();
				revalidate();
			}
			repaint();
		}

		@Override
		public Dimension getPreferredSize()
		{
			if (isPreferredSizeSet())
			{
				return super.getPreferredSize();
			}
			int w = getWidth() > 0 ? getWidth() : 206;
			return new Dimension(w, (int) Math.round(w / (aspect > 0 ? aspect : 1.6)));
		}

		@Override
		public Dimension getMaximumSize()
		{
			return isMaximumSizeSet() ? super.getMaximumSize() : new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			int w = getWidth();
			int h = getHeight();
			if (image == null)
			{
				g2.setColor(PanelParts.CARD_HOVER);
				g2.fillRoundRect(0, 0, w, h, 8, 8);
				g2.dispose();
				return;
			}
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.drawImage(image, 0, 0, w, h, null);
			// Seeing Double: while a decoy stands, a faded copy of the boss sits beside him, like the website.
			SantaData.Unit decoy = null;
			for (SantaData.Unit u : units)
			{
				if (u.isDecoy() && (u.getHp() == null || u.getHp() > 0))
				{
					decoy = u;
				}
			}
			if (decoy != null)
			{
				Graphics2D ghost = (Graphics2D) g2.create();
				ghost.clipRect(0, 0, w, h);
				ghost.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
				ghost.drawImage(image, (int) Math.round(w * 0.09), 0, w, h, null);
				ghost.dispose();
				// The fake's own bar, where the website puts it (top right).
				int bw = Math.max(36, (int) Math.round(w * 0.14));
				int bx = Math.max(0, Math.min(w - bw, (int) Math.round(w * 0.72) - bw / 2));
				paintBar(g2, bx, (int) Math.round(h * 0.16), bw, 6, blind ? null : decoy.getHp(), decoy.getMaxHp(), false);
			}
			for (SantaData.Unit u : units)
			{
				if ("boss".equals(u.getKey()))
				{
					if (art != null && art.getW() > 0 && art.getH() > 0)
					{
						// The live bar over the one in the artwork, a pixel larger all round so none of the drawn one peeks out.
						int bx = (int) Math.floor(w * art.getX() / 100) - 1;
						int by = (int) Math.floor(h * art.getY() / 100) - 1;
						int bw = (int) Math.ceil(w * art.getW() / 100) + 2;
						int bh = (int) Math.ceil(h * art.getH() / 100) + 2;
						paintBar(g2, bx, by, bw, bh, blind ? null : u.getHp(), u.getMaxHp(), false);
					}
				}
				else if (!u.isDecoy())
				{
					// A minion's bar over the minion, like the website: just the colours (the line above has the
					// numbers), faded once beaten.
					boolean dead = u.getHp() != null && u.getHp() <= 0;
					int bw = Math.max(30, (int) Math.round(w * 0.12));
					int bh = 6;
					int cx = (int) Math.round(w * (u.getX() != null ? u.getX() : 50) / 100);
					int by = (int) Math.round(h * (u.getY() != null ? u.getY() : 30) / 100);
					int bx = Math.max(0, Math.min(w - bw, cx - bw / 2));
					Graphics2D g3 = (Graphics2D) g2.create();
					if (dead)
					{
						g3.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f));
					}
					paintBar(g3, bx, by, bw, bh, blind ? null : u.getHp(), u.getMaxHp(), false);
					g3.dispose();
				}
			}
			if (blind)
			{
				paintShade(g2, w, h);
			}
			g2.dispose();
		}

		/** Animation step for the shade's drifting smoke and bobbing sunglasses, while it's on screen. */
		private int phase;
		private javax.swing.Timer shadeTimer;

		@Override
		public void addNotify()
		{
			super.addNotify();
			if (blind && shadeTimer == null)
			{
				shadeTimer = new javax.swing.Timer(80, e ->
				{
					phase++;
					repaint();
				});
				shadeTimer.start();
			}
		}

		@Override
		public void removeNotify()
		{
			if (shadeTimer != null)
			{
				shadeTimer.stop();
				shadeTimer = null;
			}
			super.removeNotify();
		}

		/**
		 * Throws Shade, like the website: dark smoke drifting across the boss, a pair of sunglasses bobbing in the
		 * middle, "THROWING SHADE" and when it clears.
		 */
		private void paintShade(Graphics2D g, int w, int h)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(new Color(8, 5, 10, 236));
			g2.fillRect(0, 0, w, h);
			// Two smoke clouds drifting opposite ways.
			double t = phase / 60.0;
			for (int i = 0; i < 2; i++)
			{
				float cx = (float) (w * (0.5 + 0.35 * Math.sin(t + i * Math.PI)));
				float cy = h * (i == 0 ? 0.38f : 0.62f);
				float r = Math.max(w, h) * 0.45f;
				g2.setPaint(new java.awt.RadialGradientPaint(cx, cy, r, new float[]{0f, 1f},
					new Color[]{new Color(30, 12, 38, 150), new Color(30, 12, 38, 0)}));
				g2.fillRect(0, 0, w, h);
			}
			// Sunglasses: two dark lenses and a bridge, bobbing gently.
			int lens = Math.max(16, w / 7);
			int gx = w / 2;
			int gy = (int) (h * 0.4 + Math.sin(phase / 6.0) * 3);
			java.awt.geom.AffineTransform tilt = g2.getTransform();
			g2.rotate(Math.sin(phase / 6.0) * 0.06, gx, gy);
			g2.setColor(new Color(15, 15, 18));
			g2.fillRoundRect(gx - lens - lens / 6, gy - lens / 2, lens, (int) (lens * 0.8), lens / 2, lens / 2);
			g2.fillRoundRect(gx + lens / 6, gy - lens / 2, lens, (int) (lens * 0.8), lens / 2, lens / 2);
			g2.setStroke(new java.awt.BasicStroke(Math.max(2, lens / 7f)));
			g2.drawLine(gx - lens / 6, gy - lens / 4, gx + lens / 6, gy - lens / 4);
			// A gold rim, so they stand out against the dark.
			g2.setColor(new Color(0xE9, 0xC2, 0x5A));
			g2.setStroke(new java.awt.BasicStroke(1.5f));
			g2.drawRoundRect(gx - lens - lens / 6, gy - lens / 2, lens, (int) (lens * 0.8), lens / 2, lens / 2);
			g2.drawRoundRect(gx + lens / 6, gy - lens / 2, lens, (int) (lens * 0.8), lens / 2, lens / 2);
			g2.drawLine(gx - lens / 6, gy - lens / 4, gx + lens / 6, gy - lens / 4);
			g2.setColor(new Color(255, 255, 255, 120));
			g2.fillOval(gx - lens - lens / 6 + lens / 5, gy - lens / 3, lens / 4, lens / 6);
			g2.fillOval(gx + lens / 6 + lens / 5, gy - lens / 3, lens / 4, lens / 6);
			g2.setTransform(tilt);
			// The words.
			String title = "THROWING SHADE";
			g2.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics fm = g2.getFontMetrics();
			int ty = gy + lens / 2 + fm.getAscent() + 4;
			g2.setColor(Color.BLACK);
			g2.drawString(title, (w - fm.stringWidth(title)) / 2 + 1, ty + 1);
			g2.setColor(new Color(0xE9, 0xC2, 0x5A));
			g2.drawString(title, (w - fm.stringWidth(title)) / 2, ty);
			if (shadeNote != null)
			{
				label(g2, shadeNote, 0, ty + 4, w, fm.getHeight());
			}
			g2.dispose();
		}
	}
}
