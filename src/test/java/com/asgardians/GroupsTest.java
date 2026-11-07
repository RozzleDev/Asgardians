package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import org.junit.Test;

public class GroupsTest
{
	private static final Gson GSON = new Gson();

	static ClanData.GroupList list(String json)
	{
		return GSON.fromJson(json, ClanData.GroupList.class);
	}

	private static String group(String id, String host, int size, String world, String... members)
	{
		StringBuilder m = new StringBuilder();
		for (String rsn : members)
		{
			m.append(m.length() > 0 ? "," : "").append("{\"rsn\":\"").append(rsn).append("\"}");
		}
		return "{\"id\":\"" + id + "\",\"activity\":\"tob\",\"name\":\"Theatre of Blood\",\"mode\":\"Hard mode\",\"size\":" + size
			+ ",\"loot\":\"split\",\"world\":" + world + ",\"host\":\"" + host + "\",\"members\":[" + m + "]}";
	}

	private static ClanData.GroupList groups(String mine, String... groups)
	{
		return list("{\"mine\":" + (mine != null ? "\"" + mine + "\"" : "null") + ",\"groups\":[" + String.join(",", groups) + "]}");
	}

	@Test
	public void theFirstLoadSaysNothing()
	{
		assertEquals(List.of(), Groups.changes(null, groups("g1", group("g1", "Rozzle", 4, "416", "Rozzle")), "Rozzle", null));
		assertEquals(List.of(), Groups.started(null, groups(null, group("g1", "Clo", 4, "416", "Clo")), "Rozzle"));
	}

	@Test
	public void startingAndJoining()
	{
		ClanData.GroupList none = groups(null);
		assertEquals(List.of("Your Theatre of Blood (Hard mode) group is open on W416. Others can join from the Groups tab."),
			Groups.changes(none, groups("g1", group("g1", "Rozzle", 4, "416", "Rozzle")), "Rozzle", null));
		assertEquals(List.of("You joined Clo's Theatre of Blood (Hard mode) group (2/4) on W416."),
			Groups.changes(none, groups("g1", group("g1", "Clo", 4, "416", "Clo", "Rozzle")), "Rozzle", null));
		assertEquals(List.of("You joined Clo's Theatre of Blood (Hard mode) group (2/2) on W416.", "Group full (2/2): time to go! Hop to W416."),
			Groups.changes(none, groups("g1", group("g1", "Clo", 2, "416", "Clo", "Rozzle")), "Rozzle", null));
	}

	@Test
	public void changesInTheMembersOwnGroup()
	{
		ClanData.GroupList before = groups("g1", group("g1", "Rozzle", 3, "416", "Rozzle", "Clo"));
		assertEquals(List.of("Str4tty joined your group (3/3).", "Group full (3/3): time to go! Hop to W416."),
			Groups.changes(before, groups("g1", group("g1", "Rozzle", 3, "416", "Rozzle", "Clo", "Str4tty")), "Rozzle", null));
		assertEquals(List.of("Clo left your group (1/3)."),
			Groups.changes(before, groups("g1", group("g1", "Rozzle", 3, "416", "Rozzle")), "Rozzle", null));
		assertEquals(List.of("You're now the group's host.", "The group moved to W420."),
			Groups.changes(groups("g1", group("g1", "Clo", 3, "416", "Clo", "Rozzle")), groups("g1", group("g1", "Rozzle", 3, "420", "Clo", "Rozzle")), "Rozzle", null));
	}

	@Test
	public void leavingAndBeingRemoved()
	{
		ClanData.GroupList in = groups("g1", group("g1", "Clo", 4, "416", "Clo", "Rozzle"));
		String other = group("g1", "Clo", 4, "416", "Clo");
		assertEquals(List.of("You left the Theatre of Blood (Hard mode) group."), Groups.changes(in, groups(null, other), "Rozzle", "g1"));
		assertEquals(List.of("You were removed from Clo's Theatre of Blood (Hard mode) group."), Groups.changes(in, groups(null, other), "Rozzle", null));
		assertEquals(List.of("The Theatre of Blood (Hard mode) group was closed."), Groups.changes(in, groups(null), "Rozzle", null));
		ClanData.GroupList away = groups(null);
		away.setReason("away");
		assertEquals(List.of("You were taken out of the Theatre of Blood (Hard mode) group after being away."), Groups.changes(in, away, "Rozzle", null));
		ClanData.GroupList hosting = groups("g1", group("g1", "Rozzle", 4, "416", "Rozzle"));
		assertEquals(List.of("You closed your Theatre of Blood (Hard mode) group."), Groups.changes(hosting, groups(null), "Rozzle", "g1"));
	}

