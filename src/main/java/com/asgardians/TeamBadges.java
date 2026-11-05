package com.asgardians;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.IconID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.Text;

/** Team badges for clan chat during a live event: which team each player is on, shown in place of their rank icon. */
final class TeamBadges
{
	/** How many teams get a badge; chat icons are reserved up front, one per team. */
	static final int MAX_TEAMS = 8;

	/** Used for teams the website gives no colour. */
	private static final Color[] PALETTE = {
		new Color(0xE8, 0x76, 0x3F), new Color(0x3F, 0x9C, 0xE8), new Color(0x5C, 0xC0, 0x5C), new Color(0xC9, 0x5C, 0xD6),
		new Color(0xE8, 0xC8, 0x3F), new Color(0x3F, 0xD6, 0xC8), new Color(0xE8, 0x4F, 0x6A), new Color(0xA0, 0xA0, 0xA0)};

	private static final Pattern IMG = Pattern.compile("<img=(\\d+)>");
	private static final Set<Integer> GAME_ICONS = new HashSet<>();

	static
	{
		for (IconID icon : IconID.values())
		{
			GAME_ICONS.add(icon.getIndex());
		}
	}

	@Value
	static class Badge
	{
		/** Which reserved chat icon this team uses, 0 to MAX_TEAMS - 1. */
		int slot;
		String tag;
		Color colour;
	}

	private TeamBadges()
	{
	}

	/** Each team's badge, by team ID, for the event that gets badges; empty when there isn't one. */
	static Map<String, Badge> teams(ClanView view)
	{
		Map<String, Badge> out = new LinkedHashMap<>();
		ClanView.EventView event = view.badgeEvent();
		if (event == null)
		{
			return out;
		}
		Map<String, String> tags = event.tags();
		int slot = 0;
		for (ClanData.Team team : event.teams())
		{
			if (slot >= MAX_TEAMS || team.getId() == null)
			{
				break;
			}
			out.put(team.getId(), new Badge(slot, tags.get(team.getId()), colour(team.getColour(), PALETTE[slot])));
			slot++;
		}
		return out;
	}

	/** Each player in the event, lowercased with spaces normalised, to their team's badge. */
	static Map<String, Badge> players(ClanView view)
	{
		Map<String, Badge> out = new LinkedHashMap<>();
		ClanView.EventView event = view.badgeEvent();
		if (event == null)
		{
			return out;
		}
		Map<String, Badge> teams = teams(view);
		for (ClanData.Team team : event.teams())
		{
			Badge badge = teams.get(team.getId());
			if (badge == null)
			{
				continue;
			}
			for (String member : ClanView.orEmpty(team.getMembers()))
			{
				out.put(key(member), badge);
			}
		}
		return out;
	}

	/** How a name is looked up: tags removed, odd spaces normalised, lowercase. */
	static String key(String name)
	{
		return Text.toJagexName(Text.removeTags(name)).toLowerCase(Locale.UK);
	}

	/**
	 * The chat name with the team badge in front, in place of any clan rank icon. Account icons the game itself draws
	 * (ironman and so on) are kept.
	 */
	static String withBadge(String name, int iconIndex)
	{
		Matcher m = IMG.matcher(name);
		StringBuffer kept = new StringBuffer();
		while (m.find())
		{
			m.appendReplacement(kept, GAME_ICONS.contains(Integer.parseInt(m.group(1))) ? "$0" : "");
		}
		m.appendTail(kept);
		return "<img=" + iconIndex + ">" + kept;
	}

	/** A small coloured box with the team's tag, the size of a chat icon. */
	static BufferedImage image(String tag, Color colour)
	{
		int width = tag.length() > 1 ? 15 : 11;
		BufferedImage image = new BufferedImage(width, 11, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(colour);
		g.fillRoundRect(0, 0, width, 11, 3, 3);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		int luminance = (colour.getRed() * 299 + colour.getGreen() * 587 + colour.getBlue() * 114) / 1000;
		g.setColor(luminance > 140 ? Color.BLACK : Color.WHITE);
		g.drawString(tag, (width - metrics.stringWidth(tag) + 1) / 2, 9);
		g.dispose();
		return image;
	}

	static Color colour(String hex, Color fallback)
	{
		try
		{
			return hex != null ? Color.decode(hex) : fallback;
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	/** The palette colour for a team without one of its own, for the panel's badges. */
	static Color fallback(int slot)
	{
		return PALETTE[Math.floorMod(slot, PALETTE.length)];
	}
}
