package com.asgardians;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class DeathTest
{
	private static final Death.Held TBOW = new Death.Held(20997, "Twisted bow", 1, 1_400_000_000);
	private static final Death.Held SHARKS = new Death.Held(385, "Shark", 10, 900);
	private static final Death.Held BRACE = new Death.Held(11133, "Regen bracelet", 1, 5_000_000);
	private static final Death.Held DARTS = new Death.Held(11230, "Dragon dart", 500, 3_000);

	@Test
	public void keepsThreeMostValuableSingleItems()
	{
		Death.Split split = Death.split(List.of(SHARKS, TBOW, DARTS, BRACE), 3);
		// Twisted bow, Regen bracelet, then one dragon dart.
		assertEquals(3, split.getKept().size());
		assertEquals(1, split.getKept().get(2).getQty());
		assertEquals(499L * 3_000 + 10L * 900, split.lostValue());
	}

	@Test
	public void skulledLosesEverything()
	{
		Death.Split split = Death.split(List.of(TBOW, SHARKS), 0);
		assertTrue(split.getKept().isEmpty());
		assertEquals(1_400_000_000L + 9_000, split.lostValue());
	}

	@Test
	public void bondsAreAlwaysKeptWithoutUsingASlot()
	{
		Death.Held bond = new Death.Held(ItemID.OSRS_BOND, "Old school bond", 1, 9_000_000);
		Death.Split split = Death.split(List.of(bond, TBOW, SHARKS), 1);
		assertEquals(2, split.getKept().size());
		assertEquals(9_000, split.lostValue());
	}

	@Test
	public void knownSafeAreas()
	{
		assertEquals("Fortis Colosseum", Death.PLACES.get(7216).getName());
		assertTrue(Death.PLACES.get(9043).isSafe());
		assertEquals("Theatre of Blood", Death.PLACES.get(12613).getName());
	}
}
