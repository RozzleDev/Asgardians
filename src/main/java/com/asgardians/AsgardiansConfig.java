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

	// "Share my data with Asgardians" sits above the sections, as the switch for everything. Moving a setting into a
	// section keeps its keyName, so members keep what they chose.

	@ConfigSection(
		name = "Sharing",
		description = "What's sent to the clan website besides drops, collection log and PBs",
		position = 1
	)
	String sharingSection = "sharing";

	@ConfigSection(
		name = "Events and news",
		description = "Clan events, team badges and staff announcements",
		position = 2
	)
	String eventsSection = "events";

	@ConfigSection(
		name = "Groups",
		description = "The group finder in the Groups tab",
		position = 3
	)
	String groupsSection = "groups";

	@ConfigSection(
		name = "Handeggs",
		description = "Easter handegg throws between clan members",
		position = 4
	)
	String eggsSection = "eggs";

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
		keyName = "shareDeaths",
		name = "Share my deaths",
		description = "Send your deaths to the clan website. While this is off, new deaths aren't recorded and won't count on the leaderboard.",
		section = sharingSection,
		position = 0
	)
	default boolean shareDeaths()
	{
		return true;
	}

	@ConfigItem(
		keyName = "uploadScreenshots",
		name = "Upload screenshots",
		description = "Attach a screenshot to valuable drops, pets and new collection log slots",
		section = sharingSection,
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
		section = sharingSection,
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
		section = eventsSection,
		position = 0
	)
	default boolean showCodeword()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eventMessages",
		name = "Event messages in chat",
		description = "Chat messages when an event starts, 15 minutes before events you're signed up for, and when any team in your event submits, gets an item accepted or completes a tile",
		section = eventsSection,
		position = 1
	)
	default boolean eventMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "newsMessages",
		name = "Announcements in chat",
		description = "A chat message when clan staff post a new announcement, and at login for any you haven't read",
		section = eventsSection,
		position = 3
	)
	default boolean newsMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "teamBadges",
		name = "Team badges in clan chat",
		description = "While a clan event with teams is live, show each player's team in place of their clan rank icon in clan chat. Only players in the event get a badge.",
		section = eventsSection,
		position = 2
	)
	default boolean teamBadges()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groupAlerts",
		name = "New groups in chat",
		description = "A chat message when a clan member starts a group in the Groups tab, so you can join. Pick which activities with the bells under \"Group alerts\" in the Groups tab. Turning this off mutes them all, except any clan staff send to everyone. Messages about your own group always show.",
		section = groupsSection,
		position = 0
	)
	default boolean groupAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groupOverlay",
		name = "New groups on screen",
		description = "Also show new groups on screen for a few seconds. Pick which activities with the screen icons under \"Group alerts\" in the Groups tab (all are on until you change them). Turn this off to never show them on screen.",
		section = groupsSection,
		position = 1
	)
	default boolean groupOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groupParty",
		name = "Join a group's RuneLite party",
		description = "When you start or join a group, put you in its RuneLite party (the one the Party plugin shows), and take you out when you leave",
		section = groupsSection,
		position = 2
	)
	default boolean groupParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eggMessages",
		name = "Egg messages in chat",
		description = "A chat message when you egg a clan member or one eggs you, and when one clan member eggs another",
		section = eggsSection,
		position = 0
	)
	default boolean eggMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eggSound",
		name = "Sound when egged",
		description = "Play the Ice Barrage freeze sound when a clan member hits you with a handegg",
		section = eggsSection,
		position = 1
	)
	default boolean eggSound()
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
