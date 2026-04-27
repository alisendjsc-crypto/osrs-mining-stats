package com.josiahcooper.miningstats;

import net.runelite.api.AnimationID;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Set of player animation IDs that indicate an active mining swing.
 *
 * <p>Coverage verified against RuneLite's {@code AnimationID} source: standard pickaxe lineup
 * (bronze through dragon), plus 3a, crystal, gilded, infernal, and trailblazer cosmetic variants.
 * Missing a player's pickaxe animation would silently undercount mining attempts, so we err
 * toward inclusion.
 */
public final class MiningAnimations
{
	private static final Set<Integer> IDS;

	static
	{
		Set<Integer> s = new HashSet<>();
		s.add(AnimationID.MINING_BRONZE_PICKAXE);
		s.add(AnimationID.MINING_IRON_PICKAXE);
		s.add(AnimationID.MINING_STEEL_PICKAXE);
		s.add(AnimationID.MINING_BLACK_PICKAXE);
		s.add(AnimationID.MINING_MITHRIL_PICKAXE);
		s.add(AnimationID.MINING_ADAMANT_PICKAXE);
		s.add(AnimationID.MINING_RUNE_PICKAXE);
		s.add(AnimationID.MINING_DRAGON_PICKAXE);
		s.add(AnimationID.MINING_3A_PICKAXE);
		s.add(AnimationID.MINING_CRYSTAL_PICKAXE);
		s.add(AnimationID.MINING_GILDED_PICKAXE);
		s.add(AnimationID.MINING_INFERNAL_PICKAXE);
		s.add(AnimationID.MINING_TRAILBLAZER_PICKAXE);
		IDS = Collections.unmodifiableSet(s);
	}

	private MiningAnimations()
	{
	}

	public static boolean isMiningAnimation(int animationId)
	{
		return IDS.contains(animationId);
	}

	/** Read-only view, primarily for testing. */
	public static Set<Integer> all()
	{
		return IDS;
	}
}
