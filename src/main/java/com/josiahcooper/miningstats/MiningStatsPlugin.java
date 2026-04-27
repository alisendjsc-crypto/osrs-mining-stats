package com.josiahcooper.miningstats;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "Mining Stats",
	description = "Honest per-hour mining rate via rolling window with AFK awareness and per-ore breakdown.",
	tags = {"mining", "skilling", "overlay", "stats"}
)
public class MiningStatsPlugin extends Plugin
{
	/**
	 * Maximum staleness (ms) of a mining animation for a subsequent inventory delta to count
	 * as a mining success. Generous enough to cover swing-to-inventory-event lag and tick
	 * variance without admitting unrelated pickups.
	 */
	private static final long MINING_ANIMATION_GATE_MS = 3000L;

	@Inject
	private Client client;

	@Inject
	private MiningStatsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MiningStatsOverlay overlay;

	@Getter
	private RollingWindow rollingWindow;

	private long lastMiningAnimationMs = Long.MIN_VALUE;
	private Map<Integer, Integer> lastInventory;

	private int baselineMiningXp = -1;
	private int currentMiningXp = 0;

	@Override
	protected void startUp()
	{
		rollingWindow = new RollingWindow(config.afkThresholdSeconds() * 1000L);
		lastMiningAnimationMs = Long.MIN_VALUE;
		lastInventory = null;
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			baselineMiningXp = client.getSkillExperience(Skill.MINING);
			currentMiningXp = baselineMiningXp;
		}
		else
		{
			baselineMiningXp = -1;
			currentMiningXp = 0;
		}
		overlayManager.add(overlay);
		log.debug("Mining Stats started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		rollingWindow = null;
		lastInventory = null;
		baselineMiningXp = -1;
		currentMiningXp = 0;
		lastMiningAnimationMs = Long.MIN_VALUE;
		log.debug("Mining Stats stopped");
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		int animId = client.getLocalPlayer().getAnimation();
		if (MiningAnimations.isMiningAnimation(animId))
		{
			lastMiningAnimationMs = System.currentTimeMillis();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		Map<Integer, Integer> current = countItems(event.getItemContainer());
		if (lastInventory == null)
		{
			// First post-login or post-startup snapshot — baseline only, never count.
			lastInventory = current;
			return;
		}
		long now = System.currentTimeMillis();
		boolean withinMiningGate = (now - lastMiningAnimationMs) <= MINING_ANIMATION_GATE_MS;
		if (withinMiningGate && rollingWindow != null)
		{
			List<Integer> oresGained = InventoryDelta.oresGained(lastInventory, current);
			for (int oreId : oresGained)
			{
				rollingWindow.recordEvent(oreId, now);
			}
		}
		lastInventory = current;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.MINING)
		{
			currentMiningXp = event.getXp();
			if (baselineMiningXp == -1)
			{
				baselineMiningXp = currentMiningXp;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			if (baselineMiningXp == -1)
			{
				baselineMiningXp = client.getSkillExperience(Skill.MINING);
				currentMiningXp = baselineMiningXp;
			}
		}
		else if (state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST)
		{
			// Force re-baseline on the next inventory event so the post-login snapshot
			// doesn't get diffed against pre-logout state.
			lastInventory = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"miningstats".equals(event.getGroup()) || rollingWindow == null)
		{
			return;
		}
		if ("afkThresholdSeconds".equals(event.getKey()))
		{
			rollingWindow.setAfkThresholdMs(config.afkThresholdSeconds() * 1000L);
		}
	}

	/** Mining XP gained since the plugin was started. */
	public int sessionMiningXp()
	{
		return baselineMiningXp == -1 ? 0 : currentMiningXp - baselineMiningXp;
	}

	/** True if a mining animation has played within {@link #MINING_ANIMATION_GATE_MS}. */
	public boolean isCurrentlyMining()
	{
		return (System.currentTimeMillis() - lastMiningAnimationMs) <= MINING_ANIMATION_GATE_MS;
	}

	/** Free inventory slots remaining, or -1 if the inventory container isn't currently available. */
	public int freeInventorySlots()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return -1;
		}
		int filled = 0;
		for (Item item : inv.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				filled++;
			}
		}
		return Math.max(0, InventoryEta.INVENTORY_CAPACITY - filled);
	}

	private static Map<Integer, Integer> countItems(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container == null)
		{
			return counts;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() == -1)
			{
				continue;
			}
			counts.merge(item.getId(), item.getQuantity(), Integer::sum);
		}
		return counts;
	}

	@Provides
	MiningStatsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MiningStatsConfig.class);
	}
}
