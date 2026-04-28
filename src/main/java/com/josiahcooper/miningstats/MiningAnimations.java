package com.josiahcooper.miningstats;

import net.runelite.api.gameval.AnimationID;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Set of player animation IDs that indicate an active mining swing.
 *
 * <p><strong>v0.3.1 — namespace migration.</strong> Previous versions imported from
 * {@code net.runelite.api.AnimationID}, which only exposes the floor-mining animation per
 * pickaxe tier (e.g. {@code MINING_ADAMANT_PICKAXE = 628}). RuneLite has been migrating
 * canonical game-value constants to {@code net.runelite.api.gameval.AnimationID}, which
 * additionally exposes the wall-mining, no-reach-forward, and power-swing variants per tier.
 *
 * <p>The previous coverage was incomplete in a way that broke <em>any</em> mining activity
 * that uses a wall-mounted rock animation. The most-affected base-game activities include
 * Camdozaal barronite deposits and Trahaearn (Prifddinas) mine — both of which the player
 * faces a vertical rock wall, triggering the {@code _WALL} animation variant rather than
 * the floor {@code MINING_*_PICKAXE} variant. Players reported overlay never rendering at
 * Camdozaal on v0.2.0 + v0.3.0 despite XP incrementing correctly. This was the prod cause
 * of the v0.3.0 base-game regression that motivated the emergency rollback to v0.2.0.
 * v0.2.0 has the same gap; v0.1.1 didn't because it had no animation gate at all
 * (enum-membership-only detection).
 *
 * <p>This set mirrors {@code net.runelite.client.plugins.mining.MiningAnimation.MINING_ANIMATIONS}
 * — the set used by RuneLite's first-party Mining plugin, which is empirically verified
 * across all current pickaxe tiers and rock orientations. Adopting their list verbatim
 * eliminates the ongoing maintenance cost of keeping our own list in sync with Jagex's
 * occasional pickaxe additions and the namespace migration in flight.
 */
public final class MiningAnimations
{
	private static final Set<Integer> IDS;

	static
	{
		Set<Integer> s = new HashSet<>();

		// Bronze
		s.add(AnimationID.HUMAN_MINING_BRONZE_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_BRONZE_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_BRONZE);

		// Iron
		s.add(AnimationID.HUMAN_MINING_IRON_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_IRON_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_IRON);

		// Steel
		s.add(AnimationID.HUMAN_MINING_STEEL_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_STEEL_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_STEEL);

		// Black
		s.add(AnimationID.HUMAN_MINING_BLACK_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_BLACK_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_BLACK);

		// Mithril
		s.add(AnimationID.HUMAN_MINING_MITHRIL_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_MITHRIL);

		// Adamant — the wall variant (6756) is the v0.3.1 fix's load-bearing addition.
		// Camdozaal barronite deposits and other wall-mounted rocks emit this ID, not the
		// floor-mining 628.
		s.add(AnimationID.HUMAN_MINING_ADAMANT_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_ADAMANT);

		// Rune
		s.add(AnimationID.HUMAN_MINING_RUNE_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_RUNE_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_RUNE);

		// Gilded
		s.add(AnimationID.HUMAN_MINING_GILDED_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_GILDED_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_GILDED);

		// Dragon (standard)
		s.add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_DRAGON);

		// Dragon (pretty / upgraded)
		s.add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY);
		s.add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL);
		s.add(AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_PRETTY);

		// Infernal
		s.add(AnimationID.HUMAN_MINING_INFERNAL_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_INFERNAL);

		// 3a
		s.add(AnimationID.HUMAN_MINING_3A_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_3A_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_3A_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_3A);

		// Crystal
		s.add(AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_CRYSTAL);

		// Trailblazer
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_TRAILBLAZER);

		// Trailblazer (no-infernal variant — pre-Infernal Trailblazer cosmetic)
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_TRAILBLAZER_NO_INFERNAL);

		// Trailblazer Reloaded
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NOREACHFORWARD);

		// Trailblazer Reloaded (no-infernal)
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL);
		s.add(AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_NOREACHFORWARD);

		// League Trailblazer
		s.add(AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_LEAGUE_TRAILBLAZER);

		// Zalcano
		s.add(AnimationID.HUMAN_MINING_ZALCANO_PICKAXE);
		s.add(AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL);
		s.add(AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_NOREACHFORWARD);
		s.add(AnimationID.PICKAXE_POWER_SWING_ZALCANO);

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
