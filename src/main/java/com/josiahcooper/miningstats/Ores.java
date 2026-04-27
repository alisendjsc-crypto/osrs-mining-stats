package com.josiahcooper.miningstats;

import net.runelite.api.ItemID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mineable yields and their display names.
 *
 * <p>Coverage extended in v0.1.1 from the v0.1.0 conservative-standard list to include the
 * non-standard yields players hit during normal play: Camdozaal (Barronite shards), Granite
 * Quarry (3 weights), Sandstone Quarry (4 weights), Daeyalt Essence Mine, Fossil Island volcanic
 * ash, Motherlode pay-dirt, Lovakengj lovakite, Weiss basalt, and gem-rock yields (uncut
 * sapphire/emerald/ruby/diamond/opal/jade/red topaz).
 *
 * <p>Intentionally excluded: uncut onyx (TzHaar/Trahaearn-only, not a gem-rock yield), salt
 * rocks (urt/efh/te/ica/basalt salt — fringe content, low player overlap), tephra/calcite from
 * Volcanic Mine (group instance, separate scoring loop), Wintertodt bruma roots/kindling
 * (grants Mining XP via Firemaking activity, not a Mining yield), legacy DAEYALT_ORE 9632
 * (replaced by DAEYALT_ESSENCE post-Sins-of-the-Father), BARRONITE_DEPOSIT 25684 (rare
 * piece-container, opening it would double-count against shards which are the primary rate
 * signal).
 *
 * <p>If a future yield is missed: the player will see Mining XP increment but no Ores/hr or
 * Total tick. That's the v0.1.0 → v0.1.1 failure mode that triggered this expansion. A
 * coincidence-detection alternative (XP delta + same-tick inventory increment) is in the
 * v0.2.0 design notes in HANDOFF.md.
 */
public enum Ores
{
	// Standard pickaxe-mineable ores (v0.1.0)
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
	PURE_ESSENCE (ItemID.PURE_ESSENCE, "Pure essence"),

	// Camdozaal (v0.1.1)
	BARRONITE_SHARDS (ItemID.BARRONITE_SHARDS, "Barronite shards"),

	// Granite quarry — yield depends on Mining level (v0.1.1)
	GRANITE_500G (ItemID.GRANITE_500G, "Granite (500g)"),
	GRANITE_2KG  (ItemID.GRANITE_2KG,  "Granite (2kg)"),
	GRANITE_5KG  (ItemID.GRANITE_5KG,  "Granite (5kg)"),

	// Sandstone quarry — yield depends on Mining level (v0.1.1)
	SANDSTONE_1KG  (ItemID.SANDSTONE_1KG,  "Sandstone (1kg)"),
	SANDSTONE_2KG  (ItemID.SANDSTONE_2KG,  "Sandstone (2kg)"),
	SANDSTONE_5KG  (ItemID.SANDSTONE_5KG,  "Sandstone (5kg)"),
	SANDSTONE_10KG (ItemID.SANDSTONE_10KG, "Sandstone (10kg)"),

	// Other non-standard mining yields (v0.1.1)
	DAEYALT_ESSENCE (ItemID.DAEYALT_ESSENCE, "Daeyalt essence"),
	VOLCANIC_ASH    (ItemID.VOLCANIC_ASH,    "Volcanic ash"),
	PAYDIRT         (ItemID.PAYDIRT,         "Pay-dirt"),
	LOVAKITE        (ItemID.LOVAKITE_ORE,    "Lovakite"),
	BASALT          (ItemID.BASALT,          "Basalt"),

	// Gem rocks — Lunar Isle, Shilo Village (v0.1.1)
	UNCUT_SAPPHIRE  (ItemID.UNCUT_SAPPHIRE,  "Uncut sapphire"),
	UNCUT_EMERALD   (ItemID.UNCUT_EMERALD,   "Uncut emerald"),
	UNCUT_RUBY      (ItemID.UNCUT_RUBY,      "Uncut ruby"),
	UNCUT_DIAMOND   (ItemID.UNCUT_DIAMOND,   "Uncut diamond"),
	UNCUT_OPAL      (ItemID.UNCUT_OPAL,      "Uncut opal"),
	UNCUT_JADE      (ItemID.UNCUT_JADE,      "Uncut jade"),
	UNCUT_RED_TOPAZ (ItemID.UNCUT_RED_TOPAZ, "Uncut red topaz");

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