	@Test
	public void newGroupsAreAnnouncedButNotYourOwnOrFullOnes()
	{
		ClanData.GroupList before = groups(null, group("g1", "Clo", 4, "416", "Clo"));
		ClanData.GroupList now = groups(null, group("g1", "Clo", 4, "416", "Clo"), group("g2", "Str4tty", 5, "302", "Str4tty"),
			group("g3", "Rozzle", 4, "416", "Rozzle"), group("g4", "Dolorosa", 1, "416", "Dolorosa"));
		assertEquals(List.of("Str4tty started a group: Theatre of Blood (Hard mode), 1/5, W302. Join from the Groups tab."),
			Groups.started(before, now, "Rozzle"));
	}

	@Test
	public void seatsAndDetails()
	{
		ClanData.Group mass = list("{\"groups\":[{\"id\":\"m\",\"name\":\"Nex\",\"mass\":true,\"world\":444,\"members\":[{\"rsn\":\"A\"},{\"rsn\":\"B\"}]}]}").find("m");
		assertEquals("2/8+ · W444", GroupTabs.details(mass));
		ClanData.Group anySize = list("{\"groups\":[{\"id\":\"a\",\"name\":\"Slayer\",\"members\":[{\"rsn\":\"A\"}]}]}").find("a");
		assertEquals("1/any", GroupTabs.details(anySize));
		assertTrue(!anySize.isFull());
		ClanData.Group ffa = list("{\"groups\":[{\"id\":\"f\",\"name\":\"Corp\",\"size\":4,\"loot\":\"ffa\"}]}").find("f");
		assertEquals("0/4 · FFA", GroupTabs.details(ffa));
		assertTrue(!ffa.isFull());
	}

	static GroupTabs.Actions actions()
	{
		return new GroupTabs.Actions()
		{
			public void create(GroupTabs.GroupForm.Values values) { }
			public void join(ClanData.Group group) { }
			public void leave(ClanData.Group group) { }
			public void close(ClanData.Group group) { }
			public void kick(ClanData.Group group, String rsn) { }
			public void joinParty(ClanData.Group group) { }
			public void refresh() { }
		};
	}

	@Test
	public void redrawsLeaveTheFormInPlace()
	{
		GroupTabs.Actions a = actions();
		GroupTabs.GroupForm form = new GroupTabs.GroupForm(v -> { });
		ZoneId zone = ZoneId.of("Europe/London");
		JPanel column = PanelParts.column();
		GroupTabs.draw(column, new GroupTabs.Input(groups(null), List.of(), false, null, "Rozzle", Map.of(), false, false, null, zone, null), form, a);
		java.awt.Component card = column.getComponent(0);
		assertTrue(javax.swing.SwingUtilities.isDescendingFrom(form, (java.awt.Container) card));
		// A new group appears: the form isn't taken out and put back, so the member's cursor stays in it.
		GroupTabs.draw(column, new GroupTabs.Input(groups(null, group("g2", "Clo", 4, "302", "Clo")), List.of(), false, null, "Rozzle",
			Map.of(), false, false, null, zone, null), form, a);
		assertTrue(column.getComponent(0) == card);
		assertTrue(form.getParent() == card);
		// In a group the form is hidden; out of it again, it's back on top.
		GroupTabs.draw(column, new GroupTabs.Input(groups("g1", group("g1", "Rozzle", 4, "416", "Rozzle")), List.of(), false, null,
			"Rozzle", Map.of(), false, false, null, zone, null), form, a);
		assertTrue(!javax.swing.SwingUtilities.isDescendingFrom(form, column));
		GroupTabs.draw(column, new GroupTabs.Input(groups(null), List.of(), false, null, "Rozzle", Map.of(), false, false, null, zone, null), form, a);
		assertTrue(column.getComponent(0) == card);
	}

	@Test
	public void theTabDrawsInEveryState()
	{
		GroupTabs.Actions a = actions();
		GroupTabs.GroupForm form = new GroupTabs.GroupForm(v -> { });
		List<ClanData.GroupActivity> activities = GSON.fromJson("{\"activities\":[{\"id\":\"tob\",\"name\":\"Theatre of Blood\","
			+ "\"modes\":[\"Normal\",\"Hard mode\"],\"minSize\":2,\"maxSize\":5,\"defaultSize\":4,\"loot\":true,\"kcName\":\"theatre of blood\"},"
			+ "{\"id\":\"nex\",\"name\":\"Nex\",\"mass\":true},{}]}", ClanData.GroupActivityList.class).getActivities();
		Instant now = Instant.parse("2026-11-06T12:00:00Z");
		ZoneId zone = ZoneId.of("Europe/London");
		Map<String, Integer> online = Map.of("rozzle", 416);
		ClanData.GroupList odd = list("{\"mine\":\"g1\",\"groups\":[{\"id\":\"g1\",\"members\":[null,{},{\"rsn\":\"Rozzle\",\"kc\":12}],"
			+ "\"party\":\"asg-abc\",\"canClose\":true},{\"id\":\"g2\"},{},null]}");
		for (ClanData.GroupList l : new ClanData.GroupList[]{null, groups(null), groups("missing"), odd,
			groups("g1", group("g1", "Rozzle", 2, "416", "Rozzle", "Clo"), group("g2", "Clo", 4, "302", "Clo"))})
		{
			for (boolean busy : new boolean[]{false, true})
			{
				GroupTabs.draw(new JPanel(), new GroupTabs.Input(l, activities, false, null, "Rozzle", online, busy, busy, now, zone, null), form, a);
			}
		}
		GroupTabs.draw(new JPanel(), new GroupTabs.Input(null, List.of(), true, null, "Rozzle", Map.of(), false, false, null, zone, null), form, a);
		GroupTabs.draw(new JPanel(), new GroupTabs.Input(null, null, false, "Log in to find or start a group.", null, Map.of(), false, false, null, zone, null), form, a);

		GroupTabs.GroupForm.Values v = form.values();
		assertEquals("tob", v.getActivity().getId());
		assertEquals("Normal", v.getMode());
		assertEquals(Integer.valueOf(4), v.getSize());
		assertEquals("split", v.getLoot());
	}

