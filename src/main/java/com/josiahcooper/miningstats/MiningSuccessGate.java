package com.josiahcooper.miningstats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Discriminator for "this inventory increment came from a mining swing."
 *
 * <p>Replaces v0.1.x's enum-membership gate ({@link Ores#isOre(int)}) with a three-signal
 * coincidence check:
 * <ol>
 *   <li>A pickaxe-family animation has played within {@link #animationGateMs} (default 3000ms).</li>
 *   <li>A Mining XP delta has been observed within {@link #xpCoincidenceWindowMs} (default 1200ms,
 *       i.e. 2 game ticks) of an inventory increment.</li>
 *   <li>The inventory snapshot diff is non-negative for at least one item ID.</li>
 * </ol>
 *
 * <p>The animation gate is non-negotiable. It is what kills the Wintertodt brazier-repair
 * false-positive (Mining XP via hammer animation, not pickaxe), and the lamp/genie XP
 * scenarios (no inventory delta, but defensively also no recent pickaxe swing). Quest XP
 * grants apply post-dialog with no concurrent inventory event in the same window.
 *
 * <p>The coincidence window is symmetric: either the inventory event or the XP delta may
 * arrive first. The class buffers a single inventory diff for up to {@code xpCoincidenceWindowMs}
 * waiting for an XP delta; symmetrically, an XP delta buffers a timestamp waiting for an
 * inventory event. On the second event of the pair, the buffer is consumed (preventing
 * double-counting against subsequent unrelated events).
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

	// Diff awaiting an XP-delta confirmation (inventory-event-first ordering).
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
	 * Reset the inventory baseline and any held diff. Called on login/hop/disconnect to
	 * prevent the post-login snapshot from being diffed against pre-logout state.
	 */
	public void resetInventoryBaseline()
	{
		lastInventory = null;
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
