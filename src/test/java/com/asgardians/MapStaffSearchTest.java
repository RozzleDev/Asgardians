package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class MapStaffSearchTest
{
	private static final Gson GSON = new Gson();
	private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

	private static ClanMap.Pin pin(String name, int world)
	{
		return new ClanMap.Pin(name, world, new WorldPoint(3200, 3200, 0), null);
	}

	@Test
	public void mapShowsOnlyMyWorldUnlessAskedForEveryone()
	{
		List<ClanMap.Pin> all = List.of(pin("Clo", 416), pin("pricey", 302), pin("Rozzle", 416), pin("Thor", 0));
		List<ClanMap.Pin> mine = ClanMap.shown(all, "Rozzle", 416, true);
		assertEquals(1, mine.size());
		assertEquals("Clo", mine.get(0).getName());
		// Everyone but the member themself.
		assertEquals(3, ClanMap.shown(all, "Rozzle", 416, false).size());
		// Not logged in to a world: nobody on "my world".
		assertTrue(ClanMap.shown(all, null, 0, true).isEmpty());
		assertEquals("Clo · W416 · Vorkath", ClanMap.tooltip(new ClanMap.Pin("Clo", 416, new WorldPoint(1, 1, 0), "Vorkath")));
	}

	@Test
	public void memberSearchIgnoresCaseAndSpaces()
	{
		MembersTabs.Member m = new MembersTabs.Member("Odin Jr", 302, null);
		assertTrue(MembersTabs.named(m, "odinj"));
		assertTrue(MembersTabs.named(m, "Jr"));
		assertTrue(MembersTabs.named(m, ""));
		assertFalse(MembersTabs.named(m, "thor"));
	}

	@Test
	public void membersPageNarrowsToTheSearchAndBadgesPluginUsers()
	{
		List<MembersTabs.Member> members = List.of(new MembersTabs.Member("Clo", 416, "Vorkath", true),
			new MembersTabs.Member("pricey", 302, "Mining", false), new MembersTabs.Member("Tyr", null, null, true));
		MembersTabs.Input in = new MembersTabs.Input(members, "Rozzle", 416, null, true, false, null);
		JPanel column = PanelParts.column();
		MembersTabs.draw(column, in, new MembersTabs.Ui("Everyone", true, "cl"), new MembersTabs.Actions()
		{
			@Override
			public void filter(String filter)
			{
			}

			@Override
			public void showOffline(boolean show)
			{
			}

			@Override
			public void toggleSharing()
			{
			}

			@Override
			public void openProfile(String rsn)
			{
			}
		});
		List<String> texts = new ArrayList<>();
		List<JLabel> badged = new ArrayList<>();
		labels(column, texts, badged);
		assertTrue(texts.contains("Clo"));
		assertFalse(texts.contains("pricey"));
		assertFalse(texts.contains("Tyr"));
		assertEquals(1, badged.size());
	}

	@Test
	public void staffNotesTrimAndShowWhoWroteThem()
	{
		assertNull(StaffTabs.clean("   "));
		assertEquals("Holding 100m", StaffTabs.clean("  Holding 100m \n"));
		assertEquals(StaffTabs.MAX_LENGTH, StaffTabs.clean("x".repeat(900)).length());
		ClanData.StaffNote note = GSON.fromJson("{\"id\":\"n1\",\"text\":\"Holding 100m\",\"author\":\"Clo\",\"at\":\"2026-11-12T10:00:00Z\"}",
			ClanData.StaffNote.class);
		assertEquals("Clo · 2h ago", StaffTabs.byline(note, NOW));
		JPanel column = PanelParts.column();
		StaffTabs.drawNotes(column, null, new StaffTabs.Input(List.of(note), false, null, NOW), new StaffTabs.Actions()
		{
			@Override
			public void add(String rsn, String text)
			{
			}

			@Override
			public void delete(String rsn, ClanData.StaffNote n)
			{
			}
		});
		List<String> texts = new ArrayList<>();
		labels(column, texts, new ArrayList<>());
		assertTrue(texts.stream().anyMatch(t -> t.contains("Holding 100m")));
		ClanData.Me me = GSON.fromJson("{\"rsn\":\"Rozzle\",\"staff\":true}", ClanData.Me.class);
		assertEquals(Boolean.TRUE, me.getStaff());
	}

	@Test
	public void onlyALoneFracturedArchiveCabbageCounts()
	{
		net.runelite.client.game.ItemStack cabbage = new net.runelite.client.game.ItemStack(net.runelite.api.gameval.ItemID.CABBAGE, 1);
		net.runelite.client.game.ItemStack runes = new net.runelite.client.game.ItemStack(net.runelite.api.gameval.ItemID.DEATHRUNE, 500);
		assertTrue(AsgardiansPlugin.isCabbage("Fractured Archive", List.of(cabbage)));
		assertFalse(AsgardiansPlugin.isCabbage("Fractured Archive", List.of(runes)));
		assertFalse(AsgardiansPlugin.isCabbage("Fractured Archive", List.of()));
		assertFalse(AsgardiansPlugin.isCabbage("Barrows", List.of(cabbage)));
	}

	@Test
	public void deathMessagesFillInTheirPlaceholders()
	{
		assertNull(Death.message("  ", "Vorkath", null, 0));
		assertNull(Death.message(null, "Vorkath", null, 0));
		assertEquals("Sit. Vorkath got me at Wilderness for 1.2M gp",
			Death.message(" Sit. {killer} got me at {place} for {lost} ", "Vorkath", "Wilderness", 1_200_000));
		assertEquals("something got me, lost nothing", Death.message("{killer} got me, lost {lost}", null, null, 0));
		assertEquals(Death.MESSAGE_LENGTH, Death.message("x".repeat(400), null, null, 0).length());
	}

	@Test
	public void onlyTheTenMinuteLogoutWarningIsANerdLog()
	{
		assertTrue(ChatParsers.isNerdLogWarning("You will be logged out in approximately 10 minutes. Make sure you move to a safe area or log out now."));
		assertFalse(ChatParsers.isNerdLogWarning("You will be logged out in approximately 30 minutes. Make sure you move to a safe area or log out now."));
		assertFalse(ChatParsers.isNerdLogWarning("You will be logged out in approximately 5 minutes. Make sure you move to a safe area or log out now."));
		assertFalse(ChatParsers.isNerdLogWarning("You've been playing for a while, consider taking a break from your screen."));
	}

	private static void labels(Container c, List<String> texts, List<JLabel> badged)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof JLabel)
			{
				JLabel l = (JLabel) child;
				texts.add(l.getText());
				if (l.getIcon() != null && l.getToolTipText() != null && l.getToolTipText().contains("plugin"))
				{
					badged.add(l);
				}
			}
			if (child instanceof Container)
			{
				labels((Container) child, texts, badged);
			}
		}
	}
}
