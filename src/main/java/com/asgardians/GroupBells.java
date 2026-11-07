package com.asgardians;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.Icon;

/**
 * Which group activities the member hears about in chat when a clanmate starts a group. The website decides, per
 * activity, whether it's up to the member ("on" or "off" by default) or sent to everyone ("always"). The member's own
 * choices are saved as "id=1,id=0".
 */
final class GroupBells
{
	static final String ON = "on";
	static final String OFF = "off";
	static final String ALWAYS = "always";

	private GroupBells()
	{
	}

	/** The website's setting for an activity: on, off or always. Anything else counts as on. */
	static String mode(ClanData.GroupActivity activity)
	{
		String alerts = activity != null && activity.getAlerts() != null ? activity.getAlerts().trim().toLowerCase(Locale.UK) : "";
		return OFF.equals(alerts) || ALWAYS.equals(alerts) ? alerts : ON;
	}

	/**
	 * Whether a new group for this activity is announced to the member.
	 *
	 * @param choices  the member's bells, by activity ID
	 * @param alertsOn the "New groups in chat" setting; off silences every activity the member can choose about
	 */
	static boolean rings(ClanData.GroupActivity activity, Map<String, Boolean> choices, boolean alertsOn)
	{
		String mode = mode(activity);
		if (ALWAYS.equals(mode))
		{
			return true;
		}
		return alertsOn && bell(activity, choices);
	}

	/** The bell as shown: the member's choice, or the website's default when they haven't chosen. */
	static boolean bell(ClanData.GroupActivity activity, Map<String, Boolean> choices)
	{
		String mode = mode(activity);
		if (ALWAYS.equals(mode))
		{
			return true;
		}
		Boolean chosen = activity != null && activity.getId() != null && choices != null ? choices.get(activity.getId()) : null;
		return chosen != null ? chosen : ON.equals(mode);
	}

	/**
	 * Whether a new group for this activity also shows on screen: only when it rings, the "New groups on screen"
	 * setting is on, and the member hasn't turned the activity's screen icon off (it's on until they do).
	 */
	static boolean onScreen(ClanData.GroupActivity activity, Map<String, Boolean> choices, Map<String, Boolean> screens,
		boolean alertsOn, boolean screenOn)
	{
		return screenOn && rings(activity, choices, alertsOn) && screen(activity, screens);
	}

	/** The screen icon as shown: the member's choice, otherwise on. */
	static boolean screen(ClanData.GroupActivity activity, Map<String, Boolean> screens)
	{
		Boolean chosen = activity != null && activity.getId() != null && screens != null ? screens.get(activity.getId()) : null;
		return chosen == null || chosen;
	}

	static Map<String, Boolean> parse(String saved)
	{
		Map<String, Boolean> out = new LinkedHashMap<>();
		if (saved == null)
		{
			return out;
		}
		for (String part : saved.split(","))
		{
			int eq = part.lastIndexOf('=');
			if (eq > 0)
			{
				String id = part.substring(0, eq).trim();
				String value = part.substring(eq + 1).trim();
				if (!id.isEmpty() && ("1".equals(value) || "0".equals(value)))
				{
					out.put(id, "1".equals(value));
				}
			}
		}
		return out;
	}

	static String format(Map<String, Boolean> choices)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Boolean> e : choices.entrySet())
		{
			// IDs come from the website; leave out any that would break the saved format.
			if (e.getKey() != null && e.getValue() != null && !e.getKey().contains(",") && !e.getKey().contains("="))
			{
				out.append(out.length() > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue() ? '1' : '0');
			}
		}
		return out.toString();
	}

	/** A small screen: orange when new groups show on screen, grey and struck through when not, dim when it can't. */
	static final class ScreenIcon implements Icon
	{
		private static final Color MUTED = new Color(0x70, 0x70, 0x70);
		private static final Color DIM = new Color(0x48, 0x48, 0x48);
		private final Color colour;
		private final boolean struck;

		ScreenIcon(boolean on, boolean usable)
		{
			this.colour = !usable ? DIM : on ? PanelParts.HIGHLIGHT : MUTED;
			this.struck = !on && usable;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.translate(x, y);
			g2.setColor(colour);
			g2.setStroke(new BasicStroke(1.6f));
			g2.drawRoundRect(1, 2, 12, 8, 2, 2);
			g2.fillRect(3, 4, 8, 4);
			g2.fillRect(6, 10, 2, 2);
			g2.fillRect(4, 12, 6, 1);
			if (struck)
			{
				g2.setColor(PanelParts.TEXT);
				g2.drawLine(1, 13, 13, 1);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 14;
		}

		@Override
		public int getIconHeight()
		{
			return 14;
		}
	}

	/** A bell, drawn since the game fonts have none: orange when ringing, grey and struck through when muted. */
	static final class BellIcon implements Icon
	{
		private static final Color MUTED = new Color(0x70, 0x70, 0x70);
		private final Color colour;
		private final boolean struck;

		BellIcon(boolean on, boolean locked)
		{
			this.colour = locked ? PanelParts.TEST_SERVER : on ? PanelParts.HIGHLIGHT : MUTED;
			this.struck = !on;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.translate(x, y);
			g2.setColor(colour);
			GeneralPath bell = new GeneralPath();
			bell.moveTo(7, 1.5);
			bell.curveTo(3.5, 1.5, 3, 4.5, 3, 7.5);
			bell.lineTo(3, 9.5);
			bell.lineTo(1.5, 11.5);
			bell.lineTo(12.5, 11.5);
			bell.lineTo(11, 9.5);
			bell.lineTo(11, 7.5);
			bell.curveTo(11, 4.5, 10.5, 1.5, 7, 1.5);
			bell.closePath();
			g2.fill(bell);
			g2.fillOval(5, 11, 4, 3);
			if (struck)
			{
				g2.setStroke(new BasicStroke(1.6f));
				g2.setColor(PanelParts.TEXT);
				g2.drawLine(1, 13, 13, 1);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 14;
		}

		@Override
		public int getIconHeight()
		{
			return 14;
		}
	}
}
