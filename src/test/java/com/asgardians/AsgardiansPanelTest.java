package com.asgardians;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class AsgardiansPanelTest
{
	private static AsgardiansPanel panel(Map<String, String> saved)
	{
		return new AsgardiansPanel(new AsgardiansPanel.Settings()
		{
			@Override
			public String get(String key)
			{
				return saved.get(key);
			}

			@Override
			public void set(String key, String value)
			{
				saved.put(key, value);
			}
		}, () -> { }, () -> { }, () -> { }, ClanTabsTest.ACTIONS, GroupsTest.actions(), StatsTabsTest.ACTIONS);
	}

	@Test
	public void opensOnAFreshInstall()
	{
		// Nothing saved at all: what every new member has. This used to throw and stop the plugin starting.
		panel(new HashMap<>());
	}

	@Test
	public void opensWithOddSavedChoices()
	{
		Map<String, String> saved = new HashMap<>();
		saved.put("leaderboardPeriod", "fortnight");
		saved.put("statsPage", "trades");
		saved.put("statsMinimum", "1b");
		saved.put("foldedLeaderboards", "\n\nDeaths\n");
		AsgardiansPanel panel = panel(saved);
		panel.updateClan(ClanTabsTest.oddInput());
		panel.updateStats(StatsTabsTest.input(StatsTabsTest.feed()));
	}
}
