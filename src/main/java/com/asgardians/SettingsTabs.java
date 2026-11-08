package com.asgardians;

import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.addCard;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;

/**
 * The settings page behind the header's cog: the plugin's on/off settings as switches, grouped as in RuneLite's plugin
 * settings, which they change. The API address stays in RuneLite's settings only. Swing thread.
 */
final class SettingsTabs
{
	@Value
	static class Setting
	{
		String key;
		String name;
		boolean byDefault;
	}

	@Value
	static class Group
	{
		String title;
		List<Setting> settings;
	}

	/** Same keys, names and defaults as {@link AsgardiansConfig}. */
	static final List<Group> GROUPS = List.of(
		new Group("Sharing", List.of(
			new Setting("shareData", "Share my data with Asgardians", false),
			new Setting("shareActivity", "Share my activity", true),
			new Setting("shareDeaths", "Share my deaths", true),
			new Setting("uploadScreenshots", "Upload screenshots", true),
			new Setting("saveScreenshots", "Save screenshots locally", true))),
		new Group("Events and news", List.of(
			new Setting("eventMessages", "Event messages in chat", true),
			new Setting("newsMessages", "Announcements in chat", true),
			new Setting("teamBadges", "Team badges in clan chat", true),
			new Setting("showCodeword", "Show event codeword", true))),
		new Group("Groups", List.of(
			new Setting("groupAlerts", "New groups in chat", true),
			new Setting("groupOverlay", "New groups on screen", true),
			new Setting("groupParty", "Join a group's RuneLite party", true))),
		new Group("Handeggs", List.of(
			new Setting("eggMessages", "Egg messages in chat", true),
			new Setting("eggSound", "Sound when egged", true))));

	private SettingsTabs()
	{
	}

	/** A setting's value: what's saved, else its default. */
	static boolean value(AsgardiansPanel.Settings settings, Setting s)
	{
		String saved = settings.get(s.getKey());
		return saved == null ? s.isByDefault() : Boolean.parseBoolean(saved.trim());
	}

	static void draw(JPanel column, AsgardiansPanel.Settings settings, Runnable redraw)
	{
		column.removeAll();
		boolean first = true;
		for (Group group : GROUPS)
		{
			if (!first)
			{
				column.add(Box.createVerticalStrut(PanelParts.GAP + 2));
			}
			first = false;
			javax.swing.JLabel heading = PanelParts.section(group.getTitle());
			heading.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			column.add(heading);
			for (Setting s : group.getSettings())
			{
				boolean on = value(settings, s);
				Card card = new Card(null);
				card.setBorder(new EmptyBorder(6, 8, 6, 8));
				JPanel row = new JPanel(new BorderLayout(6, 0));
				row.setOpaque(false);
				row.add(PanelParts.cut(s.getName(), TEXT), BorderLayout.CENTER);
				row.add(PanelParts.toggle(on, () ->
				{
					settings.set(s.getKey(), String.valueOf(!on));
					redraw.run();
				}), BorderLayout.EAST);
				card.addWide(row);
				column.add(Box.createVerticalStrut(4));
				card.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				column.add(card);
			}
		}
		Card note = new Card(null);
		note.add(small("These are the plugin's settings in RuneLite too, where each one says more about what it does.", QUIET));
		addCard(column, note);
	}
}
