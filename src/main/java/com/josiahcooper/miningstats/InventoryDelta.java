package com.josiahcooper.miningstats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure helper for computing positive item-quantity deltas between two inventory snapshots.
 *
 * <p>Decoupled from RuneLite's {@code ItemContainer} type so the diff math can be unit-tested
 * without mocking the client. The plugin's event handler converts {@code ItemContainer} into
 * a {@code Map<Integer, Integer>} of itemId → quantity and hands it here.
 *
 * <p>Negative deltas (drops, banking, trades) are ignored. Items appear once per unit gained,
 * so a +3 copper delta yields three entries.
 *
 * <p><strong>v0.3.2 contract:</strong> a curated blacklist of categorically-non-mining item
 * IDs is filtered out before the gain list is returned. This is a NEGATIVE filter — anything
 * not on the list still passes through, preserving v0.2.0's "future Jagex yield won't silently
 * zero-rate" property. The blacklist exists because OSRS Leagues coin-drop and smithing relics
 * empirically corrupted v0.3.1's Ores/hr math (~375k coins/hr counted as ores) and collapsed
 * inventory ETA to 0s. Excluded: coins, all furnace bars, uncut gems from mining, clue
 * geodes/scrolls. Whitelist (per-activity ore-item tables) is the semantically cleaner answer
 * but requires activity-detection plumbing — defer to v0.4.0+ if blacklist proves leaky.
 *
 * <p><strong>v0.3.2 revert:</strong> {@code hasNegativeDelta} was removed alongside the bank
 * handler. The manual-banking filter it served was load-bearing only for {@code onBankChange},
 * which v0.3.2 reverts after the Endless Harvest test on Leagues empirically confirmed
 * bank events do not fire while the bank UI is closed — the architecture's load-bearing
 * assumption was false.
 */
public final class InventoryDelta
{
	/**
	 * Item IDs that are inventory-additive but never mining yields. Verified against
	 * named identifiers below; numeric values are the canonical OSRS item IDs and will not
	 * change across RuneLite client versions.
	 *
	 * <p>If a future content drop introduces a new auto-add inventory item that should not
	 * count as ore (e.g., a new relic-driven bonus), append its ID here. The cost of a leaky
	 * blacklist is small bias on Total/Ores; the cost of a leaky whitelist is silently
	 * zero-rated new yields. The trade is intentional.
	 */
	private static final Set<Integer> YIELD_BLACKLIST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		// Currency
		995,    // Coins (Endless Harvest / coin-drop relic catastrophe — see v0.3.2 entry)

		// Smithing-relic bars (auto-smelted ore appears as bar, not ore)
		2349,   // Bronze bar
		2351,   // Iron bar
		2353,   // Steel bar
		2355,   // Silver bar
		2357,   // Gold bar
		2359,   // Mithril bar
		2361,   // Adamant bar
		2363,   // Rune bar

		// Uncut gems from random gem drops while mining
		1617,   // Uncut diamond
		1619,   // Uncut ruby
		1621,   // Uncut emerald
		1623,   // Uncut sapphire
		1631,   // Uncut dragonstone

		// Clue geodes (random drops while mining; reward-track item, not a yield)
		19887,  // Clue geode (easy)
		19888,  // Clue geode (medium)
		19889,  // Clue geode (hard)
		19891,  // Clue geode (elite)

		// Clue scrolls + scroll box (Leagues beginner-tier reward path)
		2677,   // Clue scroll (easy)
		2801,   // Clue scroll (medium)
		2722,   // Clue scroll (hard)
		12073,  // Clue scroll (elite)
		23182,  // Clue scroll (beginner)
		26739   // Scroll box (beginner)
	)));

	private InventoryDelta()
	{
	}

	/**
	 * Compute item-IDs gained between {@code before} and {@code after}, excluding entries
	 * matched by the v0.3.2 yield blacklist.
	 */
	public static List<Integer> itemsGained(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		List<Integer> gained = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : after.entrySet())
		{
			int itemId = entry.getKey();
			if (YIELD_BLACKLIST.contains(itemId))
			{
				continue;
			}
			int delta = entry.getValue() - before.getOrDefault(itemId, 0);
			for (int i = 0; i < delta; i++)
			{
				gained.add(itemId);
			}
		}
		return gained;
	}

	/** Test-visible accessor for the blacklist contents. Package-private. */
	static Set<Integer> yieldBlacklist()
	{
		return YIELD_BLACKLIST;
	}
}
