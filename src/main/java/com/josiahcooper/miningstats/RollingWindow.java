package com.josiahcooper.miningstats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Time-windowed event store with AFK-aware accounting.
 *
 * <p>Each recorded event carries a wall-clock timestamp and a cumulative <em>active-time</em>
 * offset. Active time is wall-clock time with AFK gaps excised: if the gap between two
 * consecutive events meets or exceeds {@code afkThresholdMs}, that gap contributes zero
 * active time. Rate queries operate on the active-time axis, so a player who mines, AFKs,
 * and resumes sees a rate based on actual mining time rather than total elapsed time.
 *
 * <p>Two parallel state surfaces are maintained:
 * <ul>
 *   <li>A bounded events deque used for windowed rate calculations (subject to pruning).</li>
 *   <li>Lifetime per-ore counters that never decrement, feeding "session total" displays.</li>
 * </ul>
 *
 * <p>Framework-free by design: the caller supplies timestamps, so the class is fully
 * testable outside the RuneLite runtime.
 */
public final class RollingWindow
{
	/** Hard cap on retained active time. Sixty minutes covers the SPEC's 30-minute window twice over. */
	private static final long MAX_RETENTION_ACTIVE_MS = 60L * 60_000L;

	private long afkThresholdMs;
	private final Deque<Event> events = new ArrayDeque<>();

	private long cumulativeActiveMs = 0L;
	private long lastEventWallMs = Long.MIN_VALUE;

	private int sessionTotal = 0;
	private final Map<Integer, Integer> sessionByOre = new HashMap<>();

	public RollingWindow(long afkThresholdMs)
	{
		setAfkThresholdMs(afkThresholdMs);
	}

	/**
	 * Update the AFK threshold. Affects only future {@link #recordEvent} calls — events already
	 * recorded keep the active-time classification they were given under the previous threshold.
	 */
	public void setAfkThresholdMs(long afkThresholdMs)
	{
		if (afkThresholdMs <= 0)
		{
			throw new IllegalArgumentException("afkThresholdMs must be positive");
		}
		this.afkThresholdMs = afkThresholdMs;
	}

	public long afkThresholdMs()
	{
		return afkThresholdMs;
	}

	/** Record a successful mining event. Out-of-order timestamps (gap &lt;= 0) are ignored for active-time accounting but the event is still stored. */
	public void recordEvent(int oreId, long wallTimeMs)
	{
		if (lastEventWallMs != Long.MIN_VALUE)
		{
			long gap = wallTimeMs - lastEventWallMs;
			if (gap > 0 && gap < afkThresholdMs)
			{
				cumulativeActiveMs += gap;
			}
			// gap >= afkThresholdMs: AFK gap, contributes zero active time.
		}
		events.addLast(new Event(oreId, wallTimeMs, cumulativeActiveMs));
		lastEventWallMs = wallTimeMs;
		sessionTotal++;
		sessionByOre.merge(oreId, 1, Integer::sum);
		prune();
	}

	/**
	 * Active rate over the most recent {@code windowMs} of active time, expressed as events per hour.
	 * If accrued active time is shorter than the window, accrued active time is used as the denominator.
	 * Returns 0.0 when no active time has accrued.
	 */
	public double activeRatePerHour(long windowMs, long nowWallMs)
	{
		return activeRatePerHourFiltered(windowMs, nowWallMs, /* oreFilter */ -1);
	}

	/** Per-ore variant. */
	public double activeRatePerHour(int oreId, long windowMs, long nowWallMs)
	{
		return activeRatePerHourFiltered(windowMs, nowWallMs, oreId);
	}

	private double activeRatePerHourFiltered(long windowMs, long nowWallMs, int oreFilter)
	{
		long currentActive = currentActiveTime(nowWallMs);
		if (currentActive <= 0)
		{
			return 0.0;
		}
		long denomMs = Math.min(windowMs, currentActive);
		long cutoff = currentActive - windowMs;
		long count = 0;
		for (Event e : events)
		{
			if (e.activeTimeMs >= cutoff && (oreFilter == -1 || e.oreId == oreFilter))
			{
				count++;
			}
		}
		return count * 3_600_000.0 / denomMs;
	}

	/** Lifetime session count across all ores. */
	public int totalCount()
	{
		return sessionTotal;
	}

	/** Lifetime session count for a given ore. */
	public int totalCount(int oreId)
	{
		return sessionByOre.getOrDefault(oreId, 0);
	}

