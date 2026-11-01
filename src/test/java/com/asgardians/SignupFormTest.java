package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SignupFormTest
{
	private static ClanData.Field field(String key, String type, boolean required)
	{
		ClanData.Field f = new ClanData.Field();
		f.setKey(key);
		f.setLabel(key);
		f.setType(type);
		f.setRequired(required);
		return f;
	}

	private static final List<ClanData.Field> BINGO_FORM;

	static
	{
		ClanData.Field hours = field("hoursPerDay", "number", true);
		hours.setMin(0.0);
		hours.setMax(24.0);
		ClanData.Field team = field("team", "choice", false);
		team.setOptions(List.of("Any", "Team 1", "Team 2"));
		BINGO_FORM = List.of(hours, field("timezone", "timezone", true), field("caTier", "caTier", false), team,
			field("bosses", "multi", false), field("notes", "text", false));
	}

	private static Map<String, Object> answers(Object... keyValues)
	{
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2)
		{
			map.put((String) keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	@Test
	public void validAnswersAreTypedAndEmptyOptionalsLeftOut()
	{
		SignupForm.Checked checked = SignupForm.check(BINGO_FORM, answers("hoursPerDay", "4", "timezone", "Europe/London",
			"caTier", "Elite", "team", "", "bosses", List.of("Nex", "Nightmare"), "notes", ""));
		assertNull(checked.getError());
		assertEquals(4L, checked.getAnswers().get("hoursPerDay"));
		assertEquals("Europe/London", checked.getAnswers().get("timezone"));
		assertEquals(List.of("Nex", "Nightmare"), checked.getAnswers().get("bosses"));
		assertTrue(!checked.getAnswers().containsKey("team") && !checked.getAnswers().containsKey("notes"));
	}

	@Test
	public void requiredMissingIsRefused()
	{
		assertEquals("hoursPerDay is required.",
			SignupForm.check(BINGO_FORM, answers("hoursPerDay", "", "timezone", "Europe/London")).getError());
	}

	@Test
	public void numbersMustBeNumbersInRange()
	{
		assertEquals("hoursPerDay must be a number.",
			SignupForm.check(BINGO_FORM, answers("hoursPerDay", "lots", "timezone", "UTC")).getError());
		assertEquals("hoursPerDay must be between 0 and 24.",
			SignupForm.check(BINGO_FORM, answers("hoursPerDay", "30", "timezone", "UTC")).getError());
		assertEquals(2.5, SignupForm.check(BINGO_FORM, answers("hoursPerDay", "2.5", "timezone", "UTC")).getAnswers().get("hoursPerDay"));
	}

	@Test
	public void timezoneAndChoiceAreChecked()
	{
		assertTrue(SignupForm.check(BINGO_FORM, answers("hoursPerDay", "1", "timezone", "Mars/Olympus")).getError()
			.contains("isn't a timezone"));
		assertTrue(SignupForm.check(BINGO_FORM, answers("hoursPerDay", "1", "timezone", "UTC", "team", "Team 9")).getError()
			.contains("pick one of the options"));
	}

	@Test
	public void caTierFromPointsAndThresholds()
	{
		int[] thresholds = {41, 161, 416, 1064, 1904, 2630};
		assertEquals("None", SignupForm.caTier(10, thresholds));
		assertEquals("Hard", SignupForm.caTier(500, thresholds));
		assertEquals("Grandmaster", SignupForm.caTier(2700, thresholds));
		assertEquals("None", SignupForm.caTier(500, new int[]{0, 0, 0, 0, 0, 0}));
	}
}
