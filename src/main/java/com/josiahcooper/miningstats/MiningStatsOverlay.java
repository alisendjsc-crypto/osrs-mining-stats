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
 * inventory ETA, and per-ore breakdown. Hidden entirely when nothing has been mined yet and
 * the player isn't currently mining — keeps the screen clean before activity starts.
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
		// Suppress the panel until there's something to show.
		if (window.totalCount() == 0 && !plugin.isCurrentlyMining())
		{
			return null;
		}

		long now = System.currentTimeMillis();
		long windowMs = config.windowMinutes() * 60_000L;

		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(160, 0));

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Mining Stats")
			.color(Color.WHITE)
			.build());

		// Session XP — only show once XP has been accrued under the plugin.
		int xp = plugin.sessionMiningXp();
		if (xp > 0)
		{
			addLine("XP", QuantityFormatter.quantityToStackSize(xp));
		}

		// Active rate (events per hour over the configured window).
		double rate = window.activeRatePerHour(windowMs, now);
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
				addLine(Ores.displayName(oreId),
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
