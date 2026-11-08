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
import net.runelite.client.ui.FontManager;

/**
 * Shared building blocks for the side panel's pages, so every page looks the same. The colours follow
 * asgardiansclan.com: warm dark browns with the site's orange.
 */
final class PanelParts
{
	/** The panel's background. */
	static final Color BACKGROUND = new Color(0x1E, 0x17, 0x14);
	/** Cards. */
	static final Color CARD = new Color(0x2A, 0x20, 0x1B);
	/** Cards under the mouse. */
	static final Color CARD_HOVER = new Color(0x36, 0x29, 0x22);
	/** Cards that stand out: big drops, finished tiles, the member's own row. */
	static final Color CARD_WARM = new Color(0x3A, 0x26, 0x18);
	/** Borders and dividers. */
	static final Color LINE = new Color(0x40, 0x31, 0x28);
	static final Color GOOD = new Color(0x4C, 0xC3, 0x5E);
	static final Color TODO = new Color(0xE8, 0xB2, 0x3A);
	static final Color BAD = new Color(0xE0, 0x4E, 0x40);
	/** Main text. */
	static final Color TEXT = new Color(0xF3, 0xEC, 0xE6);
	/** Secondary text. */
	static final Color QUIET = new Color(0xA8, 0x98, 0x8C);
	/** The website's orange. */
	static final Color HIGHLIGHT = new Color(0xE8, 0x70, 0x1E);
	/** Text on an orange button. */
	static final Color ON_HIGHLIGHT = new Color(0x1E, 0x10, 0x08);
	static final Color GOLD = new Color(0xE8, 0xB2, 0x3A);
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
		/** Drawn around the card when set. */
		private Color outline;
		/** A coloured edge down the left side, when set. */
		private Color stripe;

