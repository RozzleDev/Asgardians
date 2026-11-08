package com.asgardians;

import com.google.gson.Gson;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Opens the real side panel in a window with made-up clan data, so its design can be checked without RuneLite or a
 * login. Run C:\claude\Preview.bat. With "--png <folder>" it saves a picture of each page instead.
 *
 * Item and boss pictures come from C:\claude\mockup\icons when that folder exists (RuneLite draws them in game).
 */
public class PanelPreview
{
	private static final Gson GSON = new Gson();
	private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
	private static final ZoneId ZONE = ZoneId.of("Europe/London");
	private static final File ICONS = new File("C:\\claude\\mockup\\icons");
	private static final Map<Integer, String> ITEMS = new HashMap<>();
	private static final Map<String, String> BOSSES = new HashMap<>();

	static
	{
		ITEMS.put(20997, "Twisted_bow");
		ITEMS.put(27277, "Tumekens_shadow");
		ITEMS.put(21907, "Vorkaths_head");
		ITEMS.put(13265, "Abyssal_dagger");
		ITEMS.put(13576, "Dragon_warhammer");
		ITEMS.put(20851, "Olmlet");
		ITEMS.put(22473, "Lil_zik");
		ITEMS.put(21992, "Vorki");
		ITEMS.put(11832, "Bandos_chestplate");
		ITEMS.put(19677, "Ancient_shard");
		ITEMS.put(12934, "Zulrahs_scales");
		ITEMS.put(995, "Coins");
		ITEMS.put(4151, "Abyssal_whip");
		ITEMS.put(536, "Dragon_bones");
		ITEMS.put(12073, "Clue_scroll_elite");
		ITEMS.put(21439, "Champions_cape");
		ITEMS.put(27251, "Elidinis_ward");
		ITEMS.put(26348, "Nex_icon");
		ITEMS.put(25602, "Tempoross_icon");
		ITEMS.put(11864, "Slayer_icon");
		ITEMS.put(1275, "Mining_icon");
		ITEMS.put(301, "Fishing_icon");
		ITEMS.put(11850, "Agility_icon");
		ITEMS.put(12921, "Zulrah_icon");
		BOSSES.put("theatre of blood", "Lil_zik");
		BOSSES.put("vorkath", "Vorkath_icon");
		BOSSES.put("nex", "Nex_icon");
		BOSSES.put("zulrah", "Zulrah_icon");
		BOSSES.put("tempoross", "Tempoross_icon");
	}

