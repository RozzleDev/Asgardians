package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import org.junit.Test;

public class GroupBellsTest
{
	private static ClanData.GroupActivity activity(String id, String alerts)
	{
		ClanData.GroupActivity a = new ClanData.GroupActivity();
		a.setId(id);
		a.setName(id.toUpperCase());
		a.setAlerts(alerts);
		return a;
	}

	@Test
	public void websiteSettingsAreReadFromJson()
	{
		ClanData.GroupActivityList list = new Gson().fromJson("{\"activities\":[{\"id\":\"tob\",\"alerts\":\"always\"},"
			+ "{\"id\":\"cox\",\"alerts\":\"off\"},{\"id\":\"nex\"},{\"id\":\"toa\",\"alerts\":\"Weird\"}]}", ClanData.GroupActivityList.class);
		List<String> modes = new ArrayList<>();
		list.getActivities().forEach(a -> modes.add(GroupBells.mode(a)));
		assertEquals(List.of("always", "off", "on", "on"), modes);
	}

	@Test
	public void theWebsiteDefaultAppliesUntilTheMemberChooses()
	{
		Map<String, Boolean> none = Map.of();
		assertTrue(GroupBells.rings(activity("nex", null), none, true));
		assertTrue(GroupBells.rings(activity("nex", "on"), none, true));
		assertFalse(GroupBells.rings(activity("cox", "off"), none, true));
		assertFalse(GroupBells.rings(activity("nex", "on"), Map.of("nex", false), true));
		assertTrue(GroupBells.rings(activity("cox", "off"), Map.of("cox", true), true));
	}

	@Test
	public void alwaysRingsForEveryone()
	{
		assertTrue(GroupBells.rings(activity("tob", "always"), Map.of("tob", false), true));
		assertTrue(GroupBells.bell(activity("tob", "always"), Map.of("tob", false)));
		// Even with "New groups in chat" turned off.
		assertTrue(GroupBells.rings(activity("tob", "always"), Map.of(), false));
	}

	@Test
	public void theSettingMutesTheRest()
	{
		assertFalse(GroupBells.rings(activity("nex", "on"), Map.of("nex", true), false));
		// The bell still shows what the member picked, ready for when the setting is turned back on.
		assertTrue(GroupBells.bell(activity("nex", "on"), Map.of("nex", true)));
	}

	@Test
	public void choicesAreSavedAndRead()
	{
		Map<String, Boolean> choices = new LinkedHashMap<>();
		choices.put("tob", true);
		choices.put("cox", false);
		choices.put("bad,id", true);
		assertEquals("tob=1,cox=0", GroupBells.format(choices));
		assertEquals(Map.of("tob", true, "cox", false), GroupBells.parse("tob=1, cox=0,junk,=1,x=2"));
		assertEquals(Map.of(), GroupBells.parse(null));
	}

