package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SantaTabsTest
{
	private static final Gson GSON = new Gson();

	private static SantaData.Defence defence(String kind, long amount, String skill, String... items)
	{
		SantaData.Defence d = new SantaData.Defence();
		d.setKind(kind);
		d.setAmount(amount);
		d.setSkill(skill);
		d.setItems(items.length > 0 ? Arrays.asList(items) : null);
		return d;
	}

	@Test
	public void defence_text_matches_the_website_for_every_kind()
	{
		assertEquals("Gain 250k Thieving XP as a clan", SantaTabs.defenceText(defence("xp", 250_000, "Thieving")));
		assertEquals("Get 50 Wintertodt kills as a clan", SantaTabs.defenceText(defence("kc", 50, "wintertodt")));
		assertEquals("Land 1 confirmed drop", SantaTabs.defenceText(defence("drops", 1, null)));
		assertEquals("Find 1 of: Mysterious page, Dark dye", SantaTabs.defenceText(defence("items", 1, null, "Mysterious page", "Dark dye")));
		assertEquals("Get 25 clanmates to press Defend!", SantaTabs.defenceText(defence("rally", 25, null)));
		assertEquals("9 / 25 defending", SantaTabs.progressText(defence("rally", 25, null), 9));
		assertEquals("62.5k / 100k XP", SantaTabs.progressText(defence("xp", 100_000, "Thieving"), 62_500));
		assertEquals("37.5k Thieving XP left", SantaTabs.remainingText(defence("xp", 100_000, "Thieving"), 62_500));
		assertEquals("1 more defender", SantaTabs.remainingText(defence("rally", 25, null), 24));
		assertEquals("0 more drops", SantaTabs.remainingText(defence("drops", 3, null), 5));
	}

	@Test
	public void eachBossGetsItsIcon()
	{
		assertEquals(SantaIcons.Kind.SNOWMAN, SantaIcons.kind("Frosty", "Frosty the Roadman and his Robbin's"));
		assertEquals(SantaIcons.Kind.LIPSTICK, SantaIcons.kind("Slay", "Slay, the Drag Queen Superstar"));
		assertEquals(SantaIcons.Kind.NUTCRACKER, SantaIcons.kind("The Nutcracka", null));
		assertEquals(SantaIcons.Kind.REINDEER, SantaIcons.kind("Rude-olph", "Rude-olph the Drunk Unc"));
		assertEquals(SantaIcons.Kind.GINGERBREAD, SantaIcons.kind("Ginger Dread", "Ginger Dread"));
		assertEquals(SantaIcons.Kind.SANTA_HAT, SantaIcons.kind("Santa Claws", "Santa Claws and the Elf-in-Laws"));
		assertEquals(SantaIcons.Kind.STAR, SantaIcons.kind(null, "Krampus"));
		assertEquals("Act I", SantaTabs.actTitle("ACT I"));
		assertEquals("Finale", SantaTabs.actTitle("FINALE"));
	}

	@Test
	public void xp_is_short()
	{
		assertEquals("1m", SantaTabs.fmtXp(1_000_000));
		assertEquals("1.25m", SantaTabs.fmtXp(1_250_000));
		assertEquals("250k", SantaTabs.fmtXp(250_000));
		assertEquals("999", SantaTabs.fmtXp(999));
	}

	@Test
	public void the_sample_page_reads_and_the_tile_says_an_attack_is_coming()
	{
		SantaData.Page page = sample();
		SantaData.Encounter frosty = page.getBoard().active();
		assertEquals("Frosty", frosty.getShortName());
		assertEquals("rally", frosty.getPendingAttack().getDefence().getKind());
		assertTrue(SantaTabs.underAttack(page));
		assertEquals("Attack incoming!", SantaTabs.tileLine(page, Instant.parse(page.getBoard().getNow())));
		SantaTabs.Debuff first = SantaTabs.debuffs(frosty).get(0);
		assertEquals("Snowball Drive-by", first.getTitle());
		assertEquals("2 drops", first.getEffect());
		// Worded like the website's debuff list, not the attack's blurb.
		assertEquals("Next 2 drops half damage", first.getAbout());
		// A curse on several members is one line, one sentence each.
		SantaData.Curse a = new SantaData.Curse();
		a.setAttackName("Nut Shot");
		a.setRsn("Frigg Rune");
		a.setMode("half");
		SantaData.Curse b = new SantaData.Curse();
		b.setAttackName("Nut Shot");
		b.setRsn("Asgard Odin");
		b.setMode("half");
		frosty.getEffects().setCurses(java.util.List.of(a, b));
		SantaTabs.Debuff nut = SantaTabs.debuffs(frosty).stream().filter(d -> d.getTitle().equals("Nut Shot")).findFirst().orElseThrow();
		assertEquals("2 members", nut.getEffect());
		assertEquals("Frigg Rune's next drop does half\nAsgard Odin's next drop does half", nut.getAbout());
		// A frozen bonus is named after the attack that froze it.
		frosty.getBonus().setFrozen(true);
		List<SantaTabs.Debuff> all = SantaTabs.debuffs(frosty);
		SantaTabs.Debuff frozen = all.get(all.size() - 1);
		assertEquals("Frozen Assets", frozen.getTitle());
		assertEquals("Roasted switched off for 4 hours", frozen.getAbout());
		// Every other boss is still wrapped.
		assertFalse(page.getBoard().getEncounters().get(1).isRevealed());
	}

	@Test
	public void the_tile_shows_the_boss_once_the_attacks_are_blocked()
	{
		SantaData.Page page = sample();
		SantaData.Encounter frosty = page.getBoard().active();
		frosty.getPendingAttack().setDefended(true);
		frosty.setPendingMinionAttack(null);
		assertFalse(SantaTabs.underAttack(page));
		assertEquals("ACT I · Frosty", SantaTabs.tileLine(page, Instant.parse(page.getBoard().getNow())));
	}

	@Test
	public void no_event_hides_the_tile()
	{
		assertNull(SantaTabs.tileLine(null, Instant.now()));
	}

	@Test
	public void refusals_read_as_the_websites_reason()
	{
		assertEquals("You've already pressed Defend! on this one (3 / 25).", SantaTabs.problemText(GSON,
			"{\"title\":\"Already defending\",\"detail\":\"You've already pressed Defend! on this one (3 / 25).\"}"));
		assertEquals("Link your RSN first", SantaTabs.problemText(GSON, "{\"title\":\"Link your RSN first\"}"));
		assertEquals("Bad gateway", SantaTabs.problemText(GSON, "Bad gateway"));
	}

	@Test
	public void drop_categories_only_say_their_kind_when_the_name_doesnt()
	{
		SantaData.Rope clues = new SantaData.Rope();
		clues.setName("Beginner clues");
		clues.setKind("clue");
		assertEquals("Beginner clues", SantaTabs.ropeTitle(clues));
		SantaData.Rope raid = new SantaData.Rope();
		raid.setName("The Fractured Archive");
		raid.setKind("raid");
		assertEquals("The Fractured Archive (raid)", SantaTabs.ropeTitle(raid));
	}

	private static SantaData.Page sample()
	{
		try (InputStreamReader in = new InputStreamReader(SantaTabsTest.class.getResourceAsStream("santa-sample.json"), StandardCharsets.UTF_8))
		{
			return GSON.fromJson(in, SantaData.Page.class);
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
