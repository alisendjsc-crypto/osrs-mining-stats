package com.josiahcooper.miningstats;

import net.runelite.api.ItemID;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InventoryDeltaTest
{
	private static final int BIRD_NEST = 5070; // any non-ore item ID; just needs to not appear in Ores.

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
		assertTrue(InventoryDelta.oresGained(map(), map()).isEmpty());
	}

	@Test
	public void firstOrePickupReports()
	{
		List<Integer> gained = InventoryDelta.oresGained(
			map(),
			map(ItemID.COPPER_ORE, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), gained.get(0));
	}

	@Test
	public void stackGrowsByExactDelta()
	{
		List<Integer> gained = InventoryDelta.oresGained(
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
		List<Integer> gained = InventoryDelta.oresGained(
			map(ItemID.COPPER_ORE, 5),
			map(ItemID.COPPER_ORE, 2));
		assertTrue(gained.isEmpty());
	}

	@Test
	public void mixedOreDeltaCountsOnlyTheGainer()
	{
		List<Integer> gained = InventoryDelta.oresGained(
			map(ItemID.COPPER_ORE, 5),
			map(ItemID.COPPER_ORE, 5, ItemID.IRON_ORE, 2));
		assertEquals(2, gained.size());
		for (Integer id : gained)
		{
			assertEquals(Integer.valueOf(ItemID.IRON_ORE), id);
		}
	}

	@Test
	public void nonOreItemIgnoredEvenWhenAlongsideOre()
	{
		// Bird's nest drop alongside a successful copper swing — nest ignored, copper counted.
		List<Integer> gained = InventoryDelta.oresGained(
			map(ItemID.COPPER_ORE, 3),
			map(ItemID.COPPER_ORE, 4, BIRD_NEST, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), gained.get(0));
	}

	@Test
	public void nonOreOnlyReportsNothing()
	{
		List<Integer> gained = InventoryDelta.oresGained(
			map(),
			map(BIRD_NEST, 1));
		assertTrue(gained.isEmpty());
	}
}