		Card(String title)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(CARD);
			setOpaque(false);
			setBorder(new EmptyBorder(8, 8, 8, 8));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			if (title != null)
			{
				JLabel heading = new JLabel(wrap(escape(title)));
				heading.setFont(FontManager.getRunescapeBoldFont());
				heading.setForeground(TEXT);
				add(heading);
				add(Box.createVerticalStrut(4));
			}
		}

		/** A coloured edge down the card's left side, e.g. its event type. */
		Card stripe(Color colour)
		{
			stripe = colour;
			java.awt.Insets in = getInsets();
			setBorder(new EmptyBorder(in.top, in.left + 4, in.bottom, in.right));
			return this;
		}

		/** A card that stands out, with a coloured edge. */
		Card outlined(Color colour, Color fill)
		{
			outline = colour;
			setBackground(fill);
			return this;
		}

		/** Clicking anywhere on the card runs {@code action}; the card lights up under the mouse. */
		Card onClick(Runnable action, String tooltip)
		{
			Color normal = getBackground();
			Color normalOutline = outline;
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			if (tooltip != null)
			{
				setToolTipText(tooltip);
			}
			java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseReleased(java.awt.event.MouseEvent e)
				{
					if (e.getComponent().contains(e.getPoint()))
					{
						action.run();
					}
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					setBackground(normal == CARD ? CARD_HOVER : normal);
					outline = HIGHLIGHT;
					repaint();
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					java.awt.Point p = javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), Card.this);
					if (!Card.this.contains(p))
					{
						setBackground(normal);
						outline = normalOutline;
						repaint();
					}
				}
			};
			listenAll(this, mouse);
			// A click usually opens another page, so the mouse never "leaves": forget the hover when hidden.
			addHierarchyListener(e ->
			{
				if (!isShowing())
				{
					setBackground(normal);
					outline = normalOutline;
				}
			});
			return this;
		}

		/** Labels with tooltips take the mouse, so the card's listener goes on everything inside it too. */
		private static void listenAll(Component c, java.awt.event.MouseAdapter mouse)
		{
			c.addMouseListener(mouse);
			if (c instanceof JComponent && c != null)
			{
				((JComponent) c).setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			}
			if (c instanceof java.awt.Container)
			{
				for (Component child : ((java.awt.Container) c).getComponents())
				{
					listenAll(child, mouse);
				}
			}
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			roundBox(g, getWidth(), getHeight(), getBackground(), outline);
			if (stripe != null)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.setColor(stripe);
				g2.fillRect(0, 0, 4, getHeight());
				g2.dispose();
			}
			super.paintComponent(g);
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
		label.setForeground(TEXT);
		return label;
	}

	/** A filled box with rounded corners, and an edge when {@code outline} is set. */
	static void roundBox(java.awt.Graphics g, int w, int h, Color fill, Color outline)
	{
		java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
		g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(fill);
		g2.fillRoundRect(0, 0, w, h, 8, 8);
		if (outline != null)
		{
			g2.setColor(outline);
			g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
		}
		g2.dispose();
	}

	/** A small outlined tag, like "CoX" or "Pet". */
	static JLabel tag(String text, Color colour)
	{
		JLabel label = new JLabel(text)
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
				g2.setColor(colour);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		label.setBorder(new EmptyBorder(0, 3, 0, 3));
		return label;
	}

	/**
	 * A drop-down: shows the picked option with an arrow, and lists the options when clicked. Drawn by the panel, so
	 * it matches the theme rather than the look and feel.
	 */
	static JComponent dropdown(java.util.List<String> labels, int selected, java.util.function.IntConsumer pick)
	{
		String shown = selected >= 0 && selected < labels.size() ? labels.get(selected) : "";
		JLabel box = new JLabel(shown)
		{
			private boolean hover;

			{
				addMouseListener(new java.awt.event.MouseAdapter()
				{
					@Override
					public void mouseEntered(java.awt.event.MouseEvent e)
					{
						hover = true;
						repaint();
					}

					@Override
					public void mouseExited(java.awt.event.MouseEvent e)
					{
						hover = false;
						repaint();
					}
				});
			}

			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				roundBox(g, getWidth(), getHeight(), hover ? CARD_HOVER : CARD, hover ? HIGHLIGHT : LINE);
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(QUIET);
				g2.setStroke(new java.awt.BasicStroke(1.5f));
				int x = getWidth() - 14, y = getHeight() / 2 - 2;
				g2.drawPolyline(new int[]{x, x + 4, x + 8}, new int[]{y, y + 4, y}, 3);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		box.setFont(FontManager.getRunescapeFont());
		box.setForeground(TEXT);
		box.setBorder(new EmptyBorder(4, 8, 4, 20));
		box.setToolTipText(shown);
		box.setMinimumSize(new Dimension(0, box.getPreferredSize().height));
		box.setPreferredSize(new Dimension(0, box.getPreferredSize().height));
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
		box.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		box.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
				menu.setBorder(BorderFactory.createLineBorder(HIGHLIGHT));
				menu.setBackground(CARD_HOVER);
				if (labels.size() > LONG_LIST)
				{
					// A long list scrolls, so it never runs off the screen.
					menu.add(scrollingList(labels, selected, index ->
					{
						menu.setVisible(false);
						if (index != selected)
						{
							pick.accept(index);
						}
					}, box.getWidth()));
					menu.show(box, 0, box.getHeight() + 1);
					return;
				}
				for (int i = 0; i < labels.size(); i++)
				{
					int index = i;
					javax.swing.JMenuItem item = new javax.swing.JMenuItem(labels.get(i));
					item.setFont(FontManager.getRunescapeFont());
					item.setForeground(i == selected ? HIGHLIGHT : TEXT);
					item.setBackground(CARD_HOVER);
					item.setOpaque(true);
					item.addActionListener(a ->
					{
						if (index != selected)
						{
							pick.accept(index);
						}
					});
					menu.add(item);
				}
				menu.setPreferredSize(new Dimension(Math.max(box.getWidth(), menu.getPreferredSize().width), menu.getPreferredSize().height));
				menu.show(box, 0, box.getHeight() + 1);
			}
		});
		return box;
	}

	/** Drop-downs longer than this scroll. */
	static final int LONG_LIST = 12;

	/** The options of a long drop-down, in a scrolling list showing {@link #LONG_LIST} at a time. */
	private static javax.swing.JScrollPane scrollingList(java.util.List<String> labels, int selected,
		java.util.function.IntConsumer pick, int width)
	{
		javax.swing.JList<String> list = new javax.swing.JList<>(labels.toArray(new String[0]));
		list.setFont(FontManager.getRunescapeFont());
		list.setBackground(CARD_HOVER);
		list.setForeground(TEXT);
		list.setSelectionBackground(new Color(0x6E, 0x37, 0x15));
		list.setSelectionForeground(TEXT);
		list.setFixedCellHeight(18);
		list.setVisibleRowCount(LONG_LIST);
		list.setCellRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> l, Object value, int index, boolean isSelected, boolean focus)
			{
				JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, isSelected, false);
				c.setBorder(new EmptyBorder(0, 6, 0, 6));
				c.setForeground(index == selected ? HIGHLIGHT : TEXT);
				return c;
			}
		});
		// Hovering lights a row; clicking picks it.
		list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(java.awt.event.MouseEvent e)
			{
				list.setSelectedIndex(list.locationToIndex(e.getPoint()));
			}
		});
		list.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseReleased(java.awt.event.MouseEvent e)
			{
				int index = list.locationToIndex(e.getPoint());
				if (index >= 0)
				{
					pick.accept(index);
				}
			}
		});
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(list, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.setPreferredSize(new Dimension(Math.max(width, list.getPreferredSize().width + 16), 18 * LONG_LIST));
		if (selected >= 0)
		{
			list.ensureIndexIsVisible(selected);
		}
		return scroll;
	}

	/** Two drop-downs side by side, or one across the width when {@code right} is null. */
	static JPanel dropdowns(JComponent left, JComponent right)
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, right != null ? 2 : 1, 6, 0));
		row.setOpaque(false);
		row.add(left);
		if (right != null)
		{
			row.add(right);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
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

	/** A heading above a group of cards, in small grey capitals. */
	static JLabel section(String text)
	{
		JLabel label = new JLabel(text == null ? "" : text.toUpperCase(java.util.Locale.UK));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(QUIET);
		label.setBorder(new EmptyBorder(2, 2, 0, 0));
		return label;
	}

	// ---- item pictures ----

	/** Hands out item pictures. The plugin sets RuneLite's; tests and previews use their own or none. */
	interface ItemIcons
	{
		/** Calls {@code done} on the Swing thread with the item's picture once it's loaded. */
		void image(int itemId, java.util.function.Consumer<java.awt.image.BufferedImage> done);
	}

	private static volatile ItemIcons itemIcons = (id, done) -> { };

	static void setItemIcons(ItemIcons icons)
	{
		itemIcons = icons != null ? icons : (id, done) -> { };
	}

	/** Hands out an event's picture: the Grid Master icon for bingo, the boss for BOTW, the skill for SOTW. */
	interface EventIcons
	{
		void image(ClanData.Event event, java.util.function.Consumer<java.awt.image.BufferedImage> done);
	}

	private static volatile EventIcons eventIcons = (e, done) -> { };

	/** Puts a boss's hiscore picture on a label; set by the panel. */
	private static volatile java.util.function.BiConsumer<JLabel, String> bossIcons = (label, boss) -> { };

	static void setBossIcons(java.util.function.BiConsumer<JLabel, String> icons)
	{
		bossIcons = icons != null ? icons : (label, boss) -> { };
	}

	/** Puts a skill's icon on a label; set by the plugin. */
	private static volatile java.util.function.BiConsumer<JLabel, String> skillIcons = (label, skill) -> { };

	static void setSkillIcons(java.util.function.BiConsumer<JLabel, String> icons)
	{
		skillIcons = icons != null ? icons : (label, skill) -> { };
	}

	/** A dark box with the skill's icon, or "#1" until (or unless) there is one. */
	static JLabel skillBox(String skill, int size)
	{
		JLabel box = placeBox(1, size);
		skillIcons.accept(box, skill);
		if (box.getIcon() != null)
		{
			box.setText("");
		}
		box.addPropertyChangeListener("icon", e -> box.setText(box.getIcon() != null ? "" : "#1"));
		return box;
	}

	/** A dark box with the boss's picture, or "#1" until (or unless) there is one. */
	static JLabel bossBox(String boss, int size)
	{
		JLabel box = placeBox(1, size);
		bossIcons.accept(box, boss);
		if (box.getIcon() != null)
		{
			box.setText("");
		}
		box.addPropertyChangeListener("icon", e -> box.setText(box.getIcon() != null ? "" : "#1"));
		return box;
	}

	static void setEventIcons(EventIcons icons)
	{
		eventIcons = icons != null ? icons : (e, done) -> { };
	}

	/** A dark rounded box holding the event's picture. */
	static JLabel eventBox(ClanData.Event event, int size)
	{
		JLabel box = itemBox(null, size);
		eventIcons.image(event, img ->
		{
			double s = Math.min((size - 6) / (double) img.getWidth(), (size - 6) / (double) img.getHeight());
			// Small game icons are scaled up, pixel by pixel so they stay sharp; big ones are scaled down smoothly.
			box.setIcon(new javax.swing.ImageIcon(img.getScaledInstance(Math.max(1, (int) Math.round(img.getWidth() * s)),
				Math.max(1, (int) Math.round(img.getHeight() * s)), s >= 1 ? java.awt.Image.SCALE_REPLICATE : java.awt.Image.SCALE_SMOOTH)));
		});
		return box;
	}

	/** A dark rounded box holding an item's picture, scaled to fit. Empty when the item has no picture. */
	static JLabel itemBox(Integer itemId, int size)
	{
		JLabel box = new JLabel("", javax.swing.SwingConstants.CENTER)
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				roundBox(g, getWidth(), getHeight(), BACKGROUND, null);
				super.paintComponent(g);
			}
		};
		Dimension d = new Dimension(size, size);
		box.setPreferredSize(d);
		box.setMinimumSize(d);
		box.setMaximumSize(d);
		if (itemId != null && itemId > 0)
		{
			itemIcons.image(itemId, img -> box.setIcon(new javax.swing.ImageIcon(fit(img, size - 4, size - 4))));
		}
		return box;
	}

	/** Loads an item's picture for a component that draws it itself. */
	static void itemImage(Integer itemId, java.util.function.Consumer<java.awt.image.BufferedImage> done)
	{
		if (itemId != null && itemId > 0)
		{
			itemIcons.image(itemId, done);
		}
	}

	/** The picture scaled down (never up) to fit w by h, keeping its shape. */
	static java.awt.Image fit(java.awt.image.BufferedImage img, int w, int h)
	{
		double s = Math.min(1.0, Math.min(w / (double) img.getWidth(), h / (double) img.getHeight()));
		if (s >= 1.0)
		{
			return img;
		}
		return img.getScaledInstance(Math.max(1, (int) (img.getWidth() * s)), Math.max(1, (int) (img.getHeight() * s)),
			java.awt.Image.SCALE_SMOOTH);
	}

	// ---- controls drawn in the panel's style ----

	/** Tabs with an orange line under the picked one, like the mock-up's Board / List / Scores. */
	static JPanel tabs(java.util.List<String> labels, int selected, java.util.function.IntConsumer pick)
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, Math.max(1, labels.size()), 0, 0));
		row.setOpaque(false);
		for (int i = 0; i < labels.size(); i++)
		{
			int index = i;
			boolean on = i == selected;
			JLabel tab = new JLabel(labels.get(i), javax.swing.SwingConstants.CENTER)
			{
				private boolean hover;

				{
					addMouseListener(new java.awt.event.MouseAdapter()
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
							hover = true;
							setForeground(on ? TEXT : HIGHLIGHT);
						}

						@Override
						public void mouseExited(java.awt.event.MouseEvent e)
						{
							hover = false;
							setForeground(on ? TEXT : QUIET);
						}
					});
				}

				@Override
				protected void paintComponent(java.awt.Graphics g)
				{
					super.paintComponent(g);
					g.setColor(on ? HIGHLIGHT : LINE);
					int h = on ? 2 : 1;
					g.fillRect(0, getHeight() - h, getWidth(), h);
				}
			};
			tab.setFont(FontManager.getRunescapeBoldFont());
			tab.setForeground(on ? TEXT : QUIET);
			tab.setBorder(new EmptyBorder(4, 2, 6, 2));
			tab.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			row.add(tab);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** A dark bar filled in orange by have / need, with an optional label across it. */
	static JComponent progress(double have, double need, String label)
	{
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				g.setColor(new Color(0x14, 0x0E, 0x0B));
				g.fillRect(0, 0, getWidth(), getHeight());
				double share = need > 0 ? Math.max(0, Math.min(1, have / need)) : 0;
				g.setColor(new Color(0x9A, 0x4A, 0x18));
				g.fillRect(0, 0, (int) Math.round(getWidth() * share), getHeight());
				if (label != null)
				{
					java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
					g2.setFont(FontManager.getRunescapeSmallFont());
					java.awt.FontMetrics fm = g2.getFontMetrics();
					int x = (getWidth() - fm.stringWidth(label)) / 2;
					int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
					g2.setColor(new Color(0, 0, 0, 160));
					g2.drawString(label, x + 1, y + 1);
					g2.setColor(TEXT);
					g2.drawString(label, x, y);
					g2.dispose();
				}
			}
		};
		bar.setPreferredSize(new Dimension(10, 13));
		bar.setMinimumSize(new Dimension(10, 13));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 13));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		return bar;
	}

	/**
	 * A rounded bar showing how far through something is, with text on it that reads over both the filled and empty
	 * parts: {@code left} in white, {@code right} in grey, each with a dark outline.
	 */
	static JComponent timeBar(double done, Color colour, String left, String right)
	{
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth(), h = getHeight();
				g2.setColor(new Color(0x14, 0x0E, 0x0B));
				g2.fillRoundRect(0, 0, w, h, h, h);
				java.awt.Shape clip = g2.getClip();
				g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, h, h));
				int filled = (int) Math.round(w * Math.max(0, Math.min(1, done)));
				g2.setColor(new Color(colour.getRed() / 2, colour.getGreen() / 2, colour.getBlue() / 2));
				g2.fillRect(0, 0, filled, h);
				g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 200));
				g2.fillRect(0, h - 3, filled, 3);
				g2.setClip(clip);
				g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 120));
				g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
				g2.setFont(FontManager.getRunescapeSmallFont());
				java.awt.FontMetrics fm = g2.getFontMetrics();
				int y = (h + fm.getAscent() - fm.getDescent()) / 2;
				if (left != null)
				{
					outlined(g2, left, 7, y, TEXT);
				}
				if (right != null)
				{
					outlined(g2, right, w - 7 - fm.stringWidth(right), y, QUIET);
				}
				g2.dispose();
			}
		};
		bar.setPreferredSize(new Dimension(10, 15));
		bar.setMinimumSize(new Dimension(10, 15));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setToolTipText(left != null && right != null ? left + " · " + right : left);
		return bar;
	}

	/** Text with a dark outline all round, readable over any colour. */
	private static void outlined(java.awt.Graphics2D g, String text, int x, int y, Color colour)
	{
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setColor(new Color(0, 0, 0, 200));
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					g.drawString(text, x + dx, y + dy);
				}
			}
		}
		g.setColor(colour);
		g.drawString(text, x, y);
	}

	/** A small green tick in a circle, for "you're in". */
	static JComponent tickIcon()
	{
		JComponent tick = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				int y = (getHeight() - 10) / 2;
				g2.setColor(GOOD);
				g2.fillOval(0, y, 10, 10);
				g2.setColor(BACKGROUND);
				g2.setStroke(new java.awt.BasicStroke(1.6f));
				g2.drawPolyline(new int[]{3, 4, 7}, new int[]{y + 5, y + 7, y + 3}, 3);
				g2.dispose();
			}
		};
		tick.setPreferredSize(new Dimension(10, 12));
		return tick;
	}

	// ---- places: shared by the Leaderboard and PBs ----

	static final Color SILVER = new Color(0xC0, 0xC0, 0xC8);
	static final Color BRONZE = new Color(0xA8, 0x73, 0x4E);

	/** Gold, silver or bronze for first to third; null after that. */
	static Color medal(Integer rank)
	{
		if (rank == null)
		{
			return null;
		}
		switch (rank)
		{
			case 1:
				return GOLD;
			case 2:
				return SILVER;
			case 3:
				return BRONZE;
			default:
				return null;
		}
	}

	/**
	 * First place: a gold card with a picture (or "#1"), a gold heading, the result in large text, and who, in orange.
	 */
	static Card firstPlace(JComponent picture, String heading, String result, String who)
	{
		Card card = new Card(null).outlined(GOLD, CARD_WARM);
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(picture, BorderLayout.WEST);
		JPanel words = column();
		JLabel head = plain(heading, GOLD);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		words.add(head);
		JLabel value = bold(result, TEXT);
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		words.add(value);
		JLabel name = cut(who, HIGHLIGHT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		words.add(name);
		row.add(words, BorderLayout.CENTER);
		card.addWide(row);
		return card;
	}

	/** A dark box with "#1" in gold, for a first place without a picture. */
	static JLabel placeBox(int rank, int size)
	{
		JLabel box = new JLabel("#" + rank, javax.swing.SwingConstants.CENTER)
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				roundBox(g, getWidth(), getHeight(), BACKGROUND, null);
				super.paintComponent(g);
			}
		};
		box.setFont(FontManager.getRunescapeBoldFont());
		Color m = medal(rank);
		box.setForeground(m != null ? m : QUIET);
		Dimension d = new Dimension(size, size);
		box.setPreferredSize(d);
		box.setMinimumSize(d);
		return box;
	}

	/**
	 * A place below first: its number (silver or bronze for second and third, with an outline to match), the name and
	 * the result. The member's own row is orange.
	 */
	static Card placeCard(Integer rank, String name, String result, Color resultColour, boolean mine)
	{
		Card card = new Card(null);
		card.setBorder(new EmptyBorder(4, 8, 4, 8));
		Color m = medal(rank);
		if (mine)
		{
			card.outlined(m != null ? m : HIGHLIGHT, CARD_WARM);
		}
		else if (m != null)
		{
			card.outlined(m, CARD);
		}
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		JLabel place = bold(rank != null ? String.valueOf(rank) : "–", m != null ? m : QUIET);
		place.setPreferredSize(new Dimension(22, place.getPreferredSize().height));
		line.add(place, BorderLayout.WEST);
		line.add(cut((name != null ? name : "?") + (mine ? "  (you)" : ""), mine ? HIGHLIGHT : TEXT), BorderLayout.CENTER);
		line.add(plain(result != null ? result : "", resultColour), BorderLayout.EAST);
		card.addWide(line);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	/** A small tick box with its text, in the panel's colours. */
	static JPanel checkbox(String text, boolean on, Runnable toggle)
	{
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(4, 2, 0, 0));
		JComponent box = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				roundBox(g, 11, 11, on ? HIGHLIGHT : CARD, LINE);
				if (on)
				{
					java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
					g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(BACKGROUND);
					g2.setStroke(new java.awt.BasicStroke(2f));
					g2.drawPolyline(new int[]{2, 5, 9}, new int[]{5, 8, 3}, 3);
					g2.dispose();
				}
			}
		};
		box.setPreferredSize(new Dimension(11, 11));
		row.add(box);
		row.add(javax.swing.Box.createHorizontalStrut(5));
		row.add(plain(text, TEXT));
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				toggle.run();
			}
		});
		return row;
	}

	/** An on/off switch: orange with the knob on the right when on. */
	static JComponent toggle(boolean on, Runnable action)
	{
		JComponent sw = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				int y = (getHeight() - 13) / 2;
				g2.setColor(on ? HIGHLIGHT : LINE);
				g2.fillRoundRect(0, y, 26, 13, 13, 13);
				g2.setColor(TEXT);
				g2.fillOval(on ? 14 : 1, y + 1, 11, 11);
				g2.dispose();
			}
		};
		Dimension d = new Dimension(26, 15);
		sw.setPreferredSize(d);
		sw.setMinimumSize(d);
		sw.setMaximumSize(d);
		sw.setToolTipText(on ? "On: click to turn off" : "Off: click to turn on");
		sw.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		sw.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				action.run();
			}
		});
		return sw;
	}

	/** A small coloured dot, for online and status. */
	static JComponent dotIcon(Color colour)
	{
		JComponent dot = new JComponent()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(colour);
				g2.fillOval(0, (getHeight() - 6) / 2, 6, 6);
				g2.dispose();
			}
		};
		dot.setPreferredSize(new Dimension(6, 12));
		return dot;
	}

	/** A label in the bold font, cut short with "…" when it doesn't fit. */
	static JLabel bold(String text, Color colour)
	{
		JLabel label = new JLabel(text == null ? "" : text.regionMatches(true, 0, "<html", 0, 5) ? " " + text : text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(colour);
		label.setToolTipText(text);
		label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
		return label;
	}

	/** A line of text that is cut short with "…" rather than pushing the card wider. */
	static JLabel cut(String text, Color colour)
	{
		JLabel label = plain(text == null ? "" : text.regionMatches(true, 0, "<html", 0, 5) ? " " + text : text, colour);
		label.setToolTipText(text);
		label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
		label.setPreferredSize(new Dimension(0, label.getPreferredSize().height));
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
				label.setForeground(TEXT);
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
			option.setBackground(on ? HIGHLIGHT : CARD);
			option.setForeground(on ? ON_HIGHLIGHT : TEXT);
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
						option.setBackground(CARD_HOVER);
					}
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					option.setBackground(on ? HIGHLIGHT : CARD);
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
		heading.setForeground(TEXT);
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
				heading.setForeground(TEXT);
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
		return button(text, false, action);
	}

	/** A themed button; {@code primary} ones are solid orange. */
	static JButton button(String text, boolean primary, Runnable action)
	{
		JButton button = new JButton(text)
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				boolean hover = getModel().isRollover() && isEnabled();
				Color fill = primary ? (hover ? HIGHLIGHT.brighter() : HIGHLIGHT) : hover ? CARD_HOVER : CARD;
				roundBox(g, getWidth(), getHeight(), isEnabled() ? fill : CARD, primary ? null : hover ? HIGHLIGHT : LINE);
				super.paintComponent(g);
			}
		};
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setOpaque(false);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(primary ? ON_HIGHLIGHT : TEXT);
		button.setBorder(new EmptyBorder(5, 8, 5, 8));
		button.setRolloverEnabled(true);
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
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
