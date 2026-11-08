package com.asgardians;

import static com.asgardians.PanelParts.CARD;
import static com.asgardians.PanelParts.CARD_HOVER;
import static com.asgardians.PanelParts.GOOD;
import static com.asgardians.PanelParts.HIGHLIGHT;
import static com.asgardians.PanelParts.LINE;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.FontManager;

/** The panel's frame: the header with its back arrow, the Home page's tiles, and the status line at the bottom. */
final class PanelChrome
{
	private PanelChrome()
	{
	}

	/** The orange line icons on the Home tiles. */
	enum Icon
	{
		EVENTS, BINGO, GROUPS, MEMBERS, DROPS, LEADERBOARD, PROFILE, NEWS, PBS
	}

	/**
	 * The top of the panel. On Home it shows the clan's name; on a page it shows a back arrow, the page's title and a
	 * line under it. A refresh arrow sits on the right.
	 */
	static class Header extends JPanel
	{
		private final JLabel title = new JLabel();
		private final JLabel subtitle = new JLabel();
		private final Arrow back;
		private final HomeButton homeButton;
		private Runnable onBack = () -> { };

		Header(Runnable refresh, Runnable settings, Runnable home)
		{
			super(new BorderLayout(6, 0));
			setOpaque(false);
			setBorder(new javax.swing.border.CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(0, 0, 8, 0)));
			back = new Arrow(() -> onBack.run());
			add(back, BorderLayout.WEST);
			JPanel words = new JPanel();
			words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
			words.setOpaque(false);
			title.setFont(FontManager.getRunescapeBoldFont());
			subtitle.setFont(FontManager.getRunescapeSmallFont());
			subtitle.setForeground(QUIET);
			words.add(title);
			words.add(subtitle);
			add(words, BorderLayout.CENTER);
			JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
			buttons.setOpaque(false);
			homeButton = new HomeButton(home);
			buttons.add(homeButton);
			buttons.add(new Refresh(refresh));
			buttons.add(new Cog(settings));
			add(buttons, BorderLayout.EAST);
		}

		/** Home: the clan's name in orange, no back arrow. */
		void home(String sub)
		{
			back.setVisible(false);
			homeButton.setVisible(false);
			title.setText("Asgardians");
			title.setForeground(HIGHLIGHT);
			subtitle.setText(sub);
			revalidate();
			repaint();
		}

		/** A page: back arrow, white title and a line under it. */
		void page(String text, String sub, Runnable goBack)
		{
			back.setVisible(true);
			homeButton.setVisible(true);
			onBack = goBack;
			title.setText(text);
			title.setForeground(TEXT);
			subtitle.setText(sub == null || sub.isEmpty() ? " " : sub);
			revalidate();
			repaint();
		}

