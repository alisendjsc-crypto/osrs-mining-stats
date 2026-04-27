package com.josiahcooper.miningstats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure helper for computing positive item-quantity deltas between two inventory snapshots.
 *
 * <p>Decoupled from RuneLite's {@code ItemContainer} type so the diff math can be unit-tested
 * without mocking the client. The plugin's event handler converts {@code ItemContainer} into
 * a {@code Map<Integer, Integer>} of itemId → quantity and hands it here.
 *
 * <p>Negative deltas (drops, banking, trades) are ignored. <strong>v0.2.0 contract change:</strong>
 * non-ore item IDs are no longer filtered here — the upstream {@link MiningSuccessGate} is now
 * responsible for deciding whether a given diff was caused by mining (via animation +
 * Mining-XP-delta coincidence). Any item gained appears in the returned list. Each item
 * appears once per unit gained, so a +3 copper delta yields three entries.
 */
public final class InventoryDelta
{
	private InventoryDelta()
	{
	}

	/**
	 * Compute item-IDs gained between {@code before} and {@code after}. No filtering by item type.
	 */
	public static List<Integer> itemsGained(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		List<Integer> gained = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : after.entrySet())
		{
			int itemId = entry.getKey();
			int delta = entry.getValue() - before.getOrDefault(itemId, 0);
			for (int i = 0; i < delta; i++)
			{
				gained.add(itemId);
			}
		}
		return gained;
	}
}
