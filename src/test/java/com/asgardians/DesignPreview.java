package com.asgardians;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A window showing the Groups tab's "Group alerts" card and the new-group overlay with sample data, so design changes
 * can be seen without starting RuneLite. Run it with C:\claude\Preview.bat, which rebuilds first. Clicking the bells and
 * screen icons works as in the plugin.
 */
public class DesignPreview
{
	private static final Map<String, Boolean> bells = new HashMap<>();
	private static final Map<String, Boolean> screens = new HashMap<>();
	private static boolean open = true;

	private static ClanData.GroupActivity activity(String id, String name, String alerts, String shortName)
	{
		ClanData.GroupActivity a = new ClanData.GroupActivity();
		a.setId(id);
		a.setName(name);
		a.setAlerts(alerts);
		a.setShortName(shortName);
		a.setModeShorts(Map.of("Hard mode", "HM", "Challenge Mode", "CM", "Expert", "Exp"));
		return a;
	}

	private static ClanData.GroupActivity find(String id)
	{
		return ACTIVITIES.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
	}

	private static final List<ClanData.GroupActivity> ACTIVITIES = List.of(
		activity("cox", "Chambers of Xeric", null, "CoX"),
		activity("tob", "Theatre of Blood", null, "ToB"),
		activity("toa", "Tombs of Amascut", null, "ToA"),
		activity("nex", "Nex", "always", null),
		activity("ba", "Barbarian Assault", "off", "BA"),
		activity("cg", "Corrupted Gauntlet", null, "CG"));

	private static final String GROUPS = "{\"groups\":["
		+ "{\"id\":\"g1\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"mode\":\"Hard mode\",\"size\":4,\"world\":416,"
		+ "\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"}]},"
		+ "{\"id\":\"g2\",\"activity\":\"nex\",\"name\":\"Nex\",\"world\":302,\"host\":\"Str4tty\","
		+ "\"members\":[{\"rsn\":\"Str4tty\"},{\"rsn\":\"Rozzle\"}]},"
		+ "{\"id\":\"g3\",\"activity\":\"cox\",\"name\":\"Chambers of Xeric\",\"size\":3,\"world\":330,\"host\":\"pricey\","
		+ "\"members\":[{\"rsn\":\"pricey\"}]}]}";

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(DesignPreview::show);
	}

	private static void show()
	{
		JCheckBox alertsOn = new JCheckBox("New groups in chat", true);
		JCheckBox screenOn = new JCheckBox("New groups on screen", true);

		JPanel column = PanelParts.column();
		Runnable[] redraw = new Runnable[1];
		GroupTabs.Actions actions = new GroupTabs.Actions()
		{
			public void create(GroupTabs.GroupForm.Values v)
			{
			}

			public void join(ClanData.Group g)
			{
			}

			public void leave(ClanData.Group g)
			{
			}

			public void close(ClanData.Group g)
			{
			}

			public void kick(ClanData.Group g, String rsn)
			{
			}

			public void joinParty(ClanData.Group g)
			{
			}

			public void refresh()
			{
			}

			@Override
			public void bell(String id, boolean on)
			{
				bells.put(id, on);
				redraw[0].run();
			}

			@Override
			public void screen(String id, boolean on)
			{
				screens.put(id, on);
				redraw[0].run();
			}

			@Override
			public void foldBells()
			{
				open = !open;
				redraw[0].run();
			}
		};
		redraw[0] = () ->
		{
			column.removeAll();
			PanelParts.addCard(column, GroupTabs.bellsCard(ACTIVITIES,
				new GroupTabs.Bells(bells, alertsOn.isSelected(), open, screens, screenOn.isSelected()), actions));
			column.revalidate();
			column.repaint();
		};
		alertsOn.addActionListener(e -> redraw[0].run());
		screenOn.addActionListener(e -> redraw[0].run());
		redraw[0].run();

		// The side panel, at RuneLite's width.
		JPanel side = new JPanel(new BorderLayout());
		side.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(column, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(top);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setPreferredSize(new Dimension(242, 560));
		side.add(scroll, BorderLayout.CENTER);

		// The overlay, drawn as RuneLite would on a game-coloured background.
		GroupAlertOverlay overlay = new GroupAlertOverlay(null, screenOn::isSelected, System::currentTimeMillis);
		ClanData.GroupList list = new com.google.gson.Gson().fromJson(GROUPS, ClanData.GroupList.class);
		int[] next = {0};
		JPanel game = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setFont(FontManager.getRunescapeFont());
				Dimension size = overlay.render(g2);
				g2.dispose();
				if (size == null)
				{
					g.setColor(Color.GRAY);
					g.drawString("No alerts showing. Press \"New group\".", 20, 30);
				}
			}
		};
		game.setBackground(new Color(0x4A, 0x5A, 0x34));
		game.setPreferredSize(new Dimension(420, 560));
		new Timer(500, e -> game.repaint()).start();

		JButton add = new JButton("New group");
		add.addActionListener(e ->
		{
			ClanData.Group g = list.getGroups().get(next[0]++ % list.getGroups().size());
			overlay.add(g, Groups.shortTitle(g, find(g.getActivity())));
			System.out.println(Groups.startedLine(g, Groups.shortTitle(g, find(g.getActivity()))));
			game.repaint();
		});
		JButton clear = new JButton("Clear");
		clear.addActionListener(e ->
		{
			overlay.clear();
			game.repaint();
		});
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(alertsOn);
		controls.add(screenOn);
		controls.add(add);
		controls.add(clear);

		JFrame frame = new JFrame("Asgardians design preview");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(controls, BorderLayout.NORTH);
		frame.add(game, BorderLayout.CENTER);
		frame.add(side, BorderLayout.EAST);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
}
