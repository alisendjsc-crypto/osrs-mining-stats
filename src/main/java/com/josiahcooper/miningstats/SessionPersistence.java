package com.josiahcooper.miningstats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serializes and parses {@link RollingWindow.Snapshot} for cross-session persistence.
 *
 * <p>The snapshot is intended to survive an OSRS client disconnect/reconnect during a long
 * mining session. Format is a single line, semicolon-separated records, comma-separated
 * fields — chosen to round-trip cleanly through any properties-style backing store and to
 * stay debuggable when dumped from {@code settings.properties}.
 *
 * <p>Format (v1):
 * <pre>
 * v=1;snap=&lt;wallMs&gt;;cum=&lt;cumulativeActiveMs&gt;;last=&lt;lastEventWallMs&gt;;tot=&lt;sessionTotal&gt;
 *   ;e=&lt;oreId&gt;,&lt;wallMs&gt;,&lt;activeMs&gt;        (repeated 0..n)
 *   ;o=&lt;oreId&gt;,&lt;count&gt;                       (repeated 0..n)
 * </pre>
 *
 * <p>The class is RuneLite-decoupled and pure-functional — all I/O is the responsibility of
 * the calling plugin layer.
 */
public final class SessionPersistence
{
	/**
	 * Discard snapshots older than this — matches {@link RollingWindow}'s 60-minute hard
	 * retention cap, so we never restore data that would be pruned anyway.
	 */
	public static final long DEFAULT_MAX_AGE_MS = 60L * 60_000L;

	/** Format version. Bumped if the on-wire layout changes incompatibly. */
	public static final int FORMAT_VERSION = 1;

	private SessionPersistence()
	{
	}

	/**
	 * Serialize the current snapshot. Returns {@code null} for an empty session
	 * ({@code sessionTotal == 0}) — empty snapshots are not worth persisting.
	 *
	 * @param snap            current snapshot (typically {@link RollingWindow#exportSnapshot()})
	 * @param snapshotWallMs  wall-clock time at which the snapshot was taken
	 */
	public static String serialize(RollingWindow.Snapshot snap, long snapshotWallMs)
	{
		if (snap == null || snap.sessionTotal == 0)
		{
			return null;
		}
		StringBuilder sb = new StringBuilder(256);
		sb.append("v=").append(FORMAT_VERSION);
		sb.append(";snap=").append(snapshotWallMs);
		sb.append(";cum=").append(snap.cumulativeActiveMs);
		sb.append(";last=").append(snap.lastEventWallMs);
		sb.append(";tot=").append(snap.sessionTotal);
		for (RollingWindow.EventData e : snap.events)
		{
			sb.append(";e=").append(e.oreId)
				.append(',').append(e.wallTimeMs)
				.append(',').append(e.activeTimeMs);
		}
		for (Map.Entry<Integer, Integer> tally : snap.sessionByOre.entrySet())
		{
			sb.append(";o=").append(tally.getKey()).append(',').append(tally.getValue());
		}
		return sb.toString();
	}

	/**
	 * Parse a serialized snapshot. Returns {@link Optional#empty()} if:
	 * <ul>
	 *   <li>{@code text} is null or blank;</li>
	 *   <li>format version doesn't match {@link #FORMAT_VERSION};</li>
	 *   <li>any required header field is missing or unparseable;</li>
	 *   <li>any event/ore record is malformed;</li>
	 *   <li>the snapshot is older than {@code maxAgeMs}.</li>
	 * </ul>
	 *
	 * <p>Malformed input never throws — it returns empty so the plugin can degrade to a fresh
	 * session without surfacing the error to the user.
	 */
	public static Optional<RollingWindow.Snapshot> parse(String text, long nowWallMs, long maxAgeMs)
	{
		if (text == null || text.isEmpty())
		{
			return Optional.empty();
		}
		try
		{
			String[] records = text.split(";");
			Long snapshotWallMs = null;
			Long cumulativeActiveMs = null;
			Long lastEventWallMs = null;
			Integer sessionTotal = null;
			boolean versionSeen = false;
			List<RollingWindow.EventData> events = new ArrayList<>();
			Map<Integer, Integer> sessionByOre = new HashMap<>();

			for (String record : records)
			{
				if (record.isEmpty())
				{
					continue;
				}
				int eq = record.indexOf('=');
				if (eq <= 0)
				{
					return Optional.empty();
				}
				String key = record.substring(0, eq);
				String val = record.substring(eq + 1);

				switch (key)
				{
					case "v":
						if (Integer.parseInt(val) != FORMAT_VERSION)
						{
							return Optional.empty();
						}
						versionSeen = true;
						break;
					case "snap":
						snapshotWallMs = Long.parseLong(val);
						break;
					case "cum":
						cumulativeActiveMs = Long.parseLong(val);
						break;
					case "last":
						lastEventWallMs = Long.parseLong(val);
						break;
					case "tot":
						sessionTotal = Integer.parseInt(val);
						break;
					case "e":
					{
						String[] parts = val.split(",");
						if (parts.length != 3)
						{
							return Optional.empty();
						}
						events.add(new RollingWindow.EventData(
							Integer.parseInt(parts[0]),
							Long.parseLong(parts[1]),
							Long.parseLong(parts[2])
						));
						break;
					}
					case "o":
					{
						String[] parts = val.split(",");
						if (parts.length != 2)
						{
							return Optional.empty();
						}
						sessionByOre.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
						break;
					}
					default:
						// Forward-compatibility: unknown keys are skipped, not rejected.
						break;
				}
			}

			if (!versionSeen || snapshotWallMs == null || cumulativeActiveMs == null
				|| lastEventWallMs == null || sessionTotal == null)
			{
				return Optional.empty();
			}
			if (nowWallMs - snapshotWallMs > maxAgeMs)
			{
				return Optional.empty();
			}

			return Optional.of(new RollingWindow.Snapshot(
				cumulativeActiveMs,
				lastEventWallMs,
				sessionTotal,
				events,
				sessionByOre
			));
		}
		catch (NumberFormatException | IndexOutOfBoundsException ex)
		{
			return Optional.empty();
		}
	}
}
