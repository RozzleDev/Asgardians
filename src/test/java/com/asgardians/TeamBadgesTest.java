package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TeamBadgesTest
{
	@Test
	public void badgeReplacesTheRankIconButKeepsAccountIcons()
	{
		assertEquals("<img=90>Rozzle", TeamBadges.withBadge("<img=64>Rozzle", 90));
		assertEquals("<img=90><img=2>Iron Rozzle", TeamBadges.withBadge("<img=2>Iron Rozzle", 90));
		assertEquals("<img=90>Rozzle", TeamBadges.withBadge("Rozzle", 90));
	}

	@Test
	public void onlyPlayersInTheLiveEventGetABadge()
	{
		ClanData.Event bingo = ClanViewTest.event("bingo", "live", "2026-10-20T00:00:00Z", true);
		ClanData.Team one = ClanViewTest.team("t1", "Team 1", "Rozzle", "Str4tty");
		ClanData.Team two = ClanViewTest.team("t2", "Team 2", "Dolorosa");
		two.setColour("#3f9ce8");
		ClanView view = ClanViewTest.view(List.of(new ClanView.EventView(bingo, null, List.of(one, two), null)), List.of());
		Map<String, TeamBadges.Badge> players = TeamBadges.players(view);
		assertEquals("1", players.get("rozzle").getTag());
		assertEquals("2", players.get(TeamBadges.key("Dolorosa")).getTag());
		assertEquals(1, players.get("dolorosa").getSlot());
		assertEquals(0x3f9ce8, players.get("dolorosa").getColour().getRGB() & 0xFFFFFF);
		assertNull(players.get("someone else"));
		assertEquals(Map.of(), TeamBadges.players(ClanViewTest.view(List.of(), List.of())));
	}

	@Test
	public void santaHatsGoToSignedUpNamesHoweverTheyAreWritten()
	{
		java.util.Set<String> hats = TeamBadges.hatKeys(List.of("Iron Orish", "Clo BTW"));
		assertEquals(true, hats.contains(TeamBadges.key("iron orish")));
		assertEquals(true, hats.contains(TeamBadges.key("<col=ff0000>Clo BTW</col>")));
		assertEquals(false, hats.contains(TeamBadges.key("Someone else")));
		assertEquals(0, TeamBadges.hatKeys(null).size());
	}

	@Test
	public void theHatIsTrimmedAndFitsAChatIcon()
	{
		// A 36x32 item picture with the hat in a 20x10 patch: trimmed, then scaled to fit 13 wide.
		java.awt.image.BufferedImage item = new java.awt.image.BufferedImage(36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = item.createGraphics();
		g.setColor(java.awt.Color.RED);
		g.fillRect(8, 11, 20, 10);
		g.dispose();
		java.awt.image.BufferedImage icon = TeamBadges.chatIcon(item);
		assertEquals(13, icon.getWidth());
		assertEquals(13, icon.getHeight());
		assertEquals(0xFF, icon.getRGB(6, 6) >>> 24);
		assertEquals(0, icon.getRGB(6, 0) >>> 24);
	}
}
