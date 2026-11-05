package com.asgardians;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Shared building blocks for the side panel's tabs, so every tab looks the same. */
final class PanelParts
{
	static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color TODO = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR;
	/** Main text. */
	static final Color TEXT = new Color(0xD8, 0xD8, 0xD8);
	/** Secondary text. RuneLite's medium grey is too dark to read at this font size on the card background. */
	static final Color QUIET = new Color(0xA0, 0xA0, 0xA0);
	static final Color HIGHLIGHT = new Color(0xE8, 0x76, 0x3F);
	static final Color TEST_SERVER = new Color(0xC9, 0xA2, 0x27);
	/** Width text wraps at inside a card. */
	static final int TEXT_WIDTH = 150;
	static final int GAP = 8;
	/** Room for a position up to four digits. */
	private static final int RANK_WIDTH = 26;

	private PanelParts()
	{
	}

	/** A titled dark box that stacks its rows. */
	static class Card extends JPanel
	{
		Card(String title)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(new EmptyBorder(8, 8, 8, 8));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			if (title != null)
			{
				JLabel heading = new JLabel(wrap(escape(title)));
				heading.setFont(FontManager.getRunescapeBoldFont());
				heading.setForeground(Color.WHITE);
				add(heading);
				add(Box.createVerticalStrut(4));
			}
		}

		@Override
		public Component add(Component component)
		{
			if (component instanceof JComponent)
			{
				((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
			}
			return super.add(component);
		}

		/** A row that stretches to the card's width. */
		void addWide(JComponent component)
		{
			component.setAlignmentX(Component.LEFT_ALIGNMENT);
			component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
			super.add(component);
		}

		void gap()
		{
			add(Box.createVerticalStrut(6));
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** Lays out cards top to bottom with a gap between. */
	static JPanel column()
	{
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		return column;
	}

	static void addCard(JPanel column, JComponent card)
	{
		if (column.getComponentCount() > 0)
		{
			column.add(Box.createVerticalStrut(GAP));
		}
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(card);
	}

	static JLabel heading(String text)
	{
		JLabel label = new JLabel(wrap(escape(text)));
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	/** A coloured dot and wrapped text. */
	static JLabel status(Color colour, String text)
	{
		JLabel label = new JLabel(wrap(dot(colour) + " " + escape(text)));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour == QUIET ? TEXT : colour);
		return label;
	}

	static JLabel check(boolean done, String doneText, String todoText)
	{
		JLabel label = new JLabel(wrap((done ? "✔ " : "✖ ") + escape(done ? doneText : todoText)));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(done ? GOOD : TODO);
		label.setBorder(new EmptyBorder(1, 0, 1, 0));
		return label;
	}

	/** Wrapped small text. */
	static JLabel small(String text, Color colour)
	{
		JLabel label = new JLabel(wrap(escape(text)));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	/** Single-line text that doesn't wrap. */
	static JLabel plain(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	static JLabel badge(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setOpaque(true);
		label.setBackground(colour);
		label.setForeground(Color.BLACK);
		label.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
		return label;
	}

	/** A dot, a name that is cut short when long, and a right-hand value. */
	static JPanel dotRow(Color colour, String text, String right, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setToolTipText(tooltip);
		JLabel dot = plain("●", colour);
		dot.setToolTipText(tooltip);
		row.add(dot, BorderLayout.WEST);
		JLabel what = plain(text, TEXT);
		what.setToolTipText(tooltip != null ? tooltip : text);
		what.setMinimumSize(new Dimension(0, what.getPreferredSize().height));
		what.setPreferredSize(new Dimension(0, what.getPreferredSize().height));
		row.add(what, BorderLayout.CENTER);
		if (right != null)
		{
			row.add(plain(right, QUIET), BorderLayout.EAST);
		}
		return row;
	}

	/** A heading above a group of cards. */
	static JLabel section(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(HIGHLIGHT);
		return label;
	}

	/** Clickable text, for actions that don't need a full button. */
	static JLabel link(String text, Runnable action)
	{
		JLabel label = plain(text, HIGHLIGHT);
		label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		label.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				label.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				label.setForeground(HIGHLIGHT);
			}
		});
		return label;
	}

	/** One line with something on the left and something on the right; either can be null. */
	static JPanel spread(JComponent left, JComponent right)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		if (left != null)
		{
			row.add(left, BorderLayout.WEST);
		}
		if (right != null)
		{
			row.add(right, BorderLayout.EAST);
		}
		return row;
	}

	/** A label on the left and its value on the right. */
	static JPanel statRow(String label, String value)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.add(plain(label, QUIET), BorderLayout.WEST);
		row.add(plain(value, TEXT), BorderLayout.EAST);
		return row;
	}

