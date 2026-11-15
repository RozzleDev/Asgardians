package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CodewordOverlayTest
{
	private static ClanData.Team team(String id, String name, Double points)
	{
		ClanData.Team t = new ClanData.Team();
		t.setId(id);
		t.setName(name);
		t.setPoints(points);
		return t;
	}

	@Test
	public void listsTeamsBestFirstWithTheMembersOwnTeamHighlighted()
	{
		ClanData.Event e = new ClanData.Event();
		e.setTeamId("b");
		List<ClanData.Team> teams = List.of(team("a", "Thor's Hammer", 38.0), team("b", "Loki's Tricks", 42.5), team("c", "Odin's Eye", 12.0));
		List<CodewordOverlay.Score> s = CodewordOverlay.scores(new ClanView.EventView(e, null, teams, null));
		assertEquals(3, s.size());
		assertEquals("1. Loki's Tricks", s.get(0).getName());
		assertEquals("42.5", s.get(0).getPoints());
		assertTrue(s.get(0).isMine());
		assertEquals("2. Thor's Hammer", s.get(1).getName());
	}

	@Test
	public void ownTeamStaysListedWhenThereAreManyTeams()
	{
		ClanData.Event e = new ClanData.Event();
		e.setTeamId("t7");
		List<ClanData.Team> teams = new ArrayList<>();
		for (int i = 1; i <= 7; i++)
		{
			teams.add(team("t" + i, "Team " + i, 100.0 - i));
		}
		List<CodewordOverlay.Score> s = CodewordOverlay.scores(new ClanView.EventView(e, null, teams, null));
		assertEquals(CodewordOverlay.MAX_TEAMS, s.size());
		assertEquals("4. Team 4", s.get(3).getName());
		assertEquals("7. Team 7", s.get(4).getName());
		assertTrue(s.get(4).isMine());
	}

	@Test
	public void noPointsMeansNoScores()
	{
		ClanData.Event e = new ClanData.Event();
		assertTrue(CodewordOverlay.scores(new ClanView.EventView(e, null, List.of(team("a", "A", null)), null)).isEmpty());
		assertTrue(CodewordOverlay.scores(new ClanView.EventView(e, null, List.of(), null)).isEmpty());
	}
}
