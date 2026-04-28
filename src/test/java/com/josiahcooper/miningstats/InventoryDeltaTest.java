package com.josiahcooper.miningstats;

import net.runelite.api.ItemID;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * v0.2.0: InventoryDelta is now a pure positive-diff over arbitrary item IDs. The
 * mining-discrimination role moved upstream to {@link MiningSuccessGate}; non-ore items are
 * no longer filtered here. Tests reflect that contract change.
 */
public class InventoryDeltaTest
{
	private static final int BIRD_NEST = 5070; // any non-ore item ID

	private static Map<Integer, Integer> map(Object... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((Integer) pairs[i], (Integer) pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void emptyToEmptyReportsNothing()
	{
		assertTrue(InventoryDelta.itemsGained(map(), map()).isEmpty());
	}

	@Test
	public void firstItemPickupReports()
	{
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(ItemID.COPPER_ORE, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), gained.get(0));
	}

	@Test
	public void stackGrowsByExactDelta()
	{
		List<Integer> gained = InventoryDelta.itemsGained(
			map(ItemID.COPPER_ORE, 5),
			map(ItemID.COPPER_ORE, 8));
		assertEquals(3, gained.size());
		for (Integer id : gained)
		{
			assertEquals(Integer.valueOf(ItemID.COPPER_ORE), id);
		}
	}

	@Test
	public void stackShrinksReportsNothing()
	{
		// Drop or bank — never count.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(ItemID.COPPER_ORE, 5),
			map(ItemID.COPPER_ORE, 2));
		assertTrue(gained.isEmpty());
	}

	@Test
	public void mixedDeltaCountsOnlyTheGainer()
	{
		List<Integer> gained = InventoryDelta.itemsGained(
			map(ItemID.COPPER_ORE, 5),
			map(ItemID.COPPER_ORE, 5, ItemID.IRON_ORE, 2));
		assertEquals(2, gained.size());
		for (Integer id : gained)
		{
			assertEquals(Integer.valueOf(ItemID.IRON_ORE), id);
		}
	}

	@Test
	public void nonOreItemNowAppearsInDiff()
	{
		// v0.2.0 contract: filtering moved upstream. A bird's nest gain is reported here;
		// MiningSuccessGate decides whether to discard it based on animation + XP coincidence.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(ItemID.COPPER_ORE, 3),
			map(ItemID.COPPER_ORE, 4, BIRD_NEST, 1));
		assertEquals(2, gained.size());
		assertTrue(gained.contains(ItemID.COPPER_ORE));
		assertTrue(gained.contains(BIRD_NEST));
	}

	@Test
	public void nonOreOnlyReportsTheItem()
	{
		// v0.2.0: previously filtered; now reported.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(BIRD_NEST, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(BIRD_NEST), gained.get(0));
	}

	// --- v0.3.0 hasNegativeDelta tests ---
	// Used by MiningSuccessGate's manual-banking filter. Must catch partial losses AND
	// items that disappear from the snapshot entirely.

	@Test
	public void hasNegativeDelta_emptyToEmpty_false()
	{
		assertFalse(InventoryDelta.hasNegativeDelta(map(), map()));
	}

	@Test
	public void hasNegativeDelta_gainOnly_false()
	{
		assertFalse(InventoryDelta.hasNegativeDelta(
			map(ItemID.COPPER_ORE, 3),
			map(ItemID.COPPER_ORE, 5)));
	}

	@Test
	public void hasNegativeDelta_partialLoss_true()
	{
		// Stack shrinks (manual partial deposit, drop, etc.).
		assertTrue(InventoryDelta.hasNegativeDelta(
			map(ItemID.COPPER_ORE, 28),
			map(ItemID.COPPER_ORE, 18)));
	}

	@Test
	public void hasNegativeDelta_itemDisappears_true()
	{
		// Item disappears entirely from the snapshot (e.g., deposit-all). The implementation
		// must iterate `before` keys, not `after`, to catch this.
		assertTrue(InventoryDelta.hasNegativeDelta(
			map(ItemID.COPPER_ORE, 5),
			map()));
	}

	@Test
	public void hasNegativeDelta_mixedGainAndLoss_true()
	{
		// Some items gained, some lost — ANY loss triggers the filter.
		assertTrue(InventoryDelta.hasNegativeDelta(
			map(ItemID.COPPER_ORE, 5, ItemID.IRON_ORE, 3),
			map(ItemID.COPPER_ORE, 8, ItemID.IRON_ORE, 1)));
	}
}