	public static void main(String[] args) throws Exception
	{
		PanelParts.setItemIcons((id, done) ->
		{
			BufferedImage img = image(ITEMS.get(id));
			if (img != null)
			{
				done.accept(img);
			}
		});
		if (args.length >= 2 && args[0].equals("--png"))
		{
			File dir = new File(args[1]);
			dir.mkdirs();
			SwingUtilities.invokeAndWait(() -> pictures(dir));
			System.exit(0);
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			AsgardiansPanel panel = panel();
			JFrame frame = new JFrame("Asgardians panel preview (sample data)");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.getContentPane().setBackground(PanelParts.BACKGROUND);
			frame.add(panel);
			panel.setPreferredSize(new Dimension(242, 760));
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
			panel.onActivate();
		});
	}

	private static void pictures(File dir)
	{
		AsgardiansPanel panel = panel();
		JFrame frame = new JFrame();
		frame.add(panel);
		panel.setPreferredSize(new Dimension(242, 900));
		frame.pack();
		for (String page : new String[]{AsgardiansPanel.HOME, AsgardiansPanel.EVENTS, AsgardiansPanel.BINGO, AsgardiansPanel.GROUPS,
			AsgardiansPanel.MEMBERS, AsgardiansPanel.DROPS, AsgardiansPanel.LEADERBOARD, AsgardiansPanel.PROFILE, AsgardiansPanel.MEMBER,
			AsgardiansPanel.NEWS, AsgardiansPanel.PBS, AsgardiansPanel.SETTINGS})
		{
			if (page.equals(AsgardiansPanel.LEADERBOARD))
			{
				panel.show(page);
				save(frame, panel, new File(dir, "panel-leaderboard.png"));
				panel.showBoard("Boss kills", null, null, null);
				save(frame, panel, new File(dir, "panel-kc-all.png"));
				panel.showBoard("Boss kills", BossGroups.BOSSES, "Slayer", "Alchemical Hydra");
				save(frame, panel, new File(dir, "panel-kc-hydra.png"));
				panel.showSkill("XP", null);
				save(frame, panel, new File(dir, "panel-xp-all.png"));
				panel.showSkill("XP", "Slayer");
				page = "xp-slayer";
			}
			else if (page.equals(AsgardiansPanel.BINGO))
			{
				panel.show(page);
				save(frame, panel, new File(dir, "panel-bingo.png"));
				panel.showTile("t1");
				page = "bingo-tile";
			}
			else if (page.equals(AsgardiansPanel.EVENTS))
			{
				panel.show(page);
				save(frame, panel, new File(dir, "panel-events.png"));
				panel.showEvent("botw");
				page = "event-botw";
			}
			else if (page.equals(AsgardiansPanel.PBS))
			{
				panel.showPbs(null, null, null);
				save(frame, panel, new File(dir, "panel-pbs.png"));
				panel.showPbs(BossGroups.BOSSES, "Slayer", "Alchemical Hydra");
				save(frame, panel, new File(dir, "panel-pbs-slayer.png"));
				panel.showPbs(BossGroups.SKILLING, null, null);
				page = "pbs-skilling";
			}
			else if (page.equals(AsgardiansPanel.MEMBER))
			{
				panel.openProfile("Clo");
				panel.updateProfile("Clo", cloProfile(), false);
			}
			else
			{
				panel.show(page);
			}
			save(frame, panel, new File(dir, "panel-" + page + ".png"));
		}
		frame.dispose();
	}

	static void save(JFrame frame, AsgardiansPanel panel, File file)
	{
		// What Swing does before drawing a shown window: lay out everything that changed.
		frame.validate();
		layout(panel);
		frame.validate();
		BufferedImage img = new BufferedImage(panel.getWidth() * 2, panel.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.scale(2, 2);
		g.setColor(PanelParts.BACKGROUND);
		g.fillRect(0, 0, panel.getWidth(), panel.getHeight());
		panel.printAll(g);
		g.dispose();
		try
		{
			ImageIO.write(img, "png", file);
		}
		catch (java.io.IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static void layout(java.awt.Container c)
	{
		c.doLayout();
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof java.awt.Container)
			{
				layout((java.awt.Container) child);
			}
		}
	}

	static AsgardiansPanel panel()
	{
		Map<String, String> saved = new HashMap<>();
		AsgardiansPanel panel = new AsgardiansPanel(new AsgardiansPanel.Settings()
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
		}, () -> { }, () -> { }, () -> { }, ClanTabsTest.ACTIONS, GroupsTest.actions(), STATS);
		panel.setProfileLoader(rsn -> SwingUtilities.invokeLater(() -> panel.updateProfile(rsn, cloProfile(), false)));
		panel.loadTilePictures(new AsgardiansPanel.GamePictures()
		{
			private final File osrs = new File("C:/claude/mockup/osrs");

			@Override
			public void item(int itemId, int quantity, java.util.function.Consumer<BufferedImage> done)
			{
				String name = itemId == 995 ? "coins" : itemId == AsgardiansPanel.NEWSPAPER ? "newspaper"
					: itemId == AsgardiansPanel.GOLD_SPEEDRUN_TROPHY ? "t1" : null;
				load(name, done);
			}

			@Override
			public void sprite(int spriteId, java.util.function.Consumer<BufferedImage> done)
			{
				String name = spriteId == AsgardiansPanel.SPRITE_QUESTS ? "qp" : spriteId == AsgardiansPanel.SPRITE_CLAN ? "clan"
					: spriteId == AsgardiansPanel.SPRITE_STATS ? "stats" : null;
				load(name, done);
			}

			private void load(String name, java.util.function.Consumer<BufferedImage> done)
			{
				try
				{
					if (name != null && new File(osrs, name + ".png").exists())
					{
						done.accept(ImageIO.read(new File(osrs, name + ".png")));
					}
				}
				catch (java.io.IOException ignored)
				{
				}
			}
		});
		PanelParts.setSkillIcons((label, skill) ->
		{
			BufferedImage img = image(skill + "_icon");
			if (img != null)
			{
				label.setIcon(new ImageIcon(img));
			}
		});
		PanelParts.setEventIcons((event, done) ->
		{
			String group = ClanView.group(event);
			String name = group.equals("bingo") ? "../osrs/gm_Grid_Master_icon" : group.equals("botw") ? "Vorkath_icon"
				: group.equals("sotw") ? "Slayer_icon" : "../osrs/partyhat";
			BufferedImage img = image(name);
			if (img != null)
			{
				done.accept(img);
			}
		});
		panel.update(state());
		panel.updateClan(clan());
		panel.updateStats(new StatsTabs.Input(feed(), false, null, null, NOW, NOW, ZONE));
		panel.updateGroups(new GroupTabs.Input(groups(), activities(), false, null, "Rozzle", Map.of("rozzle", 416, "clo", 416), false,
			false, NOW, ZONE, null));
		panel.updateMembers(members());
		return panel;
	}

	private static final StatsTabs.Actions STATS = new StatsTabs.Actions()
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
			picture(label, ITEMS.get(itemId), 32);
		}

		@Override
		public void bossIcon(JLabel label, String boss)
		{
			picture(label, BOSSES.get(boss.toLowerCase()), 20);
		}
	};

	private static BufferedImage image(String name)
	{
		if (name == null)
		{
			return null;
		}
		try
		{
			return ImageIO.read(new File(ICONS, name + ".png"));
		}
		catch (java.io.IOException e)
		{
			return null;
		}
	}

	private static void picture(JLabel label, String name, int size)
	{
		BufferedImage img = image(name);
		if (img != null)
		{
			double s = Math.min(size / (double) img.getWidth(), size / (double) img.getHeight());
			label.setIcon(new ImageIcon(img.getScaledInstance((int) (img.getWidth() * s), (int) (img.getHeight() * s),
				java.awt.Image.SCALE_FAST)));
		}
	}

	private static AsgardiansPanel.State state()
	{
		ClanData.Me me = GSON.fromJson("{\"rsn\":\"Rozzle\",\"rank\":\"thor\",\"daysInClan\":2650,\"splitCredit\":990000000,"
			+ "\"discordLinked\":true,\"drops\":214,\"dropValue\":612000000,\"clogObtained\":812,\"clogTotal\":1568,\"pbs\":37,\"deaths\":41,"
			+ "\"ehb\":130.4,\"ehp\":890.2,\"lastDeathBy\":\"Vorkath\",\"lastDeathAt\":\"2026-11-06T12:00:00Z\",\"topKiller\":\"Verzik Vitur\","
			+ "\"eggsThrown\":14,\"eggsReceived\":9,"
			+ "\"pets\":[{\"itemId\":20851,\"name\":\"Olmlet\"},{\"itemId\":22473,\"name\":\"Lil' Zik\"},{\"itemId\":21992,\"name\":\"Vorki\"}],"
			+ "\"events\":[{\"name\":\"Winter Bingo\",\"result\":\"Team Odin · #2 (live)\"},{\"name\":\"Autumn Bingo\",\"result\":\"Team Freya · 1st\",\"won\":true},"
			+ "{\"name\":\"SOTW: Slayer\",\"result\":\"4th · 3.2M XP\"}],"
			+ "\"personalBests\":[{\"boss\":\"Vorkath\",\"teamSize\":\"1\",\"seconds\":72.6},{\"boss\":\"Zulrah\",\"teamSize\":\"1\",\"seconds\":58.2},"
			+ "{\"boss\":\"Nex\",\"teamSize\":\"4\",\"seconds\":161},{\"boss\":\"Theatre of Blood\",\"teamSize\":\"4\",\"seconds\":892.4}]}",
			ClanData.Me.class);
		return new AsgardiansPanel.State("Rozzle", true, true, AsgardiansConfig.DEFAULT_API_ADDRESS, "Connected as Rozzle", false, 0,
			List.of(), true, true, true, null, false, true, null, false, me);
	}

	private static ClanData.Me cloProfile()
	{
		return GSON.fromJson("{\"rsn\":\"Clo\",\"rank\":\"odin\",\"daysInClan\":1500,\"splitCredit\":2400000000,\"discordLinked\":true,"
			+ "\"drops\":402,\"dropValue\":4100000000,\"clogObtained\":1104,\"clogTotal\":1568,\"deaths\":12,\"ehb\":980,\"ehp\":1402,"
			+ "\"pets\":[{\"itemId\":22473,\"name\":\"Lil' Zik\"}],\"eggsThrown\":3,\"eggsReceived\":21,"
			+ "\"rankings\":[{\"board\":\"Drop value\",\"rank\":1,\"value\":\"2.04B\"},{\"board\":\"Raids KC\",\"rank\":2,\"value\":\"96\"}]}",
			ClanData.Me.class);
	}

	/** A 5 by 5 board with the top row done. */
	private static ClanData.Board board()
	{
		Object[][] tiles = {
			{"Kill Vorkath 250 times", 21907, 250, 250, 10, "Boss KC"}, {"Twisted bow", 20997, 1, 1, 50, "Drop"},
			{"Champion's cape", 21439, 1, 1, 20, "Quest"}, {"Gain 5M Slayer XP", 11864, 5, 5, 10, "Skill XP"},
			{"Zulrah 100 KC", 12921, 100, 100, 10, "Boss KC"},
			{"Kill Nex 50 times", 26348, 35, 50, 15, "Boss KC"}, {"100 Tempoross KC", 25602, 64, 100, 5, "Minigame KC"},
			{"Tumeken's shadow", 27277, 1, 1, 50, "Drop"}, {"Any boss pet", 20851, 0, 1, 25, "Pet"},
			{"Gain 2M Fishing XP", 301, 2, 2, 5, "Skill XP"},
			{"Team: 50M total XP", 995, 41, 50, 20, "Total XP"}, {"25 elite clues", 12073, 7, 25, 15, "Clue"},
			{"Lil' Zik", 22473, 1, 1, 40, "Pet"}, {"Vorki", 21992, 0, 1, 40, "Pet"}, {"Bandos chestplate", 11832, 0, 1, 10, "Drop"},
			{"Abyssal whip", 4151, 1, 1, 5, "Drop"}, {"Gain 3M Mining XP", 1275, 18, 30, 10, "Skill XP"},
			{"Abyssal dagger", 13265, 0, 1, 10, "Drop"}, {"Gain 1M Agility XP", 11850, 3, 10, 5, "Skill XP"},
			{"Dragon warhammer", 13576, 0, 1, 25, "Drop"},
			{"500 dragon bones", 536, 210, 500, 10, "Drop"}, {"Zulrah's scales x20k", 12934, 12, 20, 10, "Drop"},
			{"Elidinis' ward", 27251, 1, 1, 25, "Drop"}, {"Make 100M GP", 995, 25, 100, 15, "Loot value"},
			{"Ancient shard x20", 19677, 3, 20, 10, "Drop"}};
		List<Map<String, Object>> out = new ArrayList<>();
		int i = 0;
		for (Object[] t : tiles)
		{
			Map<String, Object> tile = new HashMap<>();
			tile.put("id", "t" + i++);
			tile.put("name", t[0]);
			tile.put("points", t[4]);
			tile.put("group", t[5]);
			tile.put("have", t[2]);
			tile.put("need", t[3]);
			tile.put("state", ((Integer) t[2]) >= ((Integer) t[3]) ? "accepted" : i == 9 ? "pending" : "needed");
			tile.put("items", List.of(Map.of("itemId", t[1], "name", t[0])));
			if (i == 6)
			{
				tile.put("latest", Map.of("item", "Nex kill", "rsn", "Rozzle"));
			}
			out.add(tile);
		}
		if ("24".equals(System.getProperty("board")))
		{
			// Like the clan's Test-Bingo: 24 tiles, 7 across.
			out.remove(out.size() - 1);
			return GSON.fromJson(GSON.toJson(Map.of("tiles", out, "done", 11, "total", 24, "columns", 7)), ClanData.Board.class);
		}
		return GSON.fromJson(GSON.toJson(Map.of("tiles", out, "done", 11, "total", 25)), ClanData.Board.class);
	}

	private static ClanTabs.Input clan()
	{
		List<ClanData.Event> events = GSON.fromJson("{\"events\":["
			+ "{\"id\":\"bingo\",\"name\":\"Winter Bingo\",\"type\":\"bingo\",\"status\":\"live\",\"startsAt\":\"2026-11-01T19:00:00Z\","
			+ "\"endsAt\":\"2026-11-14T19:00:00Z\",\"signedUp\":true,\"teamId\":\"odin\",\"hasBoard\":true,\"summary\":\"6 teams, 25 tiles. Lines score bonus points.\"},"
			+ "{\"id\":\"sotw\",\"name\":\"Skill of the Week: Slayer\",\"type\":\"sotw\",\"status\":\"upcoming\","
			+ "\"startsAt\":\"2026-11-10T19:00:00Z\",\"endsAt\":\"2026-11-17T19:00:00Z\"},"
			+ "{\"id\":\"party\",\"name\":\"Drop party\",\"type\":\"adhoc\",\"status\":\"upcoming\",\"summary\":\"W416 Falador park\","
			+ "\"startsAt\":\"2026-11-14T20:00:00Z\",\"endsAt\":\"2026-11-14T21:00:00Z\","
			+ "\"signup\":{\"open\":true,\"count\":34,\"myStatus\":\"none\"}},"
			+ "{\"id\":\"botw\",\"name\":\"BOTW: Vorkath\",\"type\":\"botw\",\"status\":\"live\",\"startsAt\":\"2026-11-03T19:00:00Z\","
			+ "\"endsAt\":\"2026-11-10T19:00:00Z\",\"myProgress\":\"14 kills\",\"myRank\":4,\"participants\":38,"
			+ "\"prizes\":\"1st 50M · 2nd 25M · 3rd 10M\",\"womUrl\":\"https://wiseoldman.net/competitions/1\","
			+ "\"standings\":[{\"rank\":1,\"rsn\":\"Clo\",\"value\":\"31\"},{\"rank\":2,\"rsn\":\"Str4tty\",\"value\":\"22\"},"
			+ "{\"rank\":3,\"rsn\":\"pricey\",\"value\":\"17\"},{\"rank\":4,\"rsn\":\"Rozzle\",\"value\":\"14\"},{\"rank\":5,\"rsn\":\"Freya\",\"value\":\"9\"}]},"
			+ "{\"id\":\"spring\",\"name\":\"Spring Bingo\",\"type\":\"bingo\",\"status\":\"upcoming\",\"startsAt\":\"2026-11-21T19:00:00Z\","
			+ "\"endsAt\":\"2026-12-05T19:00:00Z\",\"signup\":{\"open\":true,\"count\":34,\"myStatus\":\"signedUp\"}}]}",
			ClanData.EventList.class).getEvents();
		ClanData.Team own = GSON.fromJson("{\"id\":\"odin\",\"name\":\"Team Odin\",\"captain\":\"Clo\","
			+ "\"members\":[\"Clo\",\"Rozzle\",\"Str4tty\",\"pricey\"]}", ClanData.Team.class);
		List<ClanData.Team> standings = GSON.fromJson("{\"teams\":[{\"id\":\"thor\",\"name\":\"Team Thor\",\"points\":180,\"rank\":1,\"memberCount\":5},"
			+ "{\"id\":\"odin\",\"name\":\"Team Odin\",\"points\":145,\"rank\":2,\"members\":[\"Clo\",\"Rozzle\",\"Str4tty\",\"pricey\"]},"
			+ "{\"id\":\"freya\",\"name\":\"Team Freya\",\"points\":120,\"rank\":3,\"memberCount\":4},"
			+ "{\"id\":\"loki\",\"name\":\"Team Loki\",\"points\":95,\"rank\":4,\"memberCount\":4}]}",
			ClanData.TeamList.class).getTeams();
		List<ClanData.Announcement> news = GSON.fromJson("{\"announcements\":["
			+ "{\"id\":3,\"title\":\"Winter Bingo teams are out\",\"body\":\"Check the Bingo page for your team and board. Team captains, "
			+ "please post your strategy in the Discord by Friday. Line bonuses are 20 points each, and the full rules are on the website.\","
			+ "\"postedAt\":\"2026-11-08T10:00:00Z\",\"postedBy\":\"Clo\"},"
			+ "{\"id\":2,\"title\":\"New ranks for raid teachers\",\"body\":\"Teach 10 raids to earn the Mentor rank.\","
			+ "\"postedAt\":\"2026-11-07T10:00:00Z\",\"postedBy\":\"pricey\"}]}", ClanData.AnnouncementList.class).getAnnouncements();
		ClanView view = new ClanView(List.of(
			new ClanView.EventView(events.get(0), own, standings, board(), Map.of(), eventDrops()),
			new ClanView.EventView(events.get(3), null, List.of(), null),
			new ClanView.EventView(events.get(1), null, List.of(), null),
			new ClanView.EventView(events.get(4), null, List.of(), null),
			new ClanView.EventView(events.get(2), null, List.of(), null)), news, NOW, null);
		List<ClanData.Leaderboard> boards = GSON.fromJson("{\"boards\":["
			+ "{\"title\":\"Drop value\",\"subtitle\":\"November so far\",\"rows\":[{\"rank\":1,\"rsn\":\"Clo\",\"value\":\"2.04B\"},{\"rank\":2,\"rsn\":\"Str4tty\",\"value\":\"1.61B\"},"
			+ "{\"rank\":3,\"rsn\":\"Freya\",\"value\":\"1.45B\"},{\"rank\":4,\"rsn\":\"Odin Jr\",\"value\":\"612M\"},{\"rank\":5,\"rsn\":\"pricey\",\"value\":\"480M\"},"
			+ "{\"rank\":6,\"rsn\":\"Heimdall\",\"value\":\"301M\"},{\"rank\":7,\"rsn\":\"Rozzle\",\"value\":\"276M\"},{\"rank\":8,\"rsn\":\"Loki BTW\",\"value\":\"190M\"}],"
			+ "\"me\":{\"rank\":7,\"rsn\":\"Rozzle\",\"value\":\"276M\"}},"
			+ "{\"title\":\"Eggers\",\"rows\":[{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"14\"},{\"rank\":2,\"rsn\":\"Clo\",\"value\":\"9\"},{\"rank\":3,\"rsn\":\"Thor\",\"value\":\"4\"}],"
			+ "\"me\":{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"14\"}},"
			+ "{\"title\":\"Boss kills\",\"rows\":[{\"rank\":1,\"rsn\":\"Clo\",\"value\":\"1,204\"},{\"rank\":2,\"rsn\":\"pricey\",\"value\":\"988\"},"
			+ "{\"rank\":3,\"rsn\":\"Rozzle\",\"value\":\"640\"},{\"rank\":4,\"rsn\":\"Freya\",\"value\":\"411\"}],\"me\":{\"rank\":3,\"rsn\":\"Rozzle\",\"value\":\"640\"},"
			+ "\"bosses\":["
			+ "{\"boss\":\"Vorkath\",\"category\":\"Bosses\",\"group\":\"\",\"rows\":[{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"212\"},{\"rank\":2,\"rsn\":\"Clo\",\"value\":\"140\"},{\"rank\":3,\"rsn\":\"Freya\",\"value\":\"61\"}],\"me\":{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"212\"}},"
			+ "{\"boss\":\"Alchemical Hydra\",\"category\":\"Bosses\",\"group\":\"Slayer\",\"rows\":[{\"rank\":1,\"rsn\":\"pricey\",\"value\":\"402\"},{\"rank\":2,\"rsn\":\"Clo\",\"value\":\"318\"}]},"
			+ "{\"boss\":\"Cerberus\",\"category\":\"Bosses\",\"group\":\"Slayer\",\"rows\":[{\"rank\":1,\"rsn\":\"Clo\",\"value\":\"250\"}]},"
			+ "{\"boss\":\"Theatre of Blood\",\"category\":\"Raids\",\"group\":\"\",\"rows\":[{\"rank\":1,\"rsn\":\"Clo\",\"value\":\"96\"},{\"rank\":2,\"rsn\":\"Rozzle\",\"value\":\"40\"}]},"
			+ "{\"boss\":\"Tempoross\",\"category\":\"Skilling\",\"group\":\"\",\"rows\":[{\"rank\":1,\"rsn\":\"Freya\",\"value\":\"350\"}]}]},"
			+ "{\"title\":\"XP\",\"rows\":[{\"rank\":1,\"rsn\":\"Heimdall\",\"value\":\"18.4M\"},{\"rank\":2,\"rsn\":\"Rozzle\",\"value\":\"12.1M\"},"
			+ "{\"rank\":3,\"rsn\":\"Freya\",\"value\":\"9.7M\"},{\"rank\":4,\"rsn\":\"Clo\",\"value\":\"6.2M\"}],\"me\":{\"rank\":2,\"rsn\":\"Rozzle\",\"value\":\"12.1M\"},"
			+ "\"skills\":["
			+ "{\"skill\":\"Slayer\",\"rows\":[{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"3.2M\"},{\"rank\":2,\"rsn\":\"Odin Jr\",\"value\":\"2.4M\"},{\"rank\":3,\"rsn\":\"Clo\",\"value\":\"1.1M\"}],"
			+ "\"me\":{\"rank\":1,\"rsn\":\"Rozzle\",\"value\":\"3.2M\"}},"
			+ "{\"skill\":\"Mining\",\"rows\":[{\"rank\":1,\"rsn\":\"Heimdall\",\"value\":\"8.0M\"}]},"
			+ "{\"skill\":\"Fishing\",\"rows\":[{\"rank\":1,\"rsn\":\"Freya\",\"value\":\"4.5M\"}]},"
			+ "{\"skill\":\"Agility\",\"rows\":[{\"rank\":1,\"rsn\":\"Thor\",\"value\":\"1.9M\"}]}]}]}",
			ClanData.LeaderboardList.class).getBoards();
		return new ClanTabs.Input(view, Map.of("rozzle", 416, "clo", 416, "str4tty", 416, "pricey", 302), 1, null, ZONE, NOW,
			AsgardiansConfig.DEFAULT_API_ADDRESS, Map.of(), Set.of(), Set.of(), Map.of(ClanTabs.MONTH, boards));
	}

	private static List<ClanData.Drop> eventDrops()
	{
		return GSON.fromJson("{\"drops\":["
			+ "{\"id\":\"d1\",\"item\":\"Twisted bow\",\"itemId\":20997,\"rsn\":\"Clo\",\"teamId\":\"odin\",\"tileName\":\"Twisted bow\","
			+ "\"state\":\"accepted\",\"at\":\"2026-11-08T11:48:00Z\"},"
			+ "{\"id\":\"d2\",\"item\":\"Vorkath's head\",\"itemId\":21907,\"rsn\":\"Rozzle\",\"teamId\":\"odin\",\"tileName\":\"Kill Vorkath 250 times\","
			+ "\"state\":\"pending\",\"at\":\"2026-11-08T10:00:00Z\"},"
			+ "{\"id\":\"d3\",\"item\":\"Tumeken's shadow\",\"itemId\":27277,\"rsn\":\"Freya\",\"teamId\":\"freya\",\"tileName\":\"Tumeken's shadow\","
			+ "\"state\":\"accepted\",\"at\":\"2026-11-07T20:00:00Z\"},"
			+ "{\"id\":\"d4\",\"item\":\"Abyssal whip\",\"itemId\":4151,\"rsn\":\"Thor\",\"teamId\":\"thor\",\"state\":\"unmatched\","
			+ "\"at\":\"2026-11-07T18:00:00Z\"}]}", ClanData.DropList.class).getDrops();
	}

	private static MembersTabs.Input members()
	{
		return new MembersTabs.Input(List.of(
			new MembersTabs.Member("Rozzle", 416, "Grand Exchange"),
			new MembersTabs.Member("Clo", 416, "Theatre of Blood"),
			new MembersTabs.Member("Str4tty", 416, "Theatre of Blood"),
			new MembersTabs.Member("pricey", 302, "Nex"),
			new MembersTabs.Member("Odin Jr", 330, "Slayer · Abyssal demon"),
			new MembersTabs.Member("Freya", 360, "Tempoross"),
			new MembersTabs.Member("Heimdall", 302, "Mining"),
			new MembersTabs.Member("Loki BTW", 444, null),
			new MembersTabs.Member("Baldur", null, null),
			new MembersTabs.Member("Tyr", null, null)), "Rozzle", 416, "Grand Exchange", true, false, null);
	}

	private static ClanData.StatsFeed feed()
	{
		String tbow = "{\"rsn\":\"Clo\",\"item\":\"Twisted bow\",\"itemId\":20997,\"value\":1320000000,\"source\":\"Chambers of Xeric\",\"kc\":412,\"at\":\"2026-11-08T11:48:00Z\"}";
		String shadow = "{\"rsn\":\"Freya\",\"item\":\"Tumeken's shadow\",\"itemId\":27277,\"value\":1450000000,\"source\":\"Tombs of Amascut\",\"kc\":188,\"at\":\"2026-11-07T20:00:00Z\"}";
		String head = "{\"rsn\":\"Rozzle\",\"item\":\"Vorkath's head\",\"itemId\":21907,\"value\":75000,\"source\":\"Vorkath\",\"kc\":50,\"at\":\"2026-11-08T10:00:00Z\"}";
		String dagger = "{\"rsn\":\"pricey\",\"item\":\"Abyssal dagger\",\"itemId\":13265,\"value\":1100000,\"source\":\"Abyssal demon\",\"at\":\"2026-11-08T09:00:00Z\"}";
		String dwh = "{\"rsn\":\"Odin Jr\",\"item\":\"Dragon warhammer\",\"itemId\":13576,\"value\":38200000,\"source\":\"Lizardman shaman\",\"at\":\"2026-11-07T18:00:00Z\"}";
		String bcp = "{\"rsn\":\"Rozzle\",\"item\":\"Bandos chestplate\",\"itemId\":11832,\"value\":14800000,\"source\":\"General Graardor\",\"kc\":302,\"at\":\"2026-11-06T18:00:00Z\"}";
		String tob = "{\"boss\":\"Theatre of Blood\",\"category\":\"raids\",\"teamSize\":\"4\",\"entries\":["
			+ "{\"rsn\":\"Loki BTW\",\"seconds\":788.4,\"at\":\"2026-10-14T12:00:00Z\"},{\"rsn\":\"Thor\",\"seconds\":836.5},{\"rsn\":\"Clo\",\"seconds\":888.7},"
			+ "{\"rsn\":\"Rozzle\",\"seconds\":892.4},{\"rsn\":\"Freya\",\"seconds\":917.2},{\"rsn\":\"Heimdall\",\"seconds\":970.1},{\"rsn\":\"Str4tty\",\"seconds\":1112.7}]}";
		String tob5 = "{\"boss\":\"Theatre of Blood\",\"category\":\"raids\",\"teamSize\":\"5\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":801.0},{\"rsn\":\"Rozzle\",\"seconds\":866.0}]}";
		String vork = "{\"boss\":\"Vorkath\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":62.4},{\"rsn\":\"Rozzle\",\"seconds\":72.6}]}";
		String nex = "{\"boss\":\"Nex\",\"category\":\"bosses\",\"teamSize\":\"4\",\"entries\":[{\"rsn\":\"pricey\",\"seconds\":141},{\"rsn\":\"Rozzle\",\"seconds\":161}]}";
		String temp = "{\"boss\":\"Tempoross\",\"category\":\"minigames\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Freya\",\"seconds\":185.4}]}";
		String extra = "{\"boss\":\"Alchemical Hydra\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":92.4},{\"rsn\":\"Rozzle\",\"seconds\":101.6}]},{\"boss\":\"Cerberus\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":41.2},{\"rsn\":\"Rozzle\",\"seconds\":45.3}]},{\"boss\":\"Abyssal Sire\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":76.0},{\"rsn\":\"Rozzle\",\"seconds\":83.6}]},{\"boss\":\"Grotesque Guardians\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":88.6},{\"rsn\":\"Rozzle\",\"seconds\":97.5}]},{\"boss\":\"Kraken\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":30.1},{\"rsn\":\"Rozzle\",\"seconds\":33.1}]},{\"boss\":\"Araxxor\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":95.2},{\"rsn\":\"Rozzle\",\"seconds\":104.7}]},{\"boss\":\"General Graardor\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":48.0},{\"rsn\":\"Rozzle\",\"seconds\":52.8}]},{\"boss\":\"Kree'arra\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":52.6},{\"rsn\":\"Rozzle\",\"seconds\":57.9}]},{\"boss\":\"Commander Zilyana\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":44.4},{\"rsn\":\"Rozzle\",\"seconds\":48.8}]},{\"boss\":\"K'ril Tsutsaroth\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":39.8},{\"rsn\":\"Rozzle\",\"seconds\":43.8}]},{\"boss\":\"The Leviathan\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":72.0},{\"rsn\":\"Rozzle\",\"seconds\":79.2}]},{\"boss\":\"Vardorvis\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":61.8},{\"rsn\":\"Rozzle\",\"seconds\":68.0}]},{\"boss\":\"Duke Sucellus\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":88.0},{\"rsn\":\"Rozzle\",\"seconds\":96.8}]},{\"boss\":\"The Whisperer\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":120.4},{\"rsn\":\"Rozzle\",\"seconds\":132.4}]},{\"boss\":\"Callisto\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":58.0},{\"rsn\":\"Rozzle\",\"seconds\":63.8}]},{\"boss\":\"Venenatis\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":64.2},{\"rsn\":\"Rozzle\",\"seconds\":70.6}]},{\"boss\":\"Vet'ion\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":71.0},{\"rsn\":\"Rozzle\",\"seconds\":78.1}]},{\"boss\":\"Zulrah\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":58.2},{\"rsn\":\"Rozzle\",\"seconds\":64.0}]},{\"boss\":\"Phantom Muspah\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":95.0},{\"rsn\":\"Rozzle\",\"seconds\":104.5}]},{\"boss\":\"Wintertodt\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":320.0},{\"rsn\":\"Rozzle\",\"seconds\":352.0}]},{\"boss\":\"The Inferno\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":3720.0},{\"rsn\":\"Rozzle\",\"seconds\":4092.0}]},{\"boss\":\"Fortis Colosseum\",\"category\":\"bosses\",\"teamSize\":\"1\",\"entries\":[{\"rsn\":\"Clo\",\"seconds\":1620.0},{\"rsn\":\"Rozzle\",\"seconds\":1782.0}]}";
		return GSON.fromJson("{"
			+ "\"drops\":[" + tbow + "," + head + "," + dagger + "," + shadow + "," + dwh + "," + bcp + "],"
			+ "\"drops1m\":[" + tbow + "," + dagger + "," + shadow + "," + dwh + "," + bcp + "],"
			+ "\"drops10m\":[" + tbow + "," + shadow + "," + dwh + "," + bcp + "],"
			+ "\"drops100m\":[" + tbow + "," + shadow + "],"
			+ "\"pets\":[{\"rsn\":\"Str4tty\",\"item\":\"Olmlet\",\"itemId\":20851,\"source\":\"Chambers of Xeric\",\"at\":\"2026-11-08T11:20:00Z\"},"
			+ "{\"rsn\":\"Clo\",\"item\":\"Lil' Zik\",\"itemId\":22473,\"source\":\"Theatre of Blood\",\"at\":\"2026-11-07T11:20:00Z\"}],"
			+ "\"clogs\":[" + head + "],"
			+ "\"pbs\":[{\"rsn\":\"Rozzle\",\"boss\":\"Theatre of Blood\",\"teamSize\":\"4\",\"seconds\":892.4,\"kc\":40,\"at\":\"2026-11-08T08:00:00Z\"}],"
			+ "\"records\":[" + tob + "," + tob5 + "," + vork + "," + nex + "," + temp + "," + extra + "]"
			+ "}", ClanData.StatsFeed.class);
	}

	private static ClanData.GroupList groups()
	{
		return GroupsTest.list("{\"mine\":\"g1\",\"groups\":["
			+ "{\"id\":\"g1\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"mode\":\"Hard mode\",\"size\":4,\"loot\":\"split\",\"world\":416,"
			+ "\"host\":\"Clo\",\"party\":\"ASG1\",\"voice\":\"https://discord.com/channels/1/2\",\"members\":[{\"rsn\":\"Clo\",\"kc\":655},{\"rsn\":\"Rozzle\",\"kc\":412}]},"
			+ "{\"id\":\"g2\",\"activity\":\"nex\",\"name\":\"Nex\",\"world\":302,\"loot\":\"ffa\",\"host\":\"pricey\",\"note\":\"Mass, all welcome\","
			+ "\"members\":[{\"rsn\":\"pricey\"},{\"rsn\":\"Thor\"}]}]}");
	}

	private static List<ClanData.GroupActivity> activities()
	{
		return GSON.fromJson("{\"activities\":[{\"id\":\"tob\",\"name\":\"Theatre of Blood\",\"short\":\"ToB\",\"modeShorts\":{\"Hard mode\":\"HM\"},"
			+ "\"modes\":[\"Normal\",\"Hard mode\"],\"loot\":true,\"defaultSize\":4,\"iconItemId\":22473},"
			+ "{\"id\":\"nex\",\"name\":\"Nex\",\"loot\":true,\"iconItemId\":26348}]}", ClanData.GroupActivityList.class).getActivities();
	}
}
