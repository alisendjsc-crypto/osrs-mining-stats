package com.josiahcooper.miningstats;

import net.runelite.api.ItemID;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

	/**
	 * v0.1.1 regression test. The v0.1.0 release shipped without Barronite shards in the enum,
	 * causing Camdozaal mining to register Mining XP but zero Ores/hr. Pinning by item ID 25676
	 * so a future refactor that drops the entry trips this test by name.
	 */
	@Test
	public void barroniteShardsAreRecognizedAsOre()
	{
		assertTrue("BARRONITE_SHARDS must be an ore (v0.1.1 fix)",
			Ores.isOre(ItemID.BARRONITE_SHARDS));
		assertEquals(Ores.BARRONITE_SHARDS, Ores.fromItemId(ItemID.BARRONITE_SHARDS));
		assertEquals("Barronite shards", Ores.displayName(ItemID.BARRONITE_SHARDS));
	}

	/**
	 * Gem-rock yields (Lunar Isle, Shilo Village) — added in v0.1.1. All seven gem variants
	 * mine-able from gem rocks must register as ores. Onyx is intentionally excluded
	 * (TzHaar/Trahaearn-only, not a Mining yield).
	 */
	@Test
	public void gemRockYieldsAreRecognized()
	{
		List<Integer> gemItemIds = Arrays.asList(
			ItemID.UNCUT_SAPPHIRE,
			ItemID.UNCUT_EMERALD,
			ItemID.UNCUT_RUBY,
			ItemID.UNCUT_DIAMOND,
			ItemID.UNCUT_OPAL,
			ItemID.UNCUT_JADE,
			ItemID.UNCUT_RED_TOPAZ
		);
		for (int id : gemItemIds)
		{
			assertTrue("Gem item " + id + " must be a recognized ore", Ores.isOre(id));
			assertNotNull(Ores.fromItemId(id));
		}
	}

	/**
	 * Catches copy-paste bugs where two enum entries point at the same item ID (the second
	 * would silently overwrite the first in BY_ID).
	 */
	@Test
	public void noDuplicateItemIdsAcrossEnum()
	{
		Set<Integer> seen = new HashSet<>();
		for (Ores o : Ores.values())
		{
			assertTrue("Duplicate itemId " + o.itemId() + " on " + o.name(),
				seen.add(o.itemId()));
		}
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
