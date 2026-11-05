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
}
