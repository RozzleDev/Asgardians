package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.Test;

public class StatsTabsTest
{
	private static final Gson GSON = new Gson();
	static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

	static final StatsTabs.Actions ACTIONS = new StatsTabs.Actions()
	{
		@Override
		public void refresh()
		{
		}

		@Override
		public void opened()
		{
		}

		@Override
		public void icon(JLabel label, int itemId, int quantity)
		{
		}

		@Override
		public void bossIcon(JLabel label, String boss)
		{
		}
	};

	@Test
	public void pbBossesMatchHiscoreNames()
	{
		List<String> names = List.of("Theatre of Blood", "Theatre of Blood: Hard Mode", "Tombs of Amascut",
			"Tombs of Amascut: Expert Mode", "TzKal-Zuk", "Kree'Arra", "Nightmare", "Phosani's Nightmare",
			"The Corrupted Gauntlet", "The Gauntlet", "Lunar Chests", "Vorkath");
		assertEquals("Theatre of Blood", StatsTabs.hiscoreName("theatre of blood", names));
		assertEquals("Theatre of Blood: Hard Mode", StatsTabs.hiscoreName("theatre of blood hard mode", names));
		assertEquals("Tombs of Amascut", StatsTabs.hiscoreName("tombs of amascut entry mode", names));
		assertEquals("Tombs of Amascut: Expert Mode", StatsTabs.hiscoreName("Tombs of Amascut: Expert Mode", names));
		assertEquals("TzKal-Zuk", StatsTabs.hiscoreName("tzkal-zuk", names));
		assertEquals("Kree'Arra", StatsTabs.hiscoreName("kree'arra", names));
		assertEquals("Nightmare", StatsTabs.hiscoreName("the nightmare", names));
		assertEquals("Phosani's Nightmare", StatsTabs.hiscoreName("phosani's nightmare", names));
		assertEquals("The Corrupted Gauntlet", StatsTabs.hiscoreName("corrupted gauntlet", names));
		assertEquals("The Gauntlet", StatsTabs.hiscoreName("gauntlet", names));
		assertEquals("Lunar Chests", StatsTabs.hiscoreName("lunar chest", names));
		assertNull(StatsTabs.hiscoreName("hallowed sepulchre floor 5", names));
		assertNull(StatsTabs.hiscoreName(null, names));
	}

	@Test
	public void commonPbsHaveARealHiscoreIcon()
	{
		List<String> bosses = new ArrayList<>();
		for (net.runelite.client.hiscore.HiscoreSkill skill : net.runelite.client.hiscore.HiscoreSkill.values())
		{
			if (skill.getType() == net.runelite.client.hiscore.HiscoreSkillType.BOSS)
			{
				bosses.add(skill.getName());
			}
		}
		// Names as RuneLite's Chat Commands plugin stores PBs.
		for (String key : List.of("theatre of blood", "theatre of blood hard mode", "chambers of xeric",
			"chambers of xeric challenge mode", "tombs of amascut", "tombs of amascut expert mode", "tztok-jad", "tzkal-zuk",
			"vorkath", "zulrah", "corrupted gauntlet", "gauntlet", "nex", "the nightmare", "phosani's nightmare",
			"grotesque guardians", "alchemical hydra", "duke sucellus", "the leviathan", "vardorvis", "the whisperer",
			"phantom muspah", "sol heredit", "araxxor", "hespori", "fragment of seren", "tombs of amascut entry mode"))
		{
			if (!key.equals("fragment of seren"))
			{
				assertTrue(key, StatsTabs.hiscoreName(key, bosses) != null);
			}
		}
		assertEquals("Theatre of Blood: Hard Mode", StatsTabs.hiscoreName("theatre of blood hard mode", bosses));
		assertEquals("Vorkath", StatsTabs.hiscoreName("vorkath", bosses));
	}

	private static final StatsTabs.UiActions UI = new StatsTabs.UiActions()
	{
		@Override
		public void page(String page)
		{
		}

		@Override
		public void minimum(String minimum)
		{
		}
	};

