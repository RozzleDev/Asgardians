package com.asgardians;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(AsgardiansConfig.GROUP)
public interface AsgardiansConfig extends Config
{
	String GROUP = "asgardiansclan";
	String DEFAULT_API_ADDRESS = "https://asgardiansclan.com";

	@ConfigSection(
		name = "Advanced",
		description = "Settings for testing against another copy of the website",
		position = 10,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "shareData",
		name = "Share my data with Asgardians",
		description = "Send your drops, collection log, deaths and personal bests to the clan website. Nothing is sent while this is off.",
		position = 0
	)
	default boolean shareData()
	{
		return false;
	}

	@ConfigItem(
		keyName = "uploadScreenshots",
		name = "Upload screenshots",
		description = "Attach a screenshot to valuable drops, pets and new collection log slots",
		position = 1
	)
	default boolean uploadScreenshots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "saveScreenshots",
		name = "Save screenshots locally",
		description = "Also save those screenshots to your RuneLite screenshots folder, under Asgardians",
		position = 2
	)
	default boolean saveScreenshots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCodeword",
		name = "Show event codeword",
		description = "While a clan event you're in is live, show its codeword and the time on screen so it appears in screenshots",
		position = 3
	)
	default boolean showCodeword()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eventMessages",
		name = "Event messages in chat",
		description = "Chat messages when an event starts, 15 minutes before events you're signed up for, and when your team's tiles are reviewed",
		position = 4
	)
	default boolean eventMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "newsMessages",
		name = "Announcements in chat",
		description = "A chat message when clan staff post a new announcement, and at login for any you haven't read",
		position = 5
	)
	default boolean newsMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "apiAddress",
		name = "API address",
		description = "The website the plugin talks to. Leave as " + DEFAULT_API_ADDRESS + " unless clan staff ask you to test.",
		section = advancedSection,
		position = 0
	)
	default String apiAddress()
	{
		return DEFAULT_API_ADDRESS;
	}
}
