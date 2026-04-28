package com.josiahcooper.miningstats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Discriminator for "this item gain came from a mining swing."
 *
 * <p>v0.3.0 contract — the item-source signal accepts <em>either</em> an inventory positive
 * delta <em>or</em> a bank positive delta (gated by a manual-banking filter). This widens
 * detection to OSRS Leagues auto-bank/auto-smelt relics and any future auto-deposit content
 * that routes mined items past the player inventory. Detection requires:
 * <ol>
 *   <li>A pickaxe-family animation has played within {@link #animationGateMs} (default 3000ms).</li>
 *   <li>A Mining XP delta has been observed within {@link #xpCoincidenceWindowMs} (default 1200ms,
 *       i.e. 2 game ticks) of the item-gain event.</li>
 *   <li>EITHER an inventory snapshot diff is non-negative for at least one item ID,
 *       OR a bank snapshot diff is non-negative for at least one item ID with no paired
 *       inventory-negative delta in the same window (the manual-banking filter).</li>
 * </ol>
 *
 * <p>The animation gate is non-negotiable. It is what kills the Wintertodt brazier-repair
 * false-positive (Mining XP via hammer animation, not pickaxe), and the lamp/genie XP
 * scenarios. Quest XP grants apply post-dialog with no concurrent item-gain event in the
 * same window.
 *
 * <p>The coincidence window is symmetric: either the item-gain event or the XP delta may
 * arrive first. The class buffers a single item-source diff for up to
 * {@code xpCoincidenceWindowMs} waiting for an XP delta; symmetrically, an XP delta buffers
 * a timestamp waiting for an item-source event. On the second event of the pair, the buffer
 * is consumed (preventing double-counting against subsequent unrelated events).
 *
 * <p><strong>Manual-banking filter (v0.3.0):</strong> a bank-positive delta only counts as a
 * mining yield if no inventory-negative delta occurred within {@link #xpCoincidenceWindowMs}
 * before it. Manual deposits (deposit-all, partial deposit, deposit box, Bank Heist relic
 * teleport-then-deposit) always pair an inventory-negative with the bank-positive; auto-deposit
 * relics produce bank-positive only. Past-direction check only — bank-before-inv-negative
 * ordering remains an undetected edge case (narrow envelope: requires manual banking within
 * the 3-second animation gate AND RuneLite delivering bank events before inventory events).
 *
 * <p><strong>Split-output edge case (known limitation):</strong> if a single mining swing
 * produces items routed to BOTH inventory and bank (e.g., bird's nest to inventory + ore to
 * bank), only the first item event to pair with the XP delta emits. The second is held and
 * ages out unconsumed. Affects mixed-output Leagues setups; rare in practice.
 *
 * <p>Item-ID filtering (the v0.1.x role of {@link Ores#isOre(int)}) is intentionally absent —
 * any item ID gained inside the gated window counts. The {@link Ores} enum is demoted to a
 * display-name override layer, with {@code ItemManager.getItemComposition(itemId).getName()}
 * as the fallback for unknown IDs (handled at the plugin layer, not here).
 *
 * <p>This class is single-thread (RuneLite client thread). Not thread-safe.
 */
public final class MiningSuccessGate
{
	/** Default staleness allowance for the most recent pickaxe animation. */
	public static final long DEFAULT_ANIMATION_GATE_MS = 3000L;

	/** Default coincidence window between Mining XP delta and inventory increment (~2 game ticks). */
	public static final long DEFAULT_XP_COINCIDENCE_WINDOW_MS = 1200L;

	/**
	 * Sentinel for "no event of this kind has been seen yet." Using a finite negative rather
	 * than {@code Long.MIN_VALUE} avoids overflow in {@code now - sentinel} subtraction at
	 * test-scale timestamps; explicit equality checks cover unset state in production too.
	 */
	private static final long UNSET = -1L;

	private final long animationGateMs;
	private final long xpCoincidenceWindowMs;

	private long lastMiningAnimationMs = UNSET;
	private long lastMiningXpDeltaMs = UNSET;
	private Map<Integer, Integer> lastInventory = null;

	/** Bank snapshot for diff computation. v0.3.0 — null until first observed bank event. */
	private Map<Integer, Integer> lastBank = null;

	/**
	 * Timestamp of the most recent inventory snapshot that contained a strictly negative delta
	 * vs. the prior snapshot. Used by {@link #onBankChange} to reject bank-positive deltas that
	 * pair with a recent inventory loss (the manual-banking filter). UNSET until the first
	 * inventory loss is observed.
	 */
	private long recentInventoryNegativeMs = UNSET;

	// Diff awaiting an XP-delta confirmation (item-event-first ordering).
	private List<Integer> heldDiff = null;
	private long heldDiffMs = UNSET;

	public MiningSuccessGate()
	{
		this(DEFAULT_ANIMATION_GATE_MS, DEFAULT_XP_COINCIDENCE_WINDOW_MS);
	}

	public MiningSuccessGate(long animationGateMs, long xpCoincidenceWindowMs)
	{
		this.animationGateMs = animationGateMs;
		this.xpCoincidenceWindowMs = xpCoincidenceWindowMs;
	}

	/** Mark a pickaxe-family animation as observed. */
	public void recordMiningAnimation(long now)
	{
		lastMiningAnimationMs = now;
	}

	/** True if a pickaxe-family animation played within {@link #animationGateMs}. */
	public boolean isAnimationGateActive(long now)
	{
		return lastMiningAnimationMs != UNSET && (now - lastMiningAnimationMs) <= animationGateMs;
	}

	private boolean xpDeltaWithinWindow(long now)
	{
		return lastMiningXpDeltaMs != UNSET && (now - lastMiningXpDeltaMs) <= xpCoincidenceWindowMs;
	}

	private boolean heldDiffWithinWindow(long now)
	{
		return heldDiff != null && heldDiffMs != UNSET && (now - heldDiffMs) <= xpCoincidenceWindowMs;
	}

	/**
	 * Reset the inventory and bank baselines and any held diff. Called on login/hop/disconnect
	 * to prevent post-login snapshots from being diffed against pre-logout state.
	 *
	 * <p>v0.3.0 — also clears the bank baseline. The {@link #recentInventoryNegativeMs}
	 * timestamp is left intact: it ages out naturally via the window check and clearing it on
	 * reset would not change correctness.
	 */
	public void resetInventoryBaseline()
	{
		lastInventory = null;
		lastBank = null;
		heldDiff = null;
		heldDiffMs = UNSET;
	}

	/**
	 * Consume an inventory snapshot.
	 *
	 * @return item IDs to record as mining successes (empty if the gate did not pair this
	 *         event with a recent XP delta, or if the animation gate is inactive, or if no
	 *         positive diff exists). Returns multiple entries when the diff covers a multi-unit
	 *         gain (e.g., +3 copper → three entries).
	 */
	public List<Integer> onInventoryChange(Map<Integer, Integer> current, long now)
	{
		if (lastInventory == null)
		{
			lastInventory = current;
			return Collections.emptyList();
		}

		// v0.3.0: record any negative-going delta for the manual-banking filter in onBankChange.
		// Done BEFORE the lastInventory update so the comparison uses the correct prior snapshot.
		if (InventoryDelta.hasNegativeDelta(lastInventory, current))
		{
			recentInventoryNegativeMs = now;
		}

		// Discard any held diff that has aged past the coincidence window.
		if (heldDiff != null && !heldDiffWithinWindow(now))
		{
			heldDiff = null;
		}

		List<Integer> diff = InventoryDelta.itemsGained(lastInventory, current);
		lastInventory = current;

		if (diff.isEmpty())
		{
			return Collections.emptyList();
		}
		if (!isAnimationGateActive(now))
		{
			// Non-mining context — discard regardless of XP timing.
			return Collections.emptyList();
		}
		if (xpDeltaWithinWindow(now))
		{
			// XP delta is recent — pair this diff (plus any still-held earlier diff) with it
			// and consume the XP delta.
			lastMiningXpDeltaMs = UNSET;
			if (heldDiff != null)
			{
				List<Integer> merged = new ArrayList<>(heldDiff);
				merged.addAll(diff);
				heldDiff = null;
				return merged;
			}
			return diff;
		}
		// No recent XP delta — hold the diff for the XP delta to arrive. If a prior diff is
		// still held within window (back-to-back inventory events without an interleaved XP),
		// append rather than replace so the earlier swing isn't silently dropped. Keep the
		// older timestamp so the buffer still ages out conservatively.
		if (heldDiff != null)
		{
			heldDiff.addAll(diff);
		}
		else
		{
			heldDiff = new ArrayList<>(diff);
			heldDiffMs = now;
		}
		return Collections.emptyList();
	}

	/**
	 * Consume a bank snapshot. v0.3.0 — secondary item-source surface for OSRS Leagues
	 * auto-bank/auto-smelt relics and any other auto-deposit content that routes mined items
	 * past the player inventory.
	 *
	 * <p>Manual-banking filter: if an inventory-negative delta occurred within the
	 * {@link #xpCoincidenceWindowMs} window before this event, the bank-positive is treated
	 * as the paired half of a deposit and rejected. Past-direction check only.
	 *
	 * <p>First call is treated as a baseline (no emit) — the bank may contain hundreds of
	 * pre-existing items the first time a session observes it; counting them all as a single
	 * gain would be catastrophic.
	 *
	 * @return item IDs to record as mining successes (empty if baseline, no positive diff,
	 *         animation gate inactive, manual-banking filter triggered, or held pending an
	 *         XP delta that has yet to arrive).
	 */
	public List<Integer> onBankChange(Map<Integer, Integer> current, long now)
	{
		if (lastBank == null)
		{
			lastBank = current;
			return Collections.emptyList();
		}

		// Discard any held diff that has aged past the coincidence window.
		if (heldDiff != null && !heldDiffWithinWindow(now))
		{
			heldDiff = null;
		}

		List<Integer> diff = InventoryDelta.itemsGained(lastBank, current);
		lastBank = current;

		if (diff.isEmpty())
		{
			return Collections.emptyList();
		}
		if (!isAnimationGateActive(now))
		{
			// Non-mining context — discard regardless of XP timing.
			return Collections.emptyList();
		}
		// Manual-banking filter: a recent inventory loss within the coincidence window pairs
		// this bank-positive with a deposit, not with a mining yield.
		if (recentInventoryNegativeMs != UNSET
			&& (now - recentInventoryNegativeMs) <= xpCoincidenceWindowMs)
		{
			return Collections.emptyList();
		}
		if (xpDeltaWithinWindow(now))
		{
			lastMiningXpDeltaMs = UNSET;
			if (heldDiff != null)
			{
				List<Integer> merged = new ArrayList<>(heldDiff);
				merged.addAll(diff);
				heldDiff = null;
				return merged;
			}
			return diff;
		}
		// No XP delta yet — hold for arrival. Same merge-don't-replace semantics as inventory.
		if (heldDiff != null)
		{
			heldDiff.addAll(diff);
		}
		else
		{
			heldDiff = new ArrayList<>(diff);
			heldDiffMs = now;
		}
		return Collections.emptyList();
	}

	/**
	 * Consume a Mining XP delta event.
	 *
	 * @return item IDs from a previously-held inventory diff if it pairs with this XP delta;
	 *         empty otherwise (in which case the XP delta is buffered for an inventory event).
	 */
	public List<Integer> onMiningXpDelta(long now)
	{
		if (heldDiffWithinWindow(now))
		{
			List<Integer> emit = heldDiff;
			heldDiff = null;
			// XP delta consumed by this pairing; do not also buffer it.
			lastMiningXpDeltaMs = UNSET;
			return emit;
		}
		// No pending diff — buffer the XP delta timestamp for an upcoming inventory event.
		lastMiningXpDeltaMs = now;
		return Collections.emptyList();
	}
}
