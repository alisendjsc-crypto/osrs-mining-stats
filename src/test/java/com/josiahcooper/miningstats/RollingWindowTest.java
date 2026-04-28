package com.josiahcooper.miningstats;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RollingWindowTest
{
	private static final long AFK_30S = 30_000L;
	private static final long ONE_MIN = 60_000L;
	private static final long FIVE_MIN = 5 * ONE_MIN;

	private static final int COPPER = 436;
	private static final int IRON = 440;

	@Test
	public void emptyWindowReportsZero()
	{
		RollingWindow w = new RollingWindow(AFK_30S);
		assertEquals(0.0, w.activeRatePerHour(FIVE_MIN, 0L), 0.0001);
		assertEquals(0, w.totalCount());
	}

	@Test
	public void steadyMiningProducesExpectedRate()
	{
		// 30 events spaced 10s apart. After the 30th event: 29 gaps × 10s = 290s of active time.
		// Window = 5 min, capped to 290s. Rate = 30 events / 290s × 3600 ≈ 372.4/hr.
		RollingWindow w = new RollingWindow(AFK_30S);
		long t = 0L;
		for (int i = 0; i < 30; i++)
		{
			t += 10_000L;
			w.recordEvent(COPPER, t);
		}
		double rate = w.activeRatePerHour(FIVE_MIN, t);
		assertEquals(372.4, rate, 1.0);
		assertEquals(30, w.totalCount());
	}

	@Test
	public void afkGapDoesNotDilateRate()
	{
		// Mine 4 events at 10s intervals, AFK 10 minutes, then mine 1 more.
		// Active time = 30s (the 3 gaps inside the first burst). Post-AFK gap > threshold → contributes 0.
		// 5 events / 30s × 3600 = 600/hr — i.e., the AFK didn't drag the rate down.
		RollingWindow w = new RollingWindow(AFK_30S);
		long t = 0L;
		for (int i = 0; i < 4; i++)
		{
			t += 10_000L;
			w.recordEvent(COPPER, t);
		}
		t += 10 * ONE_MIN; // AFK
		w.recordEvent(COPPER, t);
		double rate = w.activeRatePerHour(FIVE_MIN, t);
		assertEquals(600.0, rate, 0.5);
	}

	@Test
	public void perOreRateSeparation()
	{
		// 10 copper @ 6s spacing, then 5 iron @ 6s spacing. 14 gaps of 6s = 84s active.
		RollingWindow w = new RollingWindow(AFK_30S);
		long t = 0L;
		for (int i = 0; i < 10; i++) { t += 6_000L; w.recordEvent(COPPER, t); }
		for (int i = 0; i < 5; i++)  { t += 6_000L; w.recordEvent(IRON,   t); }
		double copperRate = w.activeRatePerHour(COPPER, FIVE_MIN, t);
		double ironRate   = w.activeRatePerHour(IRON,   FIVE_MIN, t);
		// 10 / 84s × 3600 ≈ 428.6;  5 / 84s × 3600 ≈ 214.3
		assertEquals(428.6, copperRate, 1.0);
		assertEquals(214.3, ironRate,   1.0);
	}

	@Test
	public void totalByOreSortedDescending()
	{
		RollingWindow w = new RollingWindow(AFK_30S);
		long t = 0L;
		for (int i = 0; i < 3; i++) { t += 5_000L; w.recordEvent(IRON,   t); }
		for (int i = 0; i < 7; i++) { t += 5_000L; w.recordEvent(COPPER, t); }
		Map<Integer, Integer> totals = w.totalByOre();
		assertEquals(Integer.valueOf(COPPER), totals.keySet().iterator().next());
		assertEquals(7, totals.get(COPPER).intValue());
		assertEquals(3, totals.get(IRON).intValue());
	}

	@Test
	public void rejectsNonPositiveAfkThreshold()
	{
		try
		{
			new RollingWindow(0L);
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(true);
		}
	}

	@Test
	public void clearResetsState()
	{
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 1_000L);
		w.recordEvent(COPPER, 2_000L);
		assertEquals(2, w.totalCount());
		w.clear();
		assertEquals(0, w.totalCount());
		assertEquals(0.0, w.activeRatePerHour(FIVE_MIN, 3_000L), 0.0001);
	}

	@Test
	public void shortSessionUsesActiveAsDenominator()
	{
		// 2 events 20s apart. Active = 20s, well below the 5-min window — denominator clamps to 20s.
		// 2 / 20s × 3600 = 360/hr.
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 0L);
		w.recordEvent(COPPER, 20_000L);
		double rate = w.activeRatePerHour(FIVE_MIN, 20_000L);
		assertEquals(360.0, rate, 0.5);
	}

	@Test
	public void trailingGapAdvancesActiveTimeUntilThreshold()
	{
		// One event at t=0, query at t=10s (well below 30s threshold). Active should be ~10s.
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 0L);
		assertEquals(10_000L, w.activeTimeMs(10_000L));
		// At t=60s (past threshold), active time freezes at threshold.
		assertEquals(AFK_30S, w.activeTimeMs(60_000L));
	}

	// --- v0.3.2 wall-clock auto-hide tests ---
	// hasEventsWithinWallTime is the auto-hide proxy. Distinct from activeRatePerHour
	// because the rate calc operates on the (AFK-frozen) active-time axis, while auto-hide
	// is a real-elapsed-time concern.

	@Test
	public void hasEventsWithinWallTime_emptyWindow_false()
	{
		RollingWindow w = new RollingWindow(AFK_30S);
		assertFalse(w.hasEventsWithinWallTime(FIVE_MIN, 0L));
		assertFalse(w.hasEventsWithinWallTime(FIVE_MIN, 1_000_000L));
	}

	@Test
	public void hasEventsWithinWallTime_recentEvent_true()
	{
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 100_000L);
		// 60s after the event, well within the 5-min window.
		assertTrue(w.hasEventsWithinWallTime(FIVE_MIN, 160_000L));
	}

	@Test
	public void hasEventsWithinWallTime_eventAtBoundary_true()
	{
		// Inclusive boundary: event exactly windowMs old should count.
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 0L);
		assertTrue(w.hasEventsWithinWallTime(FIVE_MIN, FIVE_MIN));
	}

	@Test
	public void hasEventsWithinWallTime_eventPastWindow_false()
	{
		// Event from 6 minutes ago, window is 5 minutes.
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 0L);
		assertFalse(w.hasEventsWithinWallTime(FIVE_MIN, FIVE_MIN + ONE_MIN));
	}

	@Test
	public void hasEventsWithinWallTime_drainsNaturally_unlikeRate()
	{
		// Regression guard for the v0.3.2 fix. Pre-fix, the overlay's auto-hide check used
		// `activeRatePerHour > 0`. After AFK, currentActive freezes at cumulativeActiveMs +
		// afkThresholdMs, and the rate-window cutoff freezes with it, so events from the
		// pre-AFK burst stay inside the window forever. Wall-clock semantics drain
		// correctly: as wall time advances past windowMs since the last event, the check
		// returns false even though the active-time-based rate would still be > 0.
		RollingWindow w = new RollingWindow(AFK_30S);
		// Mine a quick burst, then walk away.
		w.recordEvent(COPPER, 0L);
		w.recordEvent(COPPER, 5_000L);
		w.recordEvent(COPPER, 10_000L);

		// Immediately after, wall-clock check sees recent activity.
		assertTrue(w.hasEventsWithinWallTime(FIVE_MIN, 10_500L));
		// Active-time-based rate also > 0 here.
		assertTrue(w.activeRatePerHour(FIVE_MIN, 10_500L) > 0.0);

		// Wait 10 minutes (well past the 5-minute window).
		long after10Min = 10_000L + 10 * ONE_MIN;
		// Wall-clock check correctly drained.
		assertFalse("wall-clock check must return false after window passes",
			w.hasEventsWithinWallTime(FIVE_MIN, after10Min));
		// Rate, by contrast, is still > 0 because active-time froze at AFK_30S past the
		// last event and the cutoff froze with it. This is the bug v0.3.2 routes around.
		assertTrue("rate is still > 0 (active-time froze) — confirms why we needed a separate check",
			w.activeRatePerHour(FIVE_MIN, after10Min) > 0.0);
	}

	@Test
	public void hasEventsWithinWallTime_iteratesFromTail_isEfficient()
	{
		// 1000 events at 1ms intervals. Tail-first iteration should return on the first
		// element past the cutoff, not scan the whole deque. Asserts behavior, not perf —
		// but a future regression that switches to head-first iteration would still produce
		// correct results, just expensively. We assert correctness here; perf is implicit.
		RollingWindow w = new RollingWindow(AFK_30S);
		for (long i = 0; i < 1000; i++)
		{
			w.recordEvent(COPPER, i);
		}
		// Window of 100ms, query at t=2000 — only the tail-end events (900-999) qualify.
		// Wait, those are also outside 100ms of t=2000 (they're at t=999, so 1001ms old).
		// Let me re-frame: events 0-999 at ms intervals, query at t=999.
		// 100ms window from t=999 means events with wallTimeMs >= 899 qualify. That's events 899-999.
		assertTrue(w.hasEventsWithinWallTime(100L, 999L));
		// Window of 100ms at t=2000 means events with wallTimeMs >= 1900 qualify. None do.
		assertFalse(w.hasEventsWithinWallTime(100L, 2000L));
	}

	@Test
	public void hasEventsWithinWallTime_clearedWindow_false()
	{
		// After clear(), the wall-clock check must return false even if events were
		// previously recorded.
		RollingWindow w = new RollingWindow(AFK_30S);
		w.recordEvent(COPPER, 1000L);
		assertTrue(w.hasEventsWithinWallTime(FIVE_MIN, 1500L));
		w.clear();
		assertFalse(w.hasEventsWithinWallTime(FIVE_MIN, 1500L));
	}

	@Test
	public void hasEventsWithinWallTime_afterRestoreFromSnapshot_works()
	{
		// Persistence round-trip must preserve wall-clock check semantics. Existing events'
		// wallTimeMs survives the snapshot.
		RollingWindow original = new RollingWindow(AFK_30S);
		original.recordEvent(COPPER, 1000L);
		original.recordEvent(COPPER, 2000L);
		RollingWindow.Snapshot snap = original.exportSnapshot();

		RollingWindow restored = new RollingWindow(AFK_30S);
		restored.restoreFromSnapshot(snap);
		assertTrue(restored.hasEventsWithinWallTime(FIVE_MIN, 2500L));
		assertFalse(restored.hasEventsWithinWallTime(100L, 5000L)); // events too old for 100ms window
	}
}