	@Test
	public void theTabOnlyRedrawsWhenSomethingShownChanges()
	{
		ClanData.GroupList l = groups(null, group("g1", "Clo", 4, "416", "Clo"));
		ZoneId zone = ZoneId.of("Europe/London");
		GroupTabs.Input a = new GroupTabs.Input(l, List.of(), false, null, "Rozzle", Map.of("clo", 416, "someone", 301), false, false,
			Instant.parse("2026-11-06T12:00:05Z"), zone, null);
		// Seconds later, same minute, and a clan member who isn't in a group hopped: nothing shown changed.
		GroupTabs.Input b = new GroupTabs.Input(groups(null, group("g1", "Clo", 4, "416", "Clo")), List.of(), false, null, "Rozzle",
			Map.of("clo", 416, "someone", 420), false, false, Instant.parse("2026-11-06T12:00:45Z"), zone, null);
		assertEquals(GroupTabs.redrawKey(a), GroupTabs.redrawKey(b));
		// The host went offline: redraw.
		GroupTabs.Input c = new GroupTabs.Input(l, List.of(), false, null, "Rozzle", Map.of(), false, false,
			Instant.parse("2026-11-06T12:00:45Z"), zone, null);
		assertTrue(!GroupTabs.redrawKey(a).equals(GroupTabs.redrawKey(c)));
	}

	@Test
	public void sizesRunFromTwoToEightThenEightPlus()
	{
		GroupTabs.GroupForm form = new GroupTabs.GroupForm(v -> { });
		form.setActivities(GSON.fromJson("{\"activities\":[{\"id\":\"cox\",\"name\":\"Chambers of Xeric\"}]}",
			ClanData.GroupActivityList.class).getActivities());
		javax.swing.JComboBox<?> size = sizeBox(form);
		assertEquals(9, size.getItemCount());
		assertEquals("Any", size.getItemAt(0));
		assertEquals("2", size.getItemAt(1));
		assertEquals("8", size.getItemAt(7));
		assertEquals("8+", size.getItemAt(8));
		GroupTabs.GroupForm.Values any = form.values();
		assertEquals(null, any.getSize());
		assertEquals(false, any.isMass());
		form.setActivities(GSON.fromJson("{\"activities\":[{\"id\":\"titans\",\"name\":\"Royal Titans\",\"minSize\":2,\"maxSize\":2}]}",
			ClanData.GroupActivityList.class).getActivities());
		assertEquals(1, sizeBox(form).getItemCount());
		// TFA: capped at 8, so Any and 2 to 8 but no 8+; Any fills at 8.
		form.setActivities(GSON.fromJson("{\"activities\":[{\"id\":\"tfa\",\"name\":\"The Fractured Archive\",\"maxSize\":8}]}",
			ClanData.GroupActivityList.class).getActivities());
		size = sizeBox(form);
		assertEquals(8, size.getItemCount());
		assertEquals("Any", size.getItemAt(0));
		assertEquals("8", size.getItemAt(7));
		assertEquals(Integer.valueOf(8), form.values().getSize());
		assertEquals(false, form.values().isMass());
		// Nex as the website sends it: maxSize 8 but a mass, so 8+ stays.
		form.setActivities(GSON.fromJson("{\"activities\":[{\"id\":\"nex\",\"name\":\"Nex\",\"minSize\":2,\"maxSize\":8,\"mass\":true}]}",
			ClanData.GroupActivityList.class).getActivities());
		size = sizeBox(form);
		assertEquals(9, size.getItemCount());
		assertEquals("8+", size.getItemAt(8));
		assertEquals(null, form.values().getSize());
	}

	private static javax.swing.JComboBox<?> sizeBox(GroupTabs.GroupForm form)
	{
		try
		{
			java.lang.reflect.Field f = GroupTabs.GroupForm.class.getDeclaredField("size");
			f.setAccessible(true);
			return (javax.swing.JComboBox<?>) f.get(form);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
}
