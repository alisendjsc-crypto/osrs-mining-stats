package com.josiahcooper.miningstats;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * v0.2.0 session-persistence audit: serialize and parse {@link RollingWindow.Snapshot}
 * cleanly across an OSRS client disconnect/reconnect cycle.
 *
 * <p>The {@link RollingWindow} round-trip test is the load-bearing one — it verifies that a
 * restored window produces the same rate calculation as the original, which is the user-
 * visible contract of the persistence layer.
 */
public class SessionPersistenceTest
{
	private static final int COPPER = 436;
	private static final int IRON = 440;

	private static RollingWindow buildWindow()
	{
		// 30-second AFK threshold matches the config default.
		RollingWindow w = new RollingWindow(30_000L);
		long t0 = 1_700_000_000_000L;
		w.recordEvent(COPPER, t0);
		w.recordEvent(COPPER, t0 + 5_000);
		w.recordEvent(IRON, t0 + 12_000);
		w.recordEvent(COPPER, t0 + 20_000);
		return w;
	}

	@Test
	public void roundTrip_preservesAllStateAndRateCalculation()
	{
		RollingWindow original = buildWindow();
		long now = 1_700_000_030_000L;
		double originalRate = original.activeRatePerHour(60_000L, now);

		String serialized = SessionPersistence.serialize(original.exportSnapshot(), now);
		assertNotNull("serialize should produce non-null for a populated window", serialized);

		Optional<RollingWindow.Snapshot> parsed = SessionPersistence.parse(
			serialized, now + 60_000L, SessionPersistence.DEFAULT_MAX_AGE_MS);
		assertTrue(parsed.isPresent());

		RollingWindow restored = new RollingWindow(30_000L);
		restored.restoreFromSnapshot(parsed.get());

		assertEquals(original.totalCount(), restored.totalCount());
		assertEquals(original.totalCount(COPPER), restored.totalCount(COPPER));
		assertEquals(original.totalCount(IRON), restored.totalCount(IRON));
		// Rate computed at the same nowWallMs should match — the load-bearing user-visible contract.
		assertEquals(originalRate, restored.activeRatePerHour(60_000L, now), 0.001);
	}

	@Test
	public void roundTrip_preservesEventOrderForPrune()
	{
		// RollingWindow.prune() relies on insertion order (early-out break on first non-stale event).
		// If serialize/parse reorders, prune behavior breaks — even if all rate math still works
		// initially, subsequent recordEvent calls can leave stale events in the deque.
		RollingWindow original = buildWindow();
		long now = 1_700_000_030_000L;
		String serialized = SessionPersistence.serialize(original.exportSnapshot(), now);
		Optional<RollingWindow.Snapshot> parsed = SessionPersistence.parse(
			serialized, now, SessionPersistence.DEFAULT_MAX_AGE_MS);
		assertTrue(parsed.isPresent());

		long prevActive = -1L;
		for (RollingWindow.EventData e : parsed.get().events)
		{
			assertTrue("activeTimeMs must be non-decreasing in parsed order", e.activeTimeMs >= prevActive);
			prevActive = e.activeTimeMs;
		}
	}

	@Test
	public void parse_nullOrEmptyText_returnsEmpty()
	{
		assertFalse(SessionPersistence.parse(null, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
		assertFalse(SessionPersistence.parse("", 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_staleSnapshot_returnsEmpty()
	{
		// Build a real snapshot, then parse with nowWallMs far in the future.
		RollingWindow w = buildWindow();
		long snapTime = 1_700_000_030_000L;
		String serialized = SessionPersistence.serialize(w.exportSnapshot(), snapTime);
		long farFuture = snapTime + SessionPersistence.DEFAULT_MAX_AGE_MS + 1;
		assertFalse(SessionPersistence.parse(serialized, farFuture, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_freshSnapshotJustInsideMaxAge_succeeds()
	{
		// Boundary case: snapshot exactly at maxAge should still load (cutoff is strict >).
		RollingWindow w = buildWindow();
		long snapTime = 1_700_000_030_000L;
		String serialized = SessionPersistence.serialize(w.exportSnapshot(), snapTime);
		long edgeOfWindow = snapTime + SessionPersistence.DEFAULT_MAX_AGE_MS;
		assertTrue(SessionPersistence.parse(serialized, edgeOfWindow, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_wrongVersion_returnsEmpty()
	{
		String serialized = "v=99;snap=0;cum=0;last=0;tot=0";
		assertFalse(SessionPersistence.parse(serialized, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_missingVersionHeader_returnsEmpty()
	{
		String serialized = "snap=0;cum=0;last=0;tot=0";
		assertFalse(SessionPersistence.parse(serialized, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_missingRequiredField_returnsEmpty()
	{
		// Missing 'tot' → reject. (Version present, but header incomplete.)
		String serialized = "v=1;snap=0;cum=0;last=0";
		assertFalse(SessionPersistence.parse(serialized, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_malformedEventRecord_returnsEmpty()
	{
		// 'e' record with only 2 fields instead of 3.
		String serialized = "v=1;snap=0;cum=0;last=0;tot=1;e=436,1000";
		assertFalse(SessionPersistence.parse(serialized, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_nonNumericField_returnsEmpty()
	{
		String serialized = "v=1;snap=NOT_A_NUMBER;cum=0;last=0;tot=0";
		assertFalse(SessionPersistence.parse(serialized, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS).isPresent());
	}

	@Test
	public void parse_unknownKey_skippedForForwardCompat()
	{
		// Future format may add fields. Older parsers must not reject.
		String serialized = "v=1;snap=0;cum=0;last=0;tot=0;futurefield=anything";
		Optional<RollingWindow.Snapshot> parsed = SessionPersistence.parse(
			serialized, 0L, SessionPersistence.DEFAULT_MAX_AGE_MS);
		assertTrue(parsed.isPresent());
	}

	@Test
	public void serialize_emptySession_returnsNull()
	{
		// sessionTotal == 0 means nothing worth persisting.
		RollingWindow empty = new RollingWindow(30_000L);
		assertNull(SessionPersistence.serialize(empty.exportSnapshot(), 1_700_000_000_000L));
	}

	@Test
	public void serialize_nullSnapshot_returnsNull()
	{
		assertNull(SessionPersistence.serialize(null, 1_700_000_000_000L));
	}

	@Test
	public void roundTrip_throughDisconnectScenario_continuesSession()
	{
		// Simulates the user-visible failure mode the audit was scoped to fix:
		// 1. Player mines for a while, accumulating events.
		// 2. Client disconnects (we serialize at this moment).
		// 3. Player reconnects 5 minutes later (well within MAX_AGE).
		// 4. We restore and continue mining — the resumed session's rate should reflect
		//    pre-disconnect events, not start from zero.
		RollingWindow before = buildWindow();
		long disconnectTime = 1_700_000_030_000L;
		String blob = SessionPersistence.serialize(before.exportSnapshot(), disconnectTime);

		long reconnectTime = disconnectTime + 5L * 60_000L; // 5 min later
		Optional<RollingWindow.Snapshot> snap = SessionPersistence.parse(
			blob, reconnectTime, SessionPersistence.DEFAULT_MAX_AGE_MS);
		assertTrue(snap.isPresent());

		RollingWindow after = new RollingWindow(30_000L);
		after.restoreFromSnapshot(snap.get());
		// Continue mining — record one more event.
		after.recordEvent(COPPER, reconnectTime + 1_000L);

		assertEquals("session totals carry across the disconnect", 5, after.totalCount());
		assertEquals(3 + 1, after.totalCount(COPPER));
		assertEquals(1, after.totalCount(IRON));
	}
}
