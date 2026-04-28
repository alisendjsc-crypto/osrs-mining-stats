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
 * v0.3.2 contract: pure positive-diff over arbitrary item IDs, with a curated blacklist of
 * categorically-non-mining items filtered out before return. Mining-discrimination by timing
 * (animation + XP coincidence) remains the {@link MiningSuccessGate}'s responsibility; the
 * blacklist here is a NEGATIVE filter — anything not on it still passes through, preserving
 * v0.2.0's "future Jagex yield won't silently zero-rate" property.
 */
public class InventoryDeltaTest
{
	private static final int BIRD_NEST = 5070;     // non-ore but NOT in the v0.3.2 blacklist; still reported

	private static final int COINS = 995;
	private static final int BRONZE_BAR = 2349;
	private static final int RUNE_BAR = 2363;
	private static final int UNCUT_DIAMOND = 1617;
	private static final int UNCUT_SAPPHIRE = 1623;
	private static final int CLUE_GEODE_EASY = 19887;
	private static final int CLUE_SCROLL_BEGINNER = 23182;
	private static final int SCROLL_BOX_BEGINNER = 26739;

	private static Map<Integer, Integer> map(Object... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((Integer) pairs[i], (Integer) pairs[i + 1]);
		}
		return m;
	}

	// --- Core positive-diff semantics ---

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
	public void nonBlacklistedNonOreItemStillAppearsInDiff()
	{
		// v0.3.2 contract: the blacklist is narrow (categorical non-mining-yields). Other
		// non-ore items (bird's nest, future Jagex content) still pass through; the gate
		// decides whether to discard based on animation + XP coincidence.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(ItemID.COPPER_ORE, 3),
			map(ItemID.COPPER_ORE, 4, BIRD_NEST, 1));
		assertEquals(2, gained.size());
		assertTrue(gained.contains(ItemID.COPPER_ORE));
		assertTrue(gained.contains(BIRD_NEST));
	}

	// --- v0.3.2 blacklist coverage ---
	// Each excluded category gets a fixture mirroring the empirical Leagues case: a plausible
	// mining-time inventory delta containing both blacklisted items and a real ore. Only the
	// real ore should increment the yield list.

	@Test
	public void coinsAreBlacklisted_leaguesEndlessHarvestCase()
	{
		// Reproduces the FlowersOEvil prod observation: coin-drop relic produces ~1000 coins
		// per swing alongside a single ore. Pre-v0.3.2 inflated Ores/hr to ~375k.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(COINS, 1000, ItemID.COAL, 1));
		assertEquals("only the coal should count, not the 1000 coins",
			1, gained.size());
		assertEquals(Integer.valueOf(ItemID.COAL), gained.get(0));
	}

	@Test
	public void barsAreBlacklisted_leaguesAutoSmeltCase()
	{
		// Smithing relic auto-converts ore to bar in inventory; bar is not the mining yield.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(BRONZE_BAR, 1, RUNE_BAR, 1, ItemID.MITHRIL_ORE, 1));
		assertEquals("only the mithril ore should count",
			1, gained.size());
		assertEquals(Integer.valueOf(ItemID.MITHRIL_ORE), gained.get(0));
	}

	@Test
	public void uncutGemsAreBlacklisted()
	{
		// Random gem drops while mining (~0.4%/rock); reward, not a mining yield.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(UNCUT_DIAMOND, 1, UNCUT_SAPPHIRE, 1, ItemID.IRON_ORE, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(ItemID.IRON_ORE), gained.get(0));
	}

	@Test
	public void clueGeodesAreBlacklisted()
	{
		// Random clue geode drops while mining (~1%/rock).
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(CLUE_GEODE_EASY, 1, ItemID.COPPER_ORE, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), gained.get(0));
	}

	@Test
	public void clueScrollsAndScrollBoxAreBlacklisted()
	{
		// Beginner scroll box specifically targets the Leagues observation; broader clue
		// scroll IDs cover any future cross-content interaction.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(CLUE_SCROLL_BEGINNER, 1, SCROLL_BOX_BEGINNER, 1, ItemID.COPPER_ORE, 1));
		assertEquals(1, gained.size());
		assertEquals(Integer.valueOf(ItemID.COPPER_ORE), gained.get(0));
	}

	@Test
	public void purelyBlacklistedDeltaReportsNothing()
	{
		// The Leagues catastrophe-without-mining case: only coins + a bar arrive. No mining
		// yield → empty list → upstream gate sees diff.isEmpty() and skips the held-diff
		// machinery entirely. Critical: prevents blacklisted items from consuming the
		// buffered XP delta and starving subsequent real ore events.
		List<Integer> gained = InventoryDelta.itemsGained(
			map(),
			map(COINS, 500, BRONZE_BAR, 1));
		assertTrue("blacklisted-only diff must produce empty gain list",
			gained.isEmpty());
	}

	@Test
	public void blacklistContentsAreTheExpectedSet()
	{
		// Smoke test catching accidental removal — if a future edit drops a category, this
		// fails fast before behavior bugs ship to prod.
		java.util.Set<Integer> bl = InventoryDelta.yieldBlacklist();
		assertTrue("coins must be blacklisted", bl.contains(COINS));
		assertTrue("bronze bar must be blacklisted", bl.contains(BRONZE_BAR));
		assertTrue("rune bar must be blacklisted", bl.contains(RUNE_BAR));
		assertTrue("uncut diamond must be blacklisted", bl.contains(UNCUT_DIAMOND));
		assertTrue("clue geode (easy) must be blacklisted", bl.contains(CLUE_GEODE_EASY));
		assertTrue("scroll box (beginner) must be blacklisted", bl.contains(SCROLL_BOX_BEGINNER));
		// Negative: bird's nest (a non-ore but legitimate random drop in some contexts) is NOT
		// on the list — we don't want to over-filter and risk silently zero-rating real yields.
		assertFalse("bird's nest must NOT be blacklisted", bl.contains(BIRD_NEST));
		assertFalse("copper ore must NOT be blacklisted", bl.contains(ItemID.COPPER_ORE));
	}
}