	/** A leaderboard position, a name that is cut short when long, and a right-hand value. The member's own row stands out. */
	static JPanel rankRow(Integer rank, String name, String value, boolean mine)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		JLabel place = plain(rank != null ? rank + "." : "-", QUIET);
		place.setPreferredSize(new Dimension(RANK_WIDTH, place.getPreferredSize().height));
		row.add(place, BorderLayout.WEST);
		JLabel who = plain(escape(name), mine ? HIGHLIGHT : TEXT);
		who.setToolTipText(name);
		who.setMinimumSize(new Dimension(0, who.getPreferredSize().height));
		who.setPreferredSize(new Dimension(0, who.getPreferredSize().height));
		row.add(who, BorderLayout.CENTER);
		row.add(plain(escape(value), mine ? HIGHLIGHT : TEXT), BorderLayout.EAST);
		return row;
	}

	/**
	 * A row (or grid) of solid buttons where one is picked, styled like the panel's tabs. Text that doesn't fit is cut
	 * short; hover shows it all.
	 */
	static JPanel choice(java.util.List<String> labels, int selected, int columns, java.util.function.IntConsumer pick)
	{
		int cols = Math.max(1, Math.min(columns, labels.size()));
		JPanel grid = new JPanel(new java.awt.GridLayout(0, cols, 4, 4));
		grid.setOpaque(false);
		for (int i = 0; i < labels.size(); i++)
		{
			int index = i;
			boolean on = i == selected;
			JLabel option = new JLabel(labels.get(i), javax.swing.SwingConstants.CENTER);
			option.setFont(FontManager.getRunescapeSmallFont());
			option.setOpaque(true);
			option.setBackground(on ? HIGHLIGHT : ColorScheme.DARKER_GRAY_COLOR);
			option.setForeground(on ? Color.WHITE : TEXT);
			option.setBorder(new EmptyBorder(4, 3, 4, 3));
			option.setToolTipText(labels.get(i));
			option.setMinimumSize(new Dimension(0, option.getPreferredSize().height));
			option.setPreferredSize(new Dimension(0, option.getPreferredSize().height));
			option.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			option.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent e)
				{
					if (!on)
					{
						pick.accept(index);
					}
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					if (!on)
					{
						option.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
					}
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					option.setBackground(on ? HIGHLIGHT : ColorScheme.DARKER_GRAY_COLOR);
				}
			});
			grid.add(option);
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
		return grid;
	}

	/** A card heading with an arrow that folds the card away or opens it again, plus optional text on the right. */
	static JPanel foldHeader(String title, boolean open, String right, Runnable toggle)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel heading = new JLabel("<html><div style='width:" + (right != null ? TEXT_WIDTH - 55 : TEXT_WIDTH - 14) + "px'>"
			+ escape(title) + "</div></html>", new Arrow(open), JLabel.LEFT);
		heading.setIconTextGap(6);
		heading.setVerticalTextPosition(JLabel.TOP);
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(Color.WHITE);
		row.add(heading, BorderLayout.CENTER);
		if (right != null)
		{
			row.add(plain(right, HIGHLIGHT), BorderLayout.EAST);
		}
		row.setToolTipText(open ? "Click to fold away" : "Click to open");
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				toggle.run();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				heading.setForeground(HIGHLIGHT);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				heading.setForeground(Color.WHITE);
			}
		});
		return row;
	}

	/** A small triangle, pointing down when open and right when folded. Drawn, since the game fonts have no arrows. */
	private static class Arrow implements javax.swing.Icon
	{
		private final boolean open;

		Arrow(boolean open)
		{
			this.open = open;
		}

		@Override
		public void paintIcon(Component c, java.awt.Graphics g, int x, int y)
		{
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
			g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(QUIET);
			int[] xs = open ? new int[]{x, x + 8, x + 4} : new int[]{x + 1, x + 7, x + 1};
			int[] ys = open ? new int[]{y + 2, y + 2, y + 7} : new int[]{y, y + 4, y + 8};
			g2.fillPolygon(xs, ys, 3);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 8;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}

	static JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.addActionListener(e -> action.run());
		return button;
	}

	static String wrap(String html)
	{
		return "<html><div style='width:" + TEXT_WIDTH + "px'>" + html + "</div></html>";
	}

	static String dot(Color colour)
	{
		return "<span style='color:" + hex(colour) + "'>●</span>";
	}

	static String hex(Color colour)
	{
		return String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
	}

	static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
