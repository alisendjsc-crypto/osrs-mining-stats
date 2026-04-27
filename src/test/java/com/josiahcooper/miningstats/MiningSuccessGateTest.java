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
 * v0.2.0 Path B detection contract: animation + Mining-XP-delta coincidence with inventory
 * increment, no item-ID enum gate. Each test simulates the event sequence with explicit
 * timestamps; the gate accepts {@code now} as a parameter so {@code System.currentTimeMillis}
 * is not in the test's path.
 */
public class MiningSuccessGateTest
{
	private static final int BIRD_NEST = 5070;          // not in Ores
	private static final int RUBBER_DUCK = 6720;        // arbitrary non-ore
	private static final int UNKNOWN_FUTURE_YIELD = 99999; // would-be future Jagex yield

	private static Map<Integer, Integer> inv(Object... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((Integer) pairs[i], (Integer) pairs[i + 1]);
		}
		return m;
	}

	// --- Core coincidence tests (Test 1 through Test 6 from the design doc) ---

	@Test
	public void xpThenInventoryWithinWindowAndAnimation_records()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty()); // baseline
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		List<Integer> emit = g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1500);
		assertEquals(1, emit.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), emit.get(0));
	}

	@Test
	public void inventoryDeltaWithoutXpDelta_doesNotRecord()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		// Inventory event with no recent XP delta — buffered, not emitted.
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1100).isEmpty());
		// After window passes, buffer ages out without ever firing.
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 3000).isEmpty());
	}

	@Test
	public void xpDeltaWithoutInventory_doesNotRecord()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		assertTrue(g.onMiningXpDelta(1500).isEmpty());
		assertTrue(g.onMiningXpDelta(2000).isEmpty());
	}

	@Test
	public void nonEnumItemIdRecordsCorrectly_provesEnumDemotion()
	{
		// This is the headline win of Path B: items not in Ores still get counted, so the
		// next Jagex content drop doesn't silently zero-rate like Barronite shards did in v0.1.0.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		List<Integer> emit = g.onInventoryChange(inv(UNKNOWN_FUTURE_YIELD, 1), 1500);
		assertEquals(1, emit.size());
		assertEquals(Integer.valueOf(UNKNOWN_FUTURE_YIELD), emit.get(0));
		assertFalse("test assumes ID isn't in Ores", Ores.isOre(UNKNOWN_FUTURE_YIELD));
	}

	@Test
	public void xpAndInventoryWithoutAnimation_doesNotRecord()
	{
		// Animation gate is the Wintertodt-FP killer: brazier repair grants Mining XP via
		// hammer animation (not in MiningAnimations), so even if a coincident inventory event
		// happened, the gate must reject it.
		MiningSuccessGate g = new MiningSuccessGate();
		// Note: no recordMiningAnimation call.
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1500).isEmpty());
	}

	@Test
	public void inventoryThenXpWithinWindowAndAnimation_records()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty()); // baseline
		// Inventory comes first; held.
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1100).isEmpty());
		// XP delta inside window pairs with held diff.
		List<Integer> emit = g.onMiningXpDelta(1200);
		assertEquals(1, emit.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), emit.get(0));
	}

	// --- Edge cases protecting non-obvious behavior ---

	@Test
	public void heldDiffAgesOut_xpDeltaBeyondWindowDoesNotEmit()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1100).isEmpty()); // held
		// XP delta arrives 1500ms later — outside 1200ms window.
		assertTrue(g.onMiningXpDelta(2700).isEmpty());
	}

	@Test
	public void xpDeltaConsumed_doesNotDoubleFireOnSecondInventoryEvent()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		// First inventory event consumes the buffered XP delta.
		List<Integer> emit1 = g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1200);
		assertEquals(1, emit1.size());
		// Second inventory event with no fresh XP delta should NOT fire.
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1, BIRD_NEST, 1), 1300).isEmpty());
	}

	@Test
	public void multiUnitGain_yieldsOneEntryPerUnit()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 5), 1000).isEmpty()); // baseline
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		List<Integer> emit = g.onInventoryChange(inv(ItemID.COPPER_ORE, 8), 1200);
		assertEquals(3, emit.size());
		for (Integer id : emit)
		{
			assertEquals(Integer.valueOf(ItemID.COPPER_ORE), id);
		}
	}

	@Test
	public void backToBackInventoryEventsBeforeXp_mergeIntoSingleEmit()
	{
		// Tick-jitter scenario: two swings' inventory events arrive before either's XP delta.
		// Merge behavior prevents the earlier swing's record from being silently dropped.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty()); // baseline
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1100).isEmpty()); // hold COPPER
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1, ItemID.IRON_ORE, 1), 1300).isEmpty()); // append IRON
		List<Integer> emit = g.onMiningXpDelta(1400);
		assertEquals(2, emit.size());
		assertTrue(emit.contains(ItemID.COPPER_ORE));
		assertTrue(emit.contains(ItemID.IRON_ORE));
	}

	@Test
	public void resetInventoryBaseline_clearsHeldDiff()
	{
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1100).isEmpty()); // hold
		g.resetInventoryBaseline();
		// After reset, the held diff is gone — XP delta should not retroactively emit.
		assertTrue(g.onMiningXpDelta(1200).isEmpty());
	}

	@Test
	public void animationGateActiveCheck_reflectsRecency()
	{
		MiningSuccessGate g = new MiningSuccessGate(3000L, 1200L);
		assertFalse(g.isAnimationGateActive(1000));
		g.recordMiningAnimation(1000);
		assertTrue(g.isAnimationGateActive(1000));
		assertTrue(g.isAnimationGateActive(3500));   // 2500ms < 3000ms gate
		assertFalse(g.isAnimationGateActive(4500));  // 3500ms > 3000ms gate
	}

	@Test
	public void droppedItemsIgnored_evenWithRecentXpAndAnimation()
	{
		// Stack shrinks (player drops ore mid-session) → not a positive diff → no record.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 5), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 2), 1200).isEmpty());
	}
}
