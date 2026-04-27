package com.josiahcooper.miningstats;

/**
 * Pure helper for computing and formatting time-to-full-inventory while mining.
 *
 * <p>Standalone (no RuneLite dependencies) so it can be unit-tested without mocking the client.
 */
public final class InventoryEta
{
	/** Standard OSRS inventory capacity. */
	public static final int INVENTORY_CAPACITY = 28;

	private InventoryEta()
	{
	}

	/**
	 * Estimated seconds until the inventory fills, given the current free-slot count and ore rate.
	 * Returns -1 when no estimate is meaningful (zero or negative rate / no free slots).
	 *
	 * <p>Ores in OSRS are unstackable, so each ore occupies one slot — free-slots is the count
	 * of ores remaining before the player must bank.
	 */
	public static long etaSeconds(int freeSlots, double oresPerHour)
	{
		if (oresPerHour <= 0 || freeSlots <= 0)
		{
			return -1L;
		}
		double oresPerSecond = oresPerHour / 3600.0;
		return Math.max(0L, Math.round(freeSlots / oresPerSecond));
	}

	/**
	 * Format a duration in seconds to a compact human-readable string. Returns {@code "—"} when
	 * the input is negative (no estimate).
	 */
	public static String formatEta(long seconds)
	{
		if (seconds < 0)
		{
			return "—"; // em-dash
		}
		if (seconds < 60)
		{
			return seconds + "s";
		}
		long minutes = seconds / 60;
		long secs = seconds % 60;
		if (minutes < 60)
		{
			return minutes + "m " + secs + "s";
		}
		long hours = minutes / 60;
		long mins = minutes % 60;
		return hours + "h " + mins + "m";
	}
}
