package com.josiahcooper.miningstats;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MiningStatsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MiningStatsPlugin.class);
		RuneLite.main(args);
	}
}
