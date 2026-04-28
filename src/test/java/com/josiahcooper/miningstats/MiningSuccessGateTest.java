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

	// --- v0.3.0 bank-watch tests ---
	// Detection contract widened to accept bank-positive deltas as a secondary item-source
	// for OSRS Leagues auto-bank/auto-smelt relics. Manual banking filtered via the
	// recent-inventory-negative paired-diff check.

	@Test
	public void bankPositiveWithXpAndAnimation_recordsLeaguesAutoDepositCase()
	{
		// The Leagues happy path: auto-bank relic routes ore directly to bank, never to
		// inventory. Animation + XP delta still co-tick on the swing.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(), 1000).isEmpty()); // bank baseline
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		List<Integer> emit = g.onBankChange(inv(ItemID.IRON_ORE, 1), 1200);
		assertEquals(1, emit.size());
		assertEquals(Integer.valueOf(ItemID.IRON_ORE), emit.get(0));
	}

	@Test
	public void firstObservedBankSnapshot_isBaselineNotGain()
	{
		// Q3 from the design investigation: counting the first-observed bank as gain would
		// turn hundreds of pre-existing items into a single bogus emit. Treat as baseline.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		// First bank event has 500 of an item id — must NOT emit anything.
		List<Integer> emit = g.onBankChange(inv(ItemID.COPPER_ORE, 500), 1200);
		assertTrue(emit.isEmpty());
	}

	@Test
	public void bankPositiveWithoutAnimation_doesNotRecord()
	{
		// Mirror of xpAndInventoryWithoutAnimation_doesNotRecord — animation gate is the
		// FP killer regardless of which item-source surface the gain came through.
		MiningSuccessGate g = new MiningSuccessGate();
		// Note: no recordMiningAnimation.
		assertTrue(g.onBankChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		assertTrue(g.onBankChange(inv(ItemID.IRON_ORE, 1), 1200).isEmpty());
	}

	@Test
	public void bankPositivePairedWithRecentInventoryNegative_isManualBankingAndIgnored()
	{
		// Manual deposit-all sequence:
		//   inv ore -3 (deposited)
		//   bank ore +3 (received)
		// The paired diff must be rejected even though animation gate is active and XP delta
		// is timely (player banked within 3 seconds of last swing — common during fast trips).
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 3), 1000).isEmpty()); // inv baseline
		assertTrue(g.onBankChange(inv(), 1000).isEmpty()); // bank baseline
		assertTrue(g.onMiningXpDelta(1050).isEmpty());
		// inv-negative: deposit-all empties inventory.
		assertTrue(g.onInventoryChange(inv(), 1100).isEmpty());
		// bank-positive arrives within window — must be rejected.
		assertTrue(g.onBankChange(inv(ItemID.COPPER_ORE, 3), 1150).isEmpty());
	}

	@Test
	public void bankPositiveAfterInventoryNegativeAgesOut_doesEmit()
	{
		// Boundary: inv-negative happened > xpCoincidenceWindowMs ago. Subsequent bank-positive
		// is a genuine auto-deposit (player happened to bank earlier in the session).
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(5000);
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 3), 5000).isEmpty());
		assertTrue(g.onBankChange(inv(), 5000).isEmpty());
		// Manual banking happens at t=5100.
		assertTrue(g.onInventoryChange(inv(), 5100).isEmpty());
		assertTrue(g.onBankChange(inv(ItemID.COPPER_ORE, 3), 5150).isEmpty()); // rejected (paired)
		// Player resumes mining; auto-deposit fires later, OUTSIDE the manual-banking window.
		g.recordMiningAnimation(7000);
		assertTrue(g.onMiningXpDelta(7100).isEmpty());
		List<Integer> emit = g.onBankChange(inv(ItemID.COPPER_ORE, 4), 7200); // +1 from t=5150 baseline of 3
		assertEquals(1, emit.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), emit.get(0));
	}

	@Test
	public void bankPositiveBeforeXpDelta_isHeldThenEmittedOnXpDelta()
	{
		// Symmetry with inventoryThenXpWithinWindowAndAnimation_records but for the bank surface.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(), 1000).isEmpty()); // bank baseline
		assertTrue(g.onBankChange(inv(ItemID.IRON_ORE, 1), 1100).isEmpty()); // held
		List<Integer> emit = g.onMiningXpDelta(1200);
		assertEquals(1, emit.size());
		assertEquals(Integer.valueOf(ItemID.IRON_ORE), emit.get(0));
	}

	@Test
	public void multiTickAutoDeposit_emitsOnePerSwing()
	{
		// Three consecutive swings, each auto-deposits one ore. Each pairs with its own XP
		// delta; total emit count = 3.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(), 1000).isEmpty());
		// Swing 1
		assertTrue(g.onMiningXpDelta(1050).isEmpty());
		List<Integer> e1 = g.onBankChange(inv(ItemID.IRON_ORE, 1), 1100);
		assertEquals(1, e1.size());
		// Swing 2 (animation gate still active, fresh XP delta)
		assertTrue(g.onMiningXpDelta(1700).isEmpty());
		List<Integer> e2 = g.onBankChange(inv(ItemID.IRON_ORE, 2), 1750);
		assertEquals(1, e2.size());
		// Swing 3
		assertTrue(g.onMiningXpDelta(2300).isEmpty());
		List<Integer> e3 = g.onBankChange(inv(ItemID.IRON_ORE, 3), 2350);
		assertEquals(1, e3.size());
	}

	@Test
	public void bankPositiveWithoutXpDelta_isHeldAndAgesOut()
	{
		// Symmetry with inventoryDeltaWithoutXpDelta_doesNotRecord.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(), 1000).isEmpty());
		// Bank-positive with no recent XP delta — held, not emitted.
		assertTrue(g.onBankChange(inv(ItemID.IRON_ORE, 1), 1100).isEmpty());
		// XP delta arrives outside window; held diff already aged out via the held-diff check
		// inside onBankChange's next call OR via onMiningXpDelta's heldDiffWithinWindow guard.
		assertTrue(g.onMiningXpDelta(2700).isEmpty());
	}

	@Test
	public void bankXpDeltaConsumed_doesNotDoubleFireOnSecondBankEvent()
	{
		// Mirror of xpDeltaConsumed_doesNotDoubleFireOnSecondInventoryEvent for bank surface.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		List<Integer> emit1 = g.onBankChange(inv(ItemID.IRON_ORE, 1), 1200);
		assertEquals(1, emit1.size());
		// Second bank event with no fresh XP delta — must NOT fire.
		assertTrue(g.onBankChange(inv(ItemID.IRON_ORE, 1, BIRD_NEST, 1), 1300).isEmpty());
	}

	@Test
	public void resetInventoryBaseline_alsoClearsBankSnapshot()
	{
		// On disconnect/login, the bank baseline must reset alongside inventory so the post-login
		// snapshot doesn't get diffed against pre-logout state (which could falsely emit
		// hundreds of items).
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(ItemID.IRON_ORE, 100), 1000).isEmpty()); // bank baseline = 100 iron
		g.resetInventoryBaseline();
		// Post-reset: bank with 100 iron should be re-baseline (no emit), not a +100 gain.
		// XP delta in window to make sure the test isn't passing for the wrong reason.
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		List<Integer> emit = g.onBankChange(inv(ItemID.IRON_ORE, 100), 1200);
		assertTrue("post-reset first bank event must be baseline, not gain", emit.isEmpty());
	}

	@Test
	public void mixedInventoryAndBankBaselines_independentOfEachOther()
	{
		// Plugin can observe inv events well before the first bank event arrives (player mines
		// without ever opening or filling the bank). When bank is finally observed, it must
		// baseline independently of the already-running inventory tracking.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		// Inventory baseline + first paired emit happens before bank is ever observed.
		assertTrue(g.onInventoryChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1050).isEmpty());
		List<Integer> e1 = g.onInventoryChange(inv(ItemID.COPPER_ORE, 1), 1100);
		assertEquals(1, e1.size());
		// Now bank is observed for the first time mid-session — must be baseline, not gain.
		assertTrue(g.onMiningXpDelta(1200).isEmpty());
		assertTrue(g.onBankChange(inv(ItemID.COPPER_ORE, 50), 1250).isEmpty()); // baseline only
	}

	@Test
	public void partialDeposit_isRejectedAsManualBanking()
	{
		// Player has 28 ore in inventory, right-click → "Deposit 10". Inventory: ore -10.
		// Bank: ore +10. The paired-diff filter must catch this even though only some of the
		// stack moved (i.e., the inventory still has remaining ore).
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 28), 1000).isEmpty());
		assertTrue(g.onBankChange(inv(), 1000).isEmpty());
		assertTrue(g.onMiningXpDelta(1050).isEmpty());
		assertTrue(g.onInventoryChange(inv(ItemID.COPPER_ORE, 18), 1100).isEmpty()); // -10 inv-negative
		// Bank +10 must be rejected.
		assertTrue(g.onBankChange(inv(ItemID.COPPER_ORE, 10), 1150).isEmpty());
	}

	@Test
	public void bankAllZerosAfterBaseline_doesNotEmit()
	{
		// Edge: bank container becomes empty (e.g., player withdraws everything). No positive
		// diff exists, so no emit. Past code path that iterated only over current.entrySet()
		// would correctly compute an empty itemsGained; the held-diff branch must not fire.
		MiningSuccessGate g = new MiningSuccessGate();
		g.recordMiningAnimation(1000);
		assertTrue(g.onBankChange(inv(ItemID.COPPER_ORE, 50), 1000).isEmpty()); // baseline = 50 copper
		assertTrue(g.onMiningXpDelta(1100).isEmpty());
		// Bank empties (player withdrew). No positive diff — no emit. XP delta remains buffered.
		assertTrue(g.onBankChange(inv(), 1200).isEmpty());
		// Verify the XP delta is still consumable by a subsequent legitimate bank gain.
		assertTrue(g.onBankChange(inv(ItemID.COPPER_ORE, 1), 1300).size() == 1);
	}
}