	/** Lifetime per-ore counts, ordered descending by count. */
	public Map<Integer, Integer> totalByOre()
	{
		Map<Integer, Integer> sorted = new LinkedHashMap<>();
		sessionByOre.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	/** Current cumulative active time in ms, including the trailing gap (capped at the AFK threshold). */
	public long activeTimeMs(long nowWallMs)
	{
		return currentActiveTime(nowWallMs);
	}

	/** Reset all state. */
	public void clear()
	{
		events.clear();
		sessionByOre.clear();
		sessionTotal = 0;
		cumulativeActiveMs = 0L;
		lastEventWallMs = Long.MIN_VALUE;
	}

	/**
	 * Export a serializable snapshot of all internal state. Used by {@link SessionPersistence}
	 * to survive client disconnects. The returned object is a defensive copy — mutating the
	 * snapshot's collections does not affect this window.
	 */
	public Snapshot exportSnapshot()
	{
		List<EventData> exported = new ArrayList<>(events.size());
		for (Event e : events)
		{
			exported.add(new EventData(e.oreId, e.wallTimeMs, e.activeTimeMs));
		}
		return new Snapshot(
			cumulativeActiveMs,
			lastEventWallMs,
			sessionTotal,
			Collections.unmodifiableList(exported),
			Collections.unmodifiableMap(new HashMap<>(sessionByOre))
		);
	}

	/**
	 * Restore state from a snapshot. Clears any existing state first. The AFK threshold is
	 * NOT touched — current threshold remains in effect for future events. Past events keep
	 * the active-time classification they were given when originally recorded, matching the
	 * documented {@link #setAfkThresholdMs} semantics.
	 */
	public void restoreFromSnapshot(Snapshot snap)
	{
		events.clear();
		sessionByOre.clear();
		for (EventData d : snap.events)
		{
			events.addLast(new Event(d.oreId, d.wallTimeMs, d.activeTimeMs));
		}
		sessionByOre.putAll(snap.sessionByOre);
		sessionTotal = snap.sessionTotal;
		cumulativeActiveMs = snap.cumulativeActiveMs;
		lastEventWallMs = snap.lastEventWallMs;
	}

	private long currentActiveTime(long nowWallMs)
	{
		if (lastEventWallMs == Long.MIN_VALUE)
		{
			return 0L;
		}
		long trailingGap = nowWallMs - lastEventWallMs;
		if (trailingGap < 0)
		{
			trailingGap = 0;
		}
		// Active time keeps ticking up to the AFK threshold past the last event, then freezes.
		long cappedGap = Math.min(trailingGap, afkThresholdMs);
		return cumulativeActiveMs + cappedGap;
	}

	private void prune()
	{
		long cutoff = cumulativeActiveMs - MAX_RETENTION_ACTIVE_MS;
		Iterator<Event> it = events.iterator();
		while (it.hasNext())
		{
			Event e = it.next();
			if (e.activeTimeMs < cutoff)
			{
				it.remove();
			}
			else
			{
				break; // events are insertion-ordered by activeTimeMs
			}
		}
	}

	private static final class Event
	{
		final int oreId;
		final long wallTimeMs;
		final long activeTimeMs;

		Event(int oreId, long wallTimeMs, long activeTimeMs)
		{
			this.oreId = oreId;
			this.wallTimeMs = wallTimeMs;
			this.activeTimeMs = activeTimeMs;
		}
	}

	/** Serializable triple mirroring the private {@code Event} class. */
	public static final class EventData
	{
		public final int oreId;
		public final long wallTimeMs;
		public final long activeTimeMs;

		public EventData(int oreId, long wallTimeMs, long activeTimeMs)
		{
			this.oreId = oreId;
			this.wallTimeMs = wallTimeMs;
			this.activeTimeMs = activeTimeMs;
		}
	}

	/** Immutable snapshot of {@link RollingWindow} internal state for persistence. */
	public static final class Snapshot
	{
		public final long cumulativeActiveMs;
		public final long lastEventWallMs;
		public final int sessionTotal;
		public final List<EventData> events;
		public final Map<Integer, Integer> sessionByOre;

		public Snapshot(long cumulativeActiveMs, long lastEventWallMs, int sessionTotal,
			List<EventData> events, Map<Integer, Integer> sessionByOre)
		{
			this.cumulativeActiveMs = cumulativeActiveMs;
			this.lastEventWallMs = lastEventWallMs;
			this.sessionTotal = sessionTotal;
			this.events = events;
			this.sessionByOre = sessionByOre;
		}
	}
}