		String titleText()
		{
			return title.getText();
		}
	}

	/** A "<" that goes back to Home. */
	private static class Arrow extends JComponent
	{
		private boolean hover;

		Arrow(Runnable action)
		{
			setPreferredSize(new Dimension(14, 26));
			setToolTipText("Back to Home");
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addHierarchyListener(e ->
			{
				if (!isShowing() && hover)
				{
					hover = false;
					repaint();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					action.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = smooth(g);
			g2.setColor(hover ? HIGHLIGHT : TEXT);
			g2.setStroke(new BasicStroke(2f));
			int cy = getHeight() / 2;
			g2.drawPolyline(new int[]{8, 3, 8}, new int[]{cy - 5, cy, cy + 5}, 3);
			g2.dispose();
		}
	}

	/** A circular arrow that reloads the page. */
	private static class Refresh extends JComponent
	{
		private boolean hover;

		Refresh(Runnable action)
		{
			setPreferredSize(new Dimension(20, 26));
			setToolTipText("Refresh");
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addHierarchyListener(e ->
			{
				if (!isShowing() && hover)
				{
					hover = false;
					repaint();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					action.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = smooth(g);
			g2.setColor(hover ? HIGHLIGHT : QUIET);
			g2.setStroke(new BasicStroke(2f));
			int cy = getHeight() / 2;
			g2.draw(new Arc2D.Float(3, cy - 7, 14, 14, 60, 290, Arc2D.OPEN));
			g2.fillPolygon(new int[]{14, 19, 13}, new int[]{cy - 9, cy - 5, cy - 2}, 3);
			g2.dispose();
		}
	}

	/** A house that goes straight back to Home. */
	private static class HomeButton extends JComponent
	{
		private boolean hover;

		HomeButton(Runnable action)
		{
			setPreferredSize(new Dimension(20, 26));
			setToolTipText("Home");
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addHierarchyListener(e ->
			{
				if (!isShowing() && hover)
				{
					hover = false;
					repaint();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					action.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = smooth(g);
			g2.setColor(hover ? HIGHLIGHT : QUIET);
			int cx = getWidth() / 2, cy = getHeight() / 2;
			g2.fillPolygon(new int[]{cx - 8, cx, cx + 8}, new int[]{cy - 1, cy - 8, cy - 1}, 3);
			g2.fillRect(cx - 6, cy - 1, 12, 8);
			g2.setColor(PanelParts.BACKGROUND);
			g2.fillRect(cx - 2, cy + 2, 4, 5);
			g2.dispose();
		}
	}

	/** A cog that opens the settings page. */
	private static class Cog extends JComponent
	{
		private boolean hover;

		Cog(Runnable action)
		{
			setPreferredSize(new Dimension(20, 26));
			setToolTipText("Settings");
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addHierarchyListener(e ->
			{
				if (!isShowing() && hover)
				{
					hover = false;
					repaint();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					action.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = smooth(g);
			g2.setColor(hover ? HIGHLIGHT : QUIET);
			int cx = getWidth() / 2, cy = getHeight() / 2;
			g2.fillOval(cx - 6, cy - 6, 12, 12);
			for (int i = 0; i < 8; i++)
			{
				double a = i * Math.PI / 4;
				int x = (int) Math.round(cx + Math.cos(a) * 7), y = (int) Math.round(cy + Math.sin(a) * 7);
				g2.fillRect(x - 2, y - 2, 4, 4);
			}
			g2.setColor(PanelParts.BACKGROUND);
			g2.fillOval(cx - 3, cy - 3, 6, 6);
			g2.dispose();
		}
	}

	/** A large Home button: an icon, the page's name and a live line under it. */
	static class Tile extends JComponent
	{
		private final Icon icon;
		private final String name;
		private String line = " ";
		private Color lineColour = QUIET;
		/** An OSRS picture shown in place of the line icon, once loaded. */
		private java.awt.image.BufferedImage picture;
		private boolean hover;

		Tile(Icon icon, String name, Runnable open)
		{
			this.icon = icon;
			this.name = name;
			setPreferredSize(new Dimension(100, 70));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Open " + name);
			addHierarchyListener(e ->
			{
				if (!isShowing() && hover)
				{
					hover = false;
					repaint();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					open.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});
		}

		/** Shows an OSRS picture (an item or one of the game's own icons) in place of the line icon. */
		void setPicture(java.awt.image.BufferedImage image)
		{
			picture = image;
			repaint();
		}

		/** The line under the name, e.g. "3 open" or "2 new". */
		void setLine(String text, Color colour)
		{
			line = text == null || text.isEmpty() ? " " : text;
			lineColour = colour;
			repaint();
		}

		String line()
		{
			return line;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			PanelParts.roundBox(g, getWidth(), getHeight(), hover ? CARD_HOVER : CARD, hover ? HIGHLIGHT : null);
			Graphics2D g2 = smooth(g);
			int cx = getWidth() / 2;
			if (picture != null)
			{
				// Scaled in whole steps where it can be, so the game's pixel art stays crisp.
				double s = Math.min(28.0 / picture.getWidth(), 28.0 / picture.getHeight());
				s = s >= 1 ? Math.floor(s) : s;
				int w = (int) Math.round(picture.getWidth() * s), h = (int) Math.round(picture.getHeight() * s);
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, s >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
					: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.drawImage(picture, cx - w / 2, 20 - h / 2, w, h, null);
			}
			else
			{
				paintIcon(g2, icon, cx, 20);
			}
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			centred(g2, FontManager.getRunescapeBoldFont(), TEXT, name, cx, 47, getWidth() - 8);
			centred(g2, FontManager.getRunescapeSmallFont(), lineColour, line, cx, 61, getWidth() - 8);
			g2.dispose();
		}
	}

	/**
	 * The card at the bottom of every page, like the top of the Profile page: the member's picture, name and rank, then
	 * whether the plugin is connected (a coloured dot) and their world, with the plugin's version. Clicking it opens
	 * their profile.
	 */
	static class Footer extends JPanel
	{
		private final JLabel picture = new JLabel("", JLabel.CENTER)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				PanelParts.roundBox(g, getWidth(), getHeight(), PanelParts.BACKGROUND, null);
				if (getIcon() == null)
				{
					// No picture yet: a head and shoulders in orange.
					Graphics2D g2 = smooth(g);
					g2.setColor(HIGHLIGHT);
					g2.setStroke(new BasicStroke(2f));
					int cx = getWidth() / 2, cy = getHeight() / 2;
					g2.drawOval(cx - 5, cy - 10, 10, 10);
					g2.drawArc(cx - 10, cy + 2, 20, 18, 0, 180);
					g2.dispose();
				}
				super.paintComponent(g);
			}
		};
		private final JLabel name = new JLabel();
		private final JPanel nameRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		private final JLabel status = new JLabel();
		private Color dot = QUIET;
		private Color edge = LINE;
		private Integer pictureId;

		Footer(Runnable open)
		{
			super(new BorderLayout(8, 0));
			setOpaque(false);
			setBorder(new EmptyBorder(7, 7, 7, 8));
			Dimension box = new Dimension(34, 34);
			picture.setPreferredSize(box);
			picture.setMinimumSize(box);
			add(picture, BorderLayout.WEST);
			JPanel words = new JPanel();
			words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
			words.setOpaque(false);
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(TEXT);
			nameRow.setOpaque(false);
			nameRow.add(name);
			nameRow.setAlignmentX(LEFT_ALIGNMENT);
			words.add(nameRow);
			JPanel statusRow = new JPanel(new BorderLayout(4, 0));
			statusRow.setOpaque(false);
			JLabel version = new JLabel("v" + ApiClient.PLUGIN_VERSION);
			version.setFont(FontManager.getRunescapeSmallFont());
			version.setForeground(QUIET);
			status.setFont(FontManager.getRunescapeSmallFont());
			status.setBorder(new EmptyBorder(0, 11, 0, 0));
			statusRow.add(status, BorderLayout.CENTER);
			statusRow.add(version, BorderLayout.EAST);
			statusRow.setAlignmentX(LEFT_ALIGNMENT);
			words.add(statusRow);
			add(words, BorderLayout.CENTER);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter click = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					open.run();
				}
			};
			addMouseListener(click);
		}

		/** Whether the plugin is connected: the dot's colour, the words, and more detail on hover. */
		void set(Color colour, String text, String tooltip)
		{
			dot = colour;
			edge = colour == GOOD ? LINE : colour;
			status.setText(text);
			status.setForeground(colour == GOOD ? GOOD : colour == QUIET ? QUIET : colour);
			setToolTipText(tooltip);
			status.setToolTipText(tooltip);
			repaint();
		}

		/** Who's logged in: name, clan rank (or null), and an item whose picture stands for them (or null). */
		void who(String rsn, String rank, Integer itemId)
		{
			name.setText(rsn != null ? rsn : "Not logged in");
			name.setForeground(rsn != null ? TEXT : QUIET);
			while (nameRow.getComponentCount() > 1)
			{
				nameRow.remove(1);
			}
			if (rank != null)
			{
				nameRow.add(javax.swing.Box.createHorizontalStrut(5));
				nameRow.add(PanelParts.tag(rank, PanelParts.GOLD));
			}
			if (!java.util.Objects.equals(itemId, pictureId))
			{
				pictureId = itemId;
				picture.setIcon(null);
				PanelParts.itemImage(itemId, img -> picture.setIcon(new javax.swing.ImageIcon(PanelParts.fit(img, 30, 30))));
			}
			revalidate();
			repaint();
		}

		String text()
		{
			return status.getText();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			PanelParts.roundBox(g, getWidth(), getHeight(), PanelParts.CARD, edge);
			super.paintComponent(g);
			Graphics2D g2 = smooth(g);
			g2.setColor(dot);
			java.awt.Point at = javax.swing.SwingUtilities.convertPoint(status, 0, 0, this);
			g2.fillOval(at.x + 1, at.y + Math.max(0, (status.getHeight() - 7) / 2), 7, 7);
			g2.dispose();
		}
	}

	static Graphics2D smooth(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g2;
	}

	private static void centred(Graphics2D g, java.awt.Font font, Color colour, String text, int cx, int y, int maxWidth)
	{
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		String shown = text;
		if (fm.stringWidth(shown) > maxWidth)
		{
			while (shown.length() > 1 && fm.stringWidth(shown + "…") > maxWidth)
			{
				shown = shown.substring(0, shown.length() - 1);
			}
			shown += "…";
		}
		int x = cx - fm.stringWidth(shown) / 2;
		g.setColor(new Color(0, 0, 0, 160));
		g.drawString(shown, x + 1, y + 1);
		g.setColor(colour);
		g.drawString(shown, x, y);
	}

	/** Draws a tile's icon centred on (cx, cy), about 20 pixels across, in orange. */
	static void paintIcon(Graphics2D g, Icon icon, int cx, int cy)
	{
		g.setColor(HIGHLIGHT);
		g.setStroke(new BasicStroke(2f));
		switch (icon)
		{
			case EVENTS:
				g.drawRoundRect(cx - 9, cy - 8, 18, 16, 3, 3);
				g.fillRect(cx - 9, cy - 8, 18, 5);
				g.fillRect(cx - 5, cy + 1, 3, 3);
				g.fillRect(cx + 1, cy + 1, 3, 3);
				break;
			case BINGO:
				for (int i = 0; i < 9; i++)
				{
					g.fillRect(cx - 9 + (i % 3) * 7, cy - 9 + (i / 3) * 7, 5, 5);
				}
				break;
			case GROUPS:
				g.drawOval(cx - 9, cy - 9, 7, 7);
				g.drawOval(cx + 2, cy - 9, 7, 7);
				g.drawArc(cx - 11, cy, 11, 10, 0, 180);
				g.drawArc(cx, cy, 11, 10, 0, 180);
				break;
			case MEMBERS:
				for (int i = 0; i < 3; i++)
				{
					g.fillRect(cx - 9, cy - 8 + i * 6, 3, 3);
					g.fillRect(cx - 4, cy - 8 + i * 6, 13, 3);
				}
				break;
			case DROPS:
				g.drawRoundRect(cx - 9, cy - 3, 18, 11, 3, 3);
				g.drawLine(cx, cy - 10, cx, cy + 2);
				g.drawPolyline(new int[]{cx - 4, cx, cx + 4}, new int[]{cy - 2, cy + 2, cy - 2}, 3);
				break;
			case LEADERBOARD:
				g.fillRect(cx - 9, cy - 1, 5, 9);
				g.fillRect(cx - 2, cy - 7, 5, 15);
				g.fillRect(cx + 5, cy + 2, 5, 6);
				break;
			case PROFILE:
				g.drawOval(cx - 4, cy - 10, 8, 8);
				g.drawArc(cx - 9, cy, 18, 16, 0, 180);
				break;
			case PBS:
				g.drawOval(cx - 8, cy - 7, 16, 16);
				g.drawLine(cx, cy + 1, cx, cy - 4);
				g.drawLine(cx, cy + 1, cx + 4, cy + 1);
				g.fillRect(cx - 2, cy - 11, 5, 3);
				break;
			default:
				g.drawRoundRect(cx - 9, cy - 8, 18, 16, 2, 2);
				g.drawLine(cx - 5, cy - 3, cx + 5, cy - 3);
				g.drawLine(cx - 5, cy + 1, cx + 5, cy + 1);
				g.drawLine(cx - 5, cy + 5, cx + 1, cy + 5);
				break;
		}
	}
}
