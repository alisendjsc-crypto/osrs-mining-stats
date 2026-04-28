package com.josiahcooper.miningstats;

import net.runelite.api.ItemID;
import net.runelite.api.gameval.AnimationID;
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

	/**
	 * v0.3.1 regression guard. v0.2.0 + v0.3.0 imported from
	 * {@code net.runelite.api.AnimationID} which only exposes floor-mining variants
	 * (e.g. adamant = 628). Wall-mounted rocks like Camdozaal barronite emit the
	 * {@code _WALL} variant (adamant wall = 6756), which the gameval namespace exposes
	 * but the legacy namespace did not. v0.3.1 migrates to gameval and includes the
	 * full variant lineup. Hardcoding the canonical Jagex animation IDs here so any
	 * future namespace shuffling that loses a variant fails the test loudly.
	 */
	@Test
	public void wallMountedRockAnimations_areRecognized()
	{
		// The load-bearing fix: adamant wall variant. Without this, Camdozaal barronite
		// mining never activates the gate. Same shape as the prod regression Josiah hit.
		assertTrue("HUMAN_MINING_ADAMANT_PICKAXE_WALL (6756) — v0.3.1 fix's anchor",
			MiningAnimations.isMiningAnimation(6756));

		// Sanity: the floor variants we already had still resolve. Stable IDs from
		// gameval AnimationID — these are Jagex internal cache IDs, fixed by the game data.
		assertTrue("HUMAN_MINING_ADAMANT_PICKAXE (628)",
			MiningAnimations.isMiningAnimation(628));
		assertTrue("HUMAN_MINING_RUNE_PICKAXE (624)",
			MiningAnimations.isMiningAnimation(624));
		assertTrue("HUMAN_MINING_DRAGON_PICKAXE (7139)",
			MiningAnimations.isMiningAnimation(7139));

		// Representative coverage of other wall variants — Trahaearn mine, Prifddinas
		// mine, anywhere with vertical rock walls would emit one of these per pickaxe.
		assertTrue("HUMAN_MINING_RUNE_PICKAXE_WALL (6752)",
			MiningAnimations.isMiningAnimation(6752));
		assertTrue("HUMAN_MINING_BRONZE_PICKAXE_WALL (6753)",
			MiningAnimations.isMiningAnimation(6753));
		assertTrue("HUMAN_MINING_IRON_PICKAXE_WALL (6754)",
			MiningAnimations.isMiningAnimation(6754));
		assertTrue("HUMAN_MINING_STEEL_PICKAXE_WALL (6755)",
			MiningAnimations.isMiningAnimation(6755));
		assertTrue("HUMAN_MINING_MITHRIL_PICKAXE_WALL",
			MiningAnimations.isMiningAnimation(AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL));

		// No-reach-forward variant — alternate orientation handled by Jagex for some
		// rock geometries. Same coverage gap as wall variants in pre-0.3.1.
		assertTrue("HUMAN_MINING_ADAMANT_PICKAXE_NOREACHFORWARD (6750)",
			MiningAnimations.isMiningAnimation(6750));

		// Power-swing variant — rare swing animation used in some special mining
		// activities. Included for completeness.
		assertTrue("PICKAXE_POWER_SWING_ADAMANT",
			MiningAnimations.isMiningAnimation(AnimationID.PICKAXE_POWER_SWING_ADAMANT));
	}

	/**
	 * Membership-count sanity check. v0.3.1 set should be substantially larger than
	 * v0.3.0's 13 entries — we now mirror the standard Mining plugin's coverage.
	 * If a future rebase accidentally drops half the entries, this test fails loudly
	 * before reaching a player.
	 */
	@Test
	public void miningAnimationsSetCoversAllPickaxeTiers()
	{
		// Lower bound — 13 was v0.3.0's value; we expect at least 60 with full variant
		// coverage across all pickaxe tiers (~17 tiers × ~4 variants each, minus a few
		// missing power-swing variants on cosmetic pickaxes).
		assertTrue("Expected substantially expanded animation set in v0.3.1+ (size: "
				+ MiningAnimations.all().size() + ")",
			MiningAnimations.all().size() >= 60);
	}
}
