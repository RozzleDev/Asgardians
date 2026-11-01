package com.asgardians;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
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
 * The side panel's tabs, as two rows of solid buttons. RuneLite's own tab group needs one row, and four tabs don't fit
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

	/** @param display where the selected tab's content is shown */
	TabBar(JPanel display)
	{
		super(new GridLayout(2, 2, SPACING, SPACING));
		this.display = display;
		setOpaque(false);
	}

	/** @param opened run each time the member opens this tab; can be null */
	Tab addTab(String text, JComponent content, Runnable opened)
	{
		Tab tab = new Tab(text, content, opened);
		tabs.add(tab);
		add(tab);
		return tab;
	}

	void select(Tab tab)
	{
		selected = tab;
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