	static ClanData.StatsFeed feed()
	{
		StringBuilder many = new StringBuilder();
		for (int i = 0; i < 30; i++)
		{
			many.append(i > 0 ? "," : "").append("{\"id\":\"d").append(i).append("\",\"rsn\":\"Clo\",\"item\":\"Bones\",\"itemId\":526,\"value\":120}");
		}
		return GSON.fromJson("{"
			+ "\"drops\":[" + many + ",null],"
			+ "\"drops10m\":[{\"id\":\"a\",\"rsn\":\"Rozzle\",\"item\":\"Twisted bow\",\"itemId\":20997,\"quantity\":1,\"value\":1450000000,"
			+ "\"source\":\"Chambers of Xeric\",\"kc\":212,\"at\":\"2026-11-06T11:55:00Z\"}],"
			+ "\"drops100m\":[],"
			+ "\"clogs\":[{\"rsn\":\"Clo\",\"item\":\"Pet snakeling\",\"itemId\":12921,\"source\":\"Zulrah\",\"at\":\"2026-11-06T09:00:00Z\"}],"
			+ "\"drops1m\":[{\"rsn\":\"Clo\",\"item\":\"Abyssal whip\",\"itemId\":4151,\"value\":1500000}],"
			+ "\"pbs\":[{\"rsn\":\"Rozzle\",\"boss\":\"Theatre of Blood\",\"teamSize\":\"4\",\"seconds\":1023.6,\"kc\":40,\"at\":\"2026-11-05T12:00:00Z\"},"
			+ "{\"rsn\":\"rozzle\",\"boss\":\"theatre of blood\",\"teamSize\":\"4\",\"seconds\":1100,\"at\":\"2026-11-04T12:00:00Z\"},"
			+ "{\"rsn\":\"Rozzle\",\"boss\":\"Theatre of Blood\",\"teamSize\":\"5\",\"seconds\":1200,\"at\":\"2026-11-03T12:00:00Z\"},"
			+ "{\"boss\":\"Vorkath\",\"teamSize\":\"1\",\"seconds\":64}],"
			+ "\"pets\":[{\"rsn\":\"<html><b>x\",\"item\":\"Vorki\",\"duplicate\":true}]"
			+ "}", ClanData.StatsFeed.class);
	}

	static StatsTabs.Input input(ClanData.StatsFeed feed)
	{
		return new StatsTabs.Input(feed, false, null, null, NOW, NOW, ZoneId.of("Europe/London"));
	}

	@Test
	public void oddSavedChoicesFallBack()
	{
		assertEquals(StatsTabs.DROPS, StatsTabs.page(null));
		assertEquals(StatsTabs.DROPS, StatsTabs.page("trades"));
		assertEquals(StatsTabs.PETS, StatsTabs.page("pets"));
		assertEquals(StatsTabs.ALL, StatsTabs.minimum(null));
		assertEquals(StatsTabs.ALL, StatsTabs.minimum("1b"));
		assertEquals(StatsTabs.HUNDRED_M, StatsTabs.minimum("100m"));
	}

	@Test
	public void listsPickTheRightOneAndStopAt25()
	{
		ClanData.StatsFeed feed = feed();
		assertEquals(25, StatsTabs.list(feed, StatsTabs.DROPS, StatsTabs.ALL).size());
		assertEquals("Twisted bow", StatsTabs.list(feed, StatsTabs.DROPS, StatsTabs.TEN_M).get(0).getItem());
		assertEquals(0, StatsTabs.list(feed, StatsTabs.DROPS, StatsTabs.HUNDRED_M).size());
		assertEquals("Abyssal whip", StatsTabs.list(feed, StatsTabs.DROPS, StatsTabs.ONE_M).get(0).getItem());
		// Rozzle's slower ToB 4-man time is dropped; the 5-man is a different PB, so it stays.
		List<ClanData.StatEntry> pbs = StatsTabs.list(feed, StatsTabs.PBS, null);
		assertEquals(3, pbs.size());
		assertEquals(Double.valueOf(1023.6), pbs.get(0).getSeconds());
		assertEquals("5", pbs.get(1).getTeamSize());
		assertEquals("Vorkath", pbs.get(2).getBoss());
		assertNull(StatsTabs.list(null, StatsTabs.DROPS, null));
		assertNull(StatsTabs.list(GSON.fromJson("{\"drops\":[]}", ClanData.StatsFeed.class), StatsTabs.PETS, null));
		assertTrue(!new ClanData.StatsFeed().known());
	}

