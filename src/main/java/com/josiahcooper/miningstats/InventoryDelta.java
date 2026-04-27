package com.josiahcooper.miningstats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure helper for computing ore-item deltas between two inventory snapshots.
 *
 * <p>Decoupled from RuneLite's {@code ItemContainer} type so the diff logic can be unit-tested
 * without mocking the client. The plugin's event handler converts {@code ItemContainer} into
 * a {@code Map<Integer, Integer>} of itemId → quantity and hands it here.
 *
 * <p>Negative deltas (drops, banking, trades) are ignored. Non-ore item IDs are ignored.
 * Each ore appears in the returned list once per unit gained, so a +3 copper delta yields three
 * entries — convenient for feeding {@code RollingWindow.recordEvent} in a loop.
 */
public final class InventoryDelta
{
	private InventoryDelta()
	{
	}

	public static List<Integer> oresGained(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		List<Integer> gained = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : after.entrySet())
		{
			int itemId = entry.getKey();
			if (!Ores.isOre(itemId))
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
}
