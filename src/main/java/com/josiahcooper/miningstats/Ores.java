package com.josiahcooper.miningstats;

import net.runelite.api.ItemID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mineable ore item IDs and their display names.
 *
 * <p>Conservative initial coverage — the standard pickaxe-mineable ore lineup plus essence and
 * the common quest-relevant ores. Extras (granite, sandstone, basalt, lovakite, lunar, daeyalt,
 * barronite, luminite, etc.) intentionally omitted on the first pass; add as needed when the
 * SPEC's per-ore overlay is exercised on real gameplay and we confirm constant names against
 * the API the build resolves to.
 */
public enum Ores
{
	COPPER     (ItemID.COPPER_ORE,     "Copper"),
	TIN        (ItemID.TIN_ORE,        "Tin"),
	IRON       (ItemID.IRON_ORE,       "Iron"),
	COAL       (ItemID.COAL,           "Coal"),
	SILVER     (ItemID.SILVER_ORE,     "Silver"),
	GOLD       (ItemID.GOLD_ORE,       "Gold"),
	MITHRIL    (ItemID.MITHRIL_ORE,    "Mithril"),
	ADAMANTITE (ItemID.ADAMANTITE_ORE, "Adamantite"),
	RUNITE     (ItemID.RUNITE_ORE,     "Runite"),
	AMETHYST   (ItemID.AMETHYST,       "Amethyst"),
	BLURITE    (ItemID.BLURITE_ORE,    "Blurite"),
	CLAY       (ItemID.CLAY,           "Clay"),
	RUNE_ESSENCE (ItemID.RUNE_ESSENCE, "Rune essence"),
	PURE_ESSENCE (ItemID.PURE_ESSENCE, "Pure essence");

	private static final Map<Integer, Ores> BY_ID;

	static
	{
		Map<Integer, Ores> m = new HashMap<>();
		for (Ores o : values())
		{
			m.put(o.itemId, o);
		}
		BY_ID = Collections.unmodifiableMap(m);
	}

	private final int itemId;
	private final String displayName;

	Ores(int itemId, String displayName)
	{
		this.itemId = itemId;
		this.displayName = displayName;
	}

	public int itemId()
	{
		return itemId;
	}

	public String displayName()
	{
		return displayName;
	}

	public static Ores fromItemId(int itemId)
	{
		return BY_ID.get(itemId);
	}

	public static boolean isOre(int itemId)
	{
		return BY_ID.containsKey(itemId);
	}

	public static String displayName(int itemId)
	{
		Ores o = BY_ID.get(itemId);
		return o == null ? "Unknown (" + itemId + ")" : o.displayName;
	}
}