	@Test
	public void rowText()
	{
		ClanData.StatsFeed feed = feed();
		ClanData.StatEntry tbow = feed.getDrops10m().get(0);
		assertEquals("Twisted bow", StatsTabs.name(tbow, StatsTabs.DROPS));
		assertEquals("1.45B", StatsTabs.right(tbow, StatsTabs.DROPS));
		assertEquals("Rozzle · Chambers of Xeric · 212 kc", StatsTabs.detail(tbow, StatsTabs.DROPS));

		ClanData.StatEntry tob = feed.getPbs().get(0);
		assertEquals("Theatre of Blood (4 players)", StatsTabs.name(tob, StatsTabs.PBS));
		assertEquals("17:03.60", StatsTabs.right(tob, StatsTabs.PBS));
		assertEquals("Rozzle · 40 kc", StatsTabs.detail(tob, StatsTabs.PBS));

		ClanData.StatEntry vork = feed.getPbs().get(3);
		assertEquals("Vorkath (solo)", StatsTabs.name(vork, StatsTabs.PBS));
		assertEquals("1:04", StatsTabs.right(vork, StatsTabs.PBS));
		assertEquals("Someone", StatsTabs.detail(vork, StatsTabs.PBS));

		assertEquals("Duplicate", StatsTabs.right(feed.getPets().get(0), StatsTabs.PETS));
		ClanData.StatEntry stack = GSON.fromJson("{\"item\":\"Coins\",\"quantity\":12500}", ClanData.StatEntry.class);
		assertEquals("12,500 x Coins", StatsTabs.name(stack, StatsTabs.DROPS));
		assertEquals("", StatsTabs.right(stack, StatsTabs.DROPS));
		assertEquals("1:02:03", StatsTabs.time(3723));
	}

	@Test
	public void drawsEveryPageAndState()
	{
		List<ClanData.StatsFeed> feeds = new ArrayList<>();
		feeds.add(feed());
		feeds.add(null);
		feeds.add(new ClanData.StatsFeed());
		for (ClanData.StatsFeed feed : feeds)
		{
			for (String page : StatsTabs.PAGES)
			{
				for (String min : StatsTabs.MINIMUMS)
				{
					JPanel column = PanelParts.column();
					StatsTabs.draw(column, input(feed), new StatsTabs.Ui(page, min), UI, ACTIONS);
					assertTrue(column.getComponentCount() > 0);
				}
			}
		}
		JPanel column = PanelParts.column();
		StatsTabs.draw(column, new StatsTabs.Input(null, true, null, null, NOW, NOW, ZoneId.of("UTC")), new StatsTabs.Ui(null, null), UI, ACTIONS);
		assertTrue(text(column).contains("aren't on the website yet"));
		column = PanelParts.column();
		StatsTabs.draw(column, new StatsTabs.Input(null, false, "Log in to see clan stats.", null, null, NOW, ZoneId.of("UTC")),
			new StatsTabs.Ui(null, null), UI, ACTIONS);
		assertTrue(text(column).contains("Log in to see clan stats."));
	}

	@Test
	public void htmlInANameIsShownAsText()
	{
		JPanel column = PanelParts.column();
		StatsTabs.draw(column, input(feed()), new StatsTabs.Ui(StatsTabs.PETS, null), UI, ACTIONS);
		for (String t : labels(column))
		{
			assertTrue(t, !t.regionMatches(true, 0, "<html><b>", 0, 9));
		}
	}

	private static String text(Container c)
	{
		return String.join("\n", labels(c));
	}

	private static List<String> labels(Container c)
	{
		List<String> all = new ArrayList<>();
		for (Component child : c.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText() != null)
			{
				all.add(((JLabel) child).getText());
			}
			if (child instanceof Container)
			{
				all.addAll(labels((Container) child));
			}
		}
		return all;
	}
}
