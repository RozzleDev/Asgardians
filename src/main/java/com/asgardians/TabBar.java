package com.asgardians;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The side panel's tabs, as two rows of solid buttons (three on top, the rest below). RuneLite's own tab group needs one row, and six tabs don't fit
 * across the side panel. Swing thread only.
 */
class TabBar extends JPanel
{
	private static final int SPACING = 4;

	/** One button. Its text can change, for the unread count. */
	class Tab extends JLabel
	{
		private final JComponent content;
		private final Runnable opened;

		private Tab(String text, JComponent content, Runnable opened)
		{
			super(text, SwingConstants.CENTER);
			this.content = content;
			this.opened = opened;
			setFont(FontManager.getRunescapeBoldFont());
			setOpaque(true);
			setBorder(new EmptyBorder(5, 2, 5, 2));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					select(Tab.this);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					if (selected != Tab.this)
					{
						setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					paintState();
				}
			});
			paintState();
		}

		@Override
		public void setText(String text)
		{
			boolean changed = !java.util.Objects.equals(text, getText());
			super.setText(text);
			// "News (2)": the bottom row shares its width by name length, so share it out again.
			if (changed && tabs != null && tabs.contains(this))
			{
				layOut();
			}
		}

		private void paintState()
		{
			boolean on = selected == this;
			setBackground(on ? PanelParts.HIGHLIGHT : ColorScheme.DARKER_GRAY_COLOR);
			setForeground(on ? Color.WHITE : PanelParts.TEXT);
		}
	}

	private final JPanel display;
	private final List<Tab> tabs = new ArrayList<>();
	private Tab selected;
	private Runnable changed;

	/** @param display where the selected tab's content is shown */
	TabBar(JPanel display)
	{
		super(new GridLayout(0, 1, SPACING, SPACING));
		this.display = display;
		setOpaque(false);
	}

	/**
	 * Lays the buttons out in two rows: Status, Events and Groups on top, the rest below. The bottom row shares its
	 * width by the length of each name, so "Leaderboard" isn't cut short beside two short names.
	 */
	private void layOut()
	{
		removeAll();
		int top = tabs.size() > 4 ? 3 : 2;
		JPanel first = new JPanel(new GridLayout(1, Math.min(top, tabs.size()), SPACING, 0));
		first.setOpaque(false);
		for (Tab t : tabs.subList(0, Math.min(top, tabs.size())))
		{
			first.add(t);
		}
		add(first);
		if (tabs.size() > top)
		{
			JPanel rest = new JPanel(new GridBagLayout());
			rest.setOpaque(false);
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.weighty = 1;
			c.gridx = 0;
			c.gridy = 0;
			for (Tab t : tabs.subList(top, tabs.size()))
			{
				c.insets = new Insets(0, c.gridx == 0 ? 0 : SPACING, 0, 0);
				c.weightx = t.getPreferredSize().width;
				// Share by name length, not by what each name needs right now, which would leave no room to spare.
				t.setMinimumSize(new java.awt.Dimension(0, t.getPreferredSize().height));
				rest.add(t, c);
				c.gridx++;
			}
			add(rest);
		}
		revalidate();
	}

	/** @param opened run each time the member opens this tab; can be null */
	Tab addTab(String text, JComponent content, Runnable opened)
	{
		Tab tab = new Tab(text, content, opened);
		tabs.add(tab);
		layOut();
		return tab;
	}

	/** Run each time the selected tab changes. */
	void onChange(Runnable changed)
	{
		this.changed = changed;
	}

	boolean isSelected(Tab tab)
	{
		return selected == tab;
	}

	void select(Tab tab)
	{
		selected = tab;
		if (changed != null)
		{
			changed.run();
		}
		tabs.forEach(Tab::paintState);
		display.removeAll();
		display.add(tab.content, BorderLayout.CENTER);
		display.revalidate();
		display.repaint();
		if (tab.opened != null)
		{
			tab.opened.run();
		}
	}
}
