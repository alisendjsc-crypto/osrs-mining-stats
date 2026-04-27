package com.josiahcooper.miningstats;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("miningstats")
public interface MiningStatsConfig extends Config
{
	@ConfigItem(
		keyName = "windowMinutes",
		name = "Rolling window (min)",
		description = "Length of the rolling window over which the active rate is calculated."
	)
	@Range(min = 1, max = 30)
	default int windowMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "afkThresholdSeconds",
		name = "AFK threshold (sec)",
		description = "Inactivity gap before mining is considered paused; gaps longer than this are excluded from the rate."
	)
	@Range(min = 10, max = 120)
	default int afkThresholdSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "showPerOreBreakdown",
		name = "Show per-ore breakdown",
		description = "Display a table of each ore type, its session total, and its current rate."
	)
	default boolean showPerOreBreakdown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInventoryETA",
		name = "Show inventory ETA",
		description = "Display an estimated time-to-full-inventory while actively mining."
	)
	default boolean showInventoryETA()
	{
		return true;
	}
}
