package com.asgardians;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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
		description = "The group finder in the Groups tab. On-screen alerts for new groups are off until you turn on \"New groups on screen\".",
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
		name = "World map",
		description = "Clanmates who share their location, on your world map",
		position = 5
	)
	String mapSection = "map";

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
		keyName = "shareActivity",
		name = "Share my activity",
		description = "Show clanmates on the Members page what you're doing: a raid, a boss, a skill or the Grand Exchange. Worked out from your own game only. While this is off, they see you as Online with your world.",
		position = 0,
		section = sharingSection
	)
	default boolean shareActivity()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareLocation",
		name = "Share my location on the map",
		description = "Show clanmates where you are on their world map. Only your own position is sent, only while \"Share my data with Asgardians\" is on, and never inside instances (raids, boss rooms). Turn this off and you don't appear on anyone's map.",
		section = sharingSection,
		position = 0
	)
	default boolean shareLocation()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showClanMap",
		name = "Show clanmates on the map",
		description = "Show clanmates who share their location on your world map. This only changes what you see.",
		section = mapSection,
		position = 0
	)
	default boolean showClanMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mapOnlyMyWorld",
		name = "Only clanmates on my world",
		description = "Only show clanmates on the world you're on, so a busy clan doesn't cover the map. Turn off to see everyone who shares their location.",
		section = mapSection,
		position = 1
	)
	default boolean mapOnlyMyWorld()
	{
		return true;
	}

	@ConfigItem(
		keyName = "deathMessage",
		name = "My death message",
		description = "Optional words added to your death post on the clan Discord, e.g. \"Sit. {killer} got me again\". {killer} becomes what killed you, {place} where, and {lost} what it cost. Leave empty for none.",
		section = sharingSection,
		position = 0
	)
	default String deathMessage()
	{
		return "";
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
		keyName = "codewordDetailed",
		name = "Detailed codeword box",
		description = "Off: a small box with just the codeword and the time. On: also the clan name, the event's name and \"Codeword:\"",
		section = eventsSection,
		position = 0
	)
	default boolean codewordDetailed()
	{
		return false;
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
		keyName = "tileSound",
		name = "Ding when my team completes a tile",
		description = "Play the boss unique-drop ding when your team completes a bingo tile. Other teams' tiles don't ding.",
		section = eventsSection,
		position = 1
	)
	default boolean tileSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tileSoundOnlyMine",
		name = "Only ding for my own tiles",
		description = "Only ding when you finish a tile yourself (you sent the item that completed it), not when a teammate does",
		section = eventsSection,
		position = 1
	)
	default boolean tileSoundOnlyMine()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "tileVolume",
		name = "Tile ding volume",
		description = "How loud the ding is when a bingo tile is completed. 0% is silent.",
		section = eventsSection,
		position = 1
	)
	default int tileVolume()
	{
		return 75;
	}

	@ConfigItem(
		keyName = "tileFireworks",
		name = "Fireworks for my team's tiles",
		description = "Set off level-up fireworks on your character when your team completes a bingo tile. Only you see them.",
		section = eventsSection,
		position = 1
	)
	default boolean tileFireworks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "motdAtLogin",
		name = "Message of the day at login",
		description = "Show the clan's message of the day in chat each time you log in, and when staff change it",
		section = eventsSection,
		position = 3
	)
	default boolean motdAtLogin()
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
		keyName = "chatCommands",
		name = "!asg chat commands",
		description = "Answer !asg commands typed in any chat, like the Chat Commands plugin: !asg groups, events, bingo, sotw, botw, news, drops, cabbage, pb [boss], credit, rank, ehb, ehp, profile, eggs (!asg help lists them). Only players with this plugin see the answers. Answers about whoever typed it are looked up on the clan website, for clan members only.",
		section = eventsSection,
		position = 4
	)
	default boolean chatCommands()
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
		description = "Off by default. Turn this on to also see new groups on screen for a few seconds. Pick which activities with the screen icons under \"Group alerts\" in the Groups tab (all are on until you change them).",
		section = groupsSection,
		position = 1
	)
	default boolean groupOverlay()
	{
		return false;
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

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "eggVolume",
		name = "Egg sound volume",
		description = "How loud the sound is when you're egged. 0% is silent.",
		section = eggsSection,
		position = 2
	)
	default int eggVolume()
	{
		return 75;
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
