package com.josiahcooper.miningstats;

import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;

/**
 * Top-right panel rendering the SPEC's five lines: session XP, active rate, session total,
 * inventory ETA, and per-ore breakdown. Hidden when no mining activity falls within the
 * configured rolling window AND the player isn't currently swinging — keeps the overlay
 * off-screen during fishing/banking/idle, and disappears after a session naturally drains.
 *
 * <p>v0.2.0: title color is the activity indicator (green = actively mining, light gray =
 * stale/post-swing). With auto-hide, the three overlay states are now distinguishable at
 * a glance: hidden = correctly idle, green title = working, gray title = canary that
 * detection has stopped picking up swings.
 */
public class MiningStatsOverlay extends OverlayPanel
{
	private final MiningStatsPlugin plugin;
	private final MiningStatsConfig config;

	@Inject
	private MiningStatsOverlay(MiningStatsPlugin plugin, MiningStatsConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RollingWindow window = plugin.getRollingWindow();
		if (window == null)
		{
			return null;
		}

		long now = System.currentTimeMillis();
		long windowMs = config.windowMinutes() * 60_000L;

		// Auto-hide: suppress the panel when the player isn't mining now AND no events
		// remain inside the rolling window. The pre-first-mine case (rate == 0, total == 0)
		// is naturally subsumed; the post-session-drain case (mined earlier, walked away,
		// rolling window emptied) is the new behavior over v0.1.x. Compute the rate once
		// and reuse below — activeRatePerHour iterates the event log, not free per call.
		boolean activelyMining = plugin.isCurrentlyMining();
		double rate = window.activeRatePerHour(windowMs, now);
		boolean hasRecentActivity = rate > 0.0;
		if (!activelyMining && !hasRecentActivity)
		{
			return null;
		}

		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(160, 0));

		// v0.2.0 indicator: title color is the canary for the three overlay states.
		//   GREEN — actively mining (visible + working).
		//   LIGHT_GRAY — visible because the rolling window still has events but no recent
		//     swing detected; either the player just stopped or detection has gone stale.
		// (The hidden-while-idle case is handled above by returning null.)
		Color titleColor = activelyMining ? new Color(108, 217, 108) : Color.LIGHT_GRAY;
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Mining Stats")
			.color(titleColor)
			.build());

		// Session XP — only show once XP has been accrued under the plugin.
		int xp = plugin.sessionMiningXp();
		if (xp > 0)
		{
			addLine("XP", QuantityFormatter.quantityToStackSize(xp));
		}

		// Active rate (events per hour over the configured window).
		addLine("Ores/hr", String.format("%,d", Math.round(rate)));

		// Session total.
		addLine("Total", String.format("%,d", window.totalCount()));

		// Inventory ETA — only when the player is actively mining and has free slots.
		if (config.showInventoryETA() && plugin.isCurrentlyMining())
		{
			int freeSlots = plugin.freeInventorySlots();
			if (freeSlots > 0)
			{
				long etaSeconds = InventoryEta.etaSeconds(freeSlots, rate);
				addLine("Full in", InventoryEta.formatEta(etaSeconds));
			}
		}

		// Per-ore breakdown.
		if (config.showPerOreBreakdown() && window.totalByOre().size() > 1)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("————")
				.right("")
				.build());
			for (Map.Entry<Integer, Integer> entry : window.totalByOre().entrySet())
			{
				int oreId = entry.getKey();
				int oreTotal = entry.getValue();
				double oreRate = window.activeRatePerHour(oreId, windowMs, now);
				addLine(plugin.displayNameFor(oreId),
					String.format("%,d (%,d/h)", oreTotal, Math.round(oreRate)));
			}
		}

		return super.render(graphics);
	}

	private void addLine(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.build());
	}
}
