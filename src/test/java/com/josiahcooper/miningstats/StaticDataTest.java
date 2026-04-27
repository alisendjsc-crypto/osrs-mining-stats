package com.josiahcooper.miningstats;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Light sanity coverage for the static lookup tables. The compile step itself is the primary
 * verification (constant names must resolve against the RuneLite API); these tests just
 * confirm the lookup wiring is correct.
 */
public class StaticDataTest
{
	@Test
	public void everyOreRoundTripsByItemId()
	{
		for (Ores o : Ores.values())
		{
			assertEquals(o, Ores.fromItemId(o.itemId()));
			assertTrue(Ores.isOre(o.itemId()));
			assertEquals(o.displayName(), Ores.displayName(o.itemId()));
		}
	}

	@Test
	public void unknownItemIdReturnsNullAndUnknownLabel()
	{
		assertNull(Ores.fromItemId(-999));
		assertFalse(Ores.isOre(-999));
		assertTrue(Ores.displayName(-999).startsWith("Unknown"));
	}

	@Test
	public void miningAnimationsSetIsNonEmptyAndRejectsNonMembers()
	{
		assertFalse(MiningAnimations.all().isEmpty());
		// Idle animation (-1) is never a mining swing.
		assertFalse(MiningAnimations.isMiningAnimation(-1));
		// Arbitrary non-mining animation (woodcutting bronze axe ~= 879) shouldn't match.
		assertFalse(MiningAnimations.isMiningAnimation(879));
	}

	@Test
	public void miningAnimationMembershipIsConsistent()
	{
		for (Integer id : MiningAnimations.all())
		{
			assertTrue("Expected " + id + " to be reported as a mining animation",
				MiningAnimations.isMiningAnimation(id));
		}
	}
}