	@Test
	public void onlyRingingActivitiesAreAnnounced()
	{
		ClanData.GroupList before = GroupsTest.list("{\"groups\":[]}");
		ClanData.GroupList now = GroupsTest.list("{\"groups\":["
			+ "{\"id\":\"g1\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"size\":4,\"world\":416,\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"}]},"
			+ "{\"id\":\"g2\",\"activity\":\"cox\",\"name\":\"Chambers of Xeric\",\"size\":4,\"world\":416,\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"}]}]}");
		List<String> lines = Groups.started(before, now, "Rozzle", id -> "tob".equals(id));
		assertEquals(1, lines.size());
		assertTrue(lines.get(0), lines.get(0).contains("Theatre of Blood"));
	}

	@Test
	public void theCardListsEveryActivityWithABell()
	{
		List<String> rung = new ArrayList<>();
		GroupTabs.Actions actions = new GroupTabs.Actions()
		{
			@Override
			public void create(GroupTabs.GroupForm.Values values)
			{
			}

			@Override
			public void join(ClanData.Group group)
			{
			}

			@Override
			public void leave(ClanData.Group group)
			{
			}

			@Override
			public void close(ClanData.Group group)
			{
			}

			@Override
			public void kick(ClanData.Group group, String rsn)
			{
			}

			@Override
			public void joinParty(ClanData.Group group)
			{
			}

			@Override
			public void refresh()
			{
			}

			@Override
			public void bell(String activityId, boolean on)
			{
				rung.add(activityId + "=" + on);
			}

			@Override
			public void screen(String activityId, boolean on)
			{
				rung.add("screen " + activityId + "=" + on);
			}
		};
		List<ClanData.GroupActivity> activities = List.of(activity("tob", "always"), activity("cox", "off"), activity("nex", null));
		List<JButton> buttons = new ArrayList<>();
		collect(GroupTabs.bellsCard(activities, new GroupTabs.Bells(Map.of(), true, true, Map.of(), true), actions), buttons);
		// A screen icon and a bell for each activity.
		assertEquals(6, buttons.size());
		buttons.forEach(JButton::doClick);
		// The staff's "always" bell can't be muted, and the screen icon does nothing while its bell is muted (cox).
		assertEquals(List.of("screen tob=false", "cox=true", "screen nex=false", "nex=false"), rung);

		rung.clear();
		List<JButton> screenOff = new ArrayList<>();
		collect(GroupTabs.bellsCard(activities, new GroupTabs.Bells(Map.of(), true, true, Map.of(), false), actions), screenOff);
		screenOff.forEach(JButton::doClick);
		// "New groups on screen" is off, so no screen icon does anything.
		assertEquals(List.of("cox=true", "nex=false"), rung);

		List<JButton> folded = new ArrayList<>();
		collect(GroupTabs.bellsCard(activities, new GroupTabs.Bells(Map.of(), true, false, Map.of(), true), actions), folded);
		assertEquals(0, folded.size());
	}

	@Test
	public void shortNamesFromTheWebsiteKeepMessagesShort()
	{
		ClanData.GroupActivityList activities = new Gson().fromJson("{\"activities\":[{\"id\":\"tob\",\"name\":\"Theatre of Blood\","
			+ "\"short\":\"ToB\",\"modeShorts\":{\"Hard mode\":\"HM\"}},{\"id\":\"nex\",\"name\":\"Nex\"}]}", ClanData.GroupActivityList.class);
		ClanData.GroupActivity tob = activities.getActivities().get(0);
		ClanData.GroupActivity nex = activities.getActivities().get(1);
		ClanData.GroupList list = GroupsTest.list("{\"groups\":["
			+ "{\"id\":\"g1\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"mode\":\"Hard mode\",\"size\":4,\"world\":416,\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"}]},"
			+ "{\"id\":\"g2\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"mode\":\"Entry mode\",\"size\":4,\"world\":416,\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"}]},"
			+ "{\"id\":\"g3\",\"activity\":\"nex\",\"name\":\"Nex\",\"world\":302,\"host\":\"Str4tty\",\"members\":[{\"rsn\":\"Str4tty\"}]}]}");
		ClanData.Group hard = list.getGroups().get(0);
		assertEquals("ToB HM", Groups.shortTitle(hard, tob));
		assertEquals("ToB (Entry mode)", Groups.shortTitle(list.getGroups().get(1), tob));
		assertEquals("Nex", Groups.shortTitle(list.getGroups().get(2), nex));
		assertEquals("Theatre of Blood (Hard mode)", Groups.shortTitle(hard, null));
		assertEquals("Clo started a group: ToB HM, 1/4, W416. Join from the Groups tab.",
			Groups.startedLine(hard, Groups.shortTitle(hard, tob)));
	}

	@Test
	public void onlyDiscordVoiceLinksOpen()
	{
		ClanData.Group g = new ClanData.Group();
		g.setVoice("https://discord.com/channels/111/222");
		assertEquals("https://discord.com/channels/111/222", Groups.voiceLink(g));
		g.setVoice(" https://discord.gg/abc ");
		assertEquals("https://discord.gg/abc", Groups.voiceLink(g));
		for (String bad : new String[]{"http://discord.com/channels/1/2", "https://discord.com.evil.io/x", "https://evil.io",
			"javascript:alert(1)", "", null})
		{
			g.setVoice(bad);
			assertEquals(bad, null, Groups.voiceLink(g));
		}
	}

	@Test
	public void theVoiceTickBoxStartsTicked()
	{
		GroupTabs.GroupForm form = new GroupTabs.GroupForm(v -> { });
		form.setActivities(List.of(activity("tob", null)));
		assertTrue(form.values().isVoice());
		assertTrue(form.values().isVoiceLimit());
	}

	@Test
	public void theSizeLockCanBeTurnedOff()
	{
		GroupTabs.GroupForm form = new GroupTabs.GroupForm(v -> { });
		form.setActivities(List.of(activity("tob", null)));
		List<javax.swing.JCheckBox> boxes = new ArrayList<>();
		collectBoxes(form, boxes);
		javax.swing.JCheckBox voice = boxes.stream().filter(b -> b.getText().equals("Discord voice channel")).findFirst().get();
		javax.swing.JCheckBox lock = boxes.stream().filter(b -> b.getText().equals("Lock to group size")).findFirst().get();
		lock.setSelected(false);
		assertTrue(form.values().isVoice());
		assertFalse(form.values().isVoiceLimit());
		// With no voice channel, the lock is greyed out and never sent as on.
		lock.setSelected(true);
		voice.setSelected(false);
		assertFalse(lock.isEnabled());
		assertFalse(form.values().isVoiceLimit());
	}

	private static void collectBoxes(Component c, List<javax.swing.JCheckBox> out)
	{
		if (c instanceof javax.swing.JCheckBox)
		{
			out.add((javax.swing.JCheckBox) c);
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collectBoxes(child, out);
			}
		}
	}

	@Test
	public void screensAreOnUntilTurnedOff()
	{
		ClanData.GroupActivity nex = activity("nex", null);
		assertTrue(GroupBells.onScreen(nex, Map.of(), Map.of(), true, true));
		assertFalse(GroupBells.onScreen(nex, Map.of(), Map.of("nex", false), true, true));
		// Not when its bell is muted, nor with "New groups on screen" off.
		assertFalse(GroupBells.onScreen(nex, Map.of("nex", false), Map.of(), true, true));
		assertFalse(GroupBells.onScreen(nex, Map.of(), Map.of(), true, false));
		// Staff's "always" activities follow the member's screen icon too.
		assertTrue(GroupBells.onScreen(activity("tob", "always"), Map.of(), Map.of(), false, true));
		assertFalse(GroupBells.onScreen(activity("tob", "always"), Map.of(), Map.of("tob", false), false, true));
	}

	@Test
	public void theOverlayShowsTheNewestFewForAWhile()
	{
		long[] now = {1_000};
		GroupAlertOverlay overlay = new GroupAlertOverlay(null, () -> true, () -> now[0]);
		ClanData.GroupList list = GroupsTest.list("{\"groups\":["
			+ "{\"id\":\"g1\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"size\":4,\"world\":416,\"host\":\"Clo\",\"members\":[{\"rsn\":\"Clo\"}]}]}");
		ClanData.Group g = list.getGroups().get(0);
		for (int i = 0; i < 5; i++)
		{
			overlay.add(g);
			now[0] += 1_000;
		}
		assertEquals(GroupAlertOverlay.MAX_SHOWN, overlay.showing().size());
		assertEquals("Clo", overlay.showing().get(0).getHost());
		assertEquals("Theatre of Blood", overlay.showing().get(0).getTitle());
		now[0] += GroupAlertOverlay.SHOW_MILLIS;
		assertEquals(0, overlay.showing().size());
	}

	private static void collect(Component c, List<JButton> out)
	{
		if (c instanceof JButton)
		{
			out.add((JButton) c);
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collect(child, out);
			}
		}
	}
}
