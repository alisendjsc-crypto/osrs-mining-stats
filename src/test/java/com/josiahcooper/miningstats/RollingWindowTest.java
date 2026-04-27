package com.josiahcooper.miningstats;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
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
}
