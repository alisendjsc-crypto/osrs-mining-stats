package com.josiahcooper.miningstats;

import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Plugin-layer event-routing tests. Closes the test-coverage gap that let v0.3.0 ship with
 * a base-game regression invisible to the gate-level unit tests. {@link MiningSuccessGateTest}
 * exercises the gate in isolation; this class exercises the path from
 * {@link MiningStatsPlugin}'s {@code @Subscribe} handlers through container-id dispatch and
 * (v0.3.2) the per-tick animation heartbeat.
 *
 * <p>Dispatch tests use the package-private seams {@link MiningStatsPlugin#handleItemContainerEvent}
 * and {@link MiningStatsPlugin#handleAnimationHeartbeat} so the harness doesn't need to
 * construct RuneLite events or mock {@link net.runelite.api.ItemContainer} /
 * {@link net.runelite.api.Player}. Animation and XP-delta state are driven directly via the
 * gate API (the same calls the plugin's animation/XP handlers make).
 *
 * <p><strong>v0.3.2 status:</strong> the bank-handler architecture has been reverted after
 * the Endless Harvest test on a Leagues alt empirically demonstrated that bank events do not
 * fire while the bank UI is closed. The {@code bankEvent*} tests retained here verify the
 * post-revert contract — bank container events are ignored entirely, so a bank event
 * arriving between an XP delta and an inventory event must not perturb the inventory
 * pairing.
 */
public class PluginEventRoutingTest
{
	private static final int INVENTORY_ID = InventoryID.INVENTORY.getId();
	private static final int BANK_ID = InventoryID.BANK.getId();

	private MiningStatsPlugin plugin;
	private MiningSuccessGate gate;
	private RollingWindow window;

	@Before
	public void setUp() throws Exception
	{
		plugin = new MiningStatsPlugin();
		gate = new MiningSuccessGate();
		// 30s AFK threshold matches the plugin's default config; doesn't interact with the
		// gate logic under test, only with RollingWindow's active-ms tracking.
		window = new RollingWindow(30_000L);
		setField(plugin, "miningGate", gate);
		setField(plugin, "rollingWindow", window);
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static Map<Integer, Integer> snapshot(Object... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((Integer) pairs[i], (Integer) pairs[i + 1]);
		}
		return m;
	}

	// --- Scenario I distinguisher ---

	/**
	 * Clean base-game swing — animation, XP delta, inventory increment. No bank events.
	 * Mirrors the trace Josiah is reporting broken at Camdozaal calcified rocks. If this
	 * fails, the simple inventory path is broken at the plugin layer; v0.2.0 has the same
	 * regression; rollback target should be v0.1.1 not v0.2.0.
	 */
	@Test
	public void baseGameMiningSwing_noBankEvents_recordsEvent()
	{
		// Login: inventory baseline (mirrors the first ItemContainerChanged after login).
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(), 1000L);
		assertEquals("inventory baseline must not emit", 0, window.totalCount());

		// Swing: animation, then XP delta, then inventory gains an ore.
		gate.recordMiningAnimation(2000L);
		gate.onMiningXpDelta(2100L);
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, 1), 2150L);

		assertEquals("ore must record after anim+XP+inv coincidence",
			1, window.totalCount());
		assertEquals(1, window.totalCount(ItemID.COPPER_ORE));
	}

	/**
	 * Inventory-event-first ordering (XP delta arrives after the inventory event). Same
	 * coincidence requirement; gate buffers the inventory diff for the upcoming XP delta.
	 * Sanity check that both orderings work via the plugin's dispatch seam, not just the
	 * one tested above.
	 */
	@Test
	public void baseGameMiningSwing_inventoryBeforeXp_recordsEvent()
	{
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(), 1000L);

		gate.recordMiningAnimation(2000L);
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, 1), 2050L);
		// Diff is held; not yet emitted.
		assertEquals(0, window.totalCount());
		// XP delta arrives within window — pairs with held diff. The gate API returns the
		// emit list, but the plugin's onStatChanged shell would route it into the rolling
		// window; this test calls the gate directly so we assert via gate output.
		assertEquals("held diff must be returned when XP delta arrives in window",
			1, gate.onMiningXpDelta(2100L).size());
	}

	/**
	 * Five successive swings — exactly the steady-state Josiah is reporting broken. First
	 * swing baselines inventory; swings 2–5 each record one ore. Tests that the simple path
	 * is durable across multiple iterations, not just one.
	 */
	@Test
	public void baseGameSteadyState_fiveSwings_recordsFour()
	{
		// Login baseline.
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(), 0L);

		// Five swings, each granting one ore. Swing 1 baselines inventory at the
		// plugin-layer side too (the post-swing-1 snapshot becomes the comparison base
		// for swing 2 onwards).
		for (int swing = 1; swing <= 5; swing++)
		{
			long animT = swing * 5000L;        // ~5s between swings, well under 3s gate
			long xpT = animT + 100L;
			long invT = animT + 150L;
			gate.recordMiningAnimation(animT);
			gate.onMiningXpDelta(xpT);
			plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, swing), invT);
		}

		// Explicit baseline at t=0 (empty inventory) becomes the comparison point.
		// Each of the 5 swings produces a positive diff against the prior snapshot, so
		// all 5 should record under the correct contract.
		assertEquals("after explicit baseline + 5 swings, all 5 ores recorded",
			5, window.totalCount(ItemID.COPPER_ORE));
	}

	// --- Scenario II distinguisher ---

	/**
	 * Bank events arriving between an XP delta and an inventory event must not steal the
	 * pairing. Pre-v0.3.2 this was the hypothesized Scenario II bug at the gate level; in
	 * v0.3.2 the bank handler is fully reverted and bank container events are ignored at
	 * the plugin's dispatch layer, so the test now verifies a stronger property: bank events
	 * have no observable effect at all on the inventory path. Passes naturally.
	 */
	@Test
	public void bankEventBetweenXpAndInventory_doesNotStealInventoryEmit()
	{
		// Login baseline (inventory only — bank is no longer routed in v0.3.2).
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(), 1000L);

		// Swing: animation, then XP delta, then BANK event with diff, then inventory event.
		gate.recordMiningAnimation(2000L);
		gate.onMiningXpDelta(2100L);

		// Bank event with non-empty diff arrives between the XP delta and the inventory
		// event. Pre-v0.3.2 this could perturb the gate's pairing; v0.3.2 drops bank events
		// entirely at the dispatch layer, so the buffered XP delta survives intact.
		plugin.handleItemContainerEvent(BANK_ID, snapshot(ItemID.LOGS, 5), 2150L);

		// Inventory gains the actual mined ore — pairs with the still-buffered XP delta.
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, 1), 2200L);

		assertEquals("inventory ore must record even when a bank event interleaves",
			1, window.totalCount(ItemID.COPPER_ORE));
	}

	/**
	 * Bank events with empty diffs (e.g., container-state re-broadcast with no actual
	 * change) must not interfere with inventory event pairing. v0.3.2: the dispatch layer
	 * drops all bank container events before they reach the gate, so empty/full diffs are
	 * indistinguishable here — both are no-ops by virtue of routing.
	 */
	@Test
	public void emptyBankEvents_doNotInterfereWithBaseGameMining()
	{
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(), 1000L);
		plugin.handleItemContainerEvent(BANK_ID, snapshot(ItemID.LOGS, 100), 1000L);

		gate.recordMiningAnimation(2000L);
		gate.onMiningXpDelta(2100L);
		// Bank event with no change — diff is empty, should hit the early-out.
		plugin.handleItemContainerEvent(BANK_ID, snapshot(ItemID.LOGS, 100), 2150L);
		// Inventory event — should still pair with the buffered XP delta.
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, 1), 2200L);

		assertEquals(1, window.totalCount(ItemID.COPPER_ORE));
	}

	/**
	 * Equipment container events must not be routed anywhere. Catches any future regression
	 * where the dispatch logic accidentally widens to a third container.
	 */
	@Test
	public void equipmentContainerEvent_isIgnored()
	{
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(), 1000L);

		gate.recordMiningAnimation(2000L);
		gate.onMiningXpDelta(2100L);
		// Equipment container ID — should be ignored entirely.
		final int EQUIPMENT_ID = InventoryID.EQUIPMENT.getId();
		plugin.handleItemContainerEvent(EQUIPMENT_ID, snapshot(ItemID.RUNE_PICKAXE, 1), 2150L);
		// Inventory event with real ore — should record normally.
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, 1), 2200L);

		assertEquals(1, window.totalCount(ItemID.COPPER_ORE));
		assertEquals("equipment events must not record",
			0, window.totalCount(ItemID.RUNE_PICKAXE));
	}

	/**
	 * Null guard: events arriving before {@code startUp} populates the gate must be silently
	 * ignored. The {@code @Subscribe} shell's null guard handles this in production; this
	 * test verifies the helper preserves that guard.
	 */
	@Test
	public void containerEventBeforeGateInitialized_isIgnored() throws Exception
	{
		// Tear down the gate to simulate the pre-startUp race window.
		setField(plugin, "miningGate", null);

		// Should be a no-op; no NPE, no recording.
		plugin.handleItemContainerEvent(INVENTORY_ID, snapshot(ItemID.COPPER_ORE, 1), 2200L);
		assertEquals(0, window.totalCount());
	}

	// --- v0.3.2 animation heartbeat tests ---

	/**
	 * The per-tick heartbeat refreshes the gate when the player's current animation is a
	 * mining animation. Pre-v0.3.2, the gate only refreshed on {@code AnimationChanged}
	 * transitions, which left it stale during continuous in-place animation loops — title
	 * never green, ETA hidden during active mining. The heartbeat closes that gap.
	 */
	@Test
	public void heartbeatRefreshesGate_whenAnimationIsMiningAnim()
	{
		// Use adamant wall-rock animation (6756) — verified mining animation per StaticDataTest.
		final int ADAMANT_WALL_ANIM = 6756;
		assertFalse("gate must start unrefreshed", gate.isAnimationGateActive(1000L));

		plugin.handleAnimationHeartbeat(ADAMANT_WALL_ANIM, 1000L);
		assertTrue("gate must be active immediately after heartbeat",
			gate.isAnimationGateActive(1000L));
		assertTrue("gate must remain active just under animationGateMs",
			gate.isAnimationGateActive(3500L));
		assertFalse("gate must close past animationGateMs",
			gate.isAnimationGateActive(4500L));
	}

	/**
	 * The heartbeat is a no-op for non-mining animations. Catches a regression where the
	 * heartbeat would refresh the gate based on, e.g., a smithing or woodcutting animation
	 * that happens to share a numerical neighborhood with mining anims.
	 */
	@Test
	public void heartbeatDoesNotRefreshGate_forNonMiningAnimation()
	{
		// -1 is the OSRS idle/no-animation sentinel; not in MiningAnimations.
		final int IDLE = -1;
		plugin.handleAnimationHeartbeat(IDLE, 1000L);
		assertFalse("gate must remain inactive when animation is idle",
			gate.isAnimationGateActive(1000L));
	}

	/**
	 * Heartbeat must continue to refresh the gate during sustained mining. Reproduces the
	 * FlowersOEvil-reported bug pattern in unit form: many ticks, no inter-tick
	 * AnimationChanged events (only the heartbeat is firing), gate must stay open.
	 */
	@Test
	public void heartbeatKeepsGateOpenAcrossManyTicks_simulatesContinuousMining()
	{
		final int ADAMANT_WALL_ANIM = 6756;
		// Simulate 30 game ticks of continuous mining. Without the heartbeat the gate would
		// close 3000ms after the first tick; with it, every tick refreshes.
		for (int tick = 0; tick < 30; tick++)
		{
			long now = 1000L + tick * 600L; // 600ms per tick = standard OSRS tick rate
			plugin.handleAnimationHeartbeat(ADAMANT_WALL_ANIM, now);
			assertTrue("gate must stay open at tick " + tick,
				gate.isAnimationGateActive(now));
		}
	}

	/**
	 * Null guard: heartbeat ticks arriving before {@code startUp} populates the gate must
	 * be silently ignored.
	 */
	@Test
	public void heartbeatBeforeGateInitialized_isIgnored() throws Exception
	{
		setField(plugin, "miningGate", null);
		// Should be a no-op; no NPE.
		plugin.handleAnimationHeartbeat(6756, 1000L);
	}
}
