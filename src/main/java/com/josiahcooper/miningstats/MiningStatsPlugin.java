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
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@PluginDescriptor(
	name = "Mining Stats",
	description = "Honest per-hour mining rate via rolling window with AFK awareness and per-ore breakdown.",
	tags = {"mining", "skilling", "overlay", "stats"}
)
public class MiningStatsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private MiningStatsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MiningStatsOverlay overlay;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Getter
	private RollingWindow rollingWindow;

	/**
	 * Persistence key prefix under config group {@code miningstats}. Full key is
	 * {@code persisted.<normalizedPlayerName>}. Set dynamically via
	 * {@link ConfigManager#setConfiguration}, so it does NOT appear in the {@code @ConfigItem}
	 * settings panel.
	 */
	static final String PERSIST_KEY_PREFIX = "persisted.";

	/** Last seen logged-in player name, lowercased + whitespace-normalized. Null until first login. */
	private String lastKnownPlayerKey;

	/** Once-per-session flag preventing repeated load attempts after CONNECTION_LOST → LOGGED_IN cycles. */
	private boolean loadAttempted;

	/**
	 * Path B (v0.2.0): replaces the v0.1.x enum-membership gate with animation +
	 * XP-delta-coincidence discrimination. Owns inventory snapshot, animation timestamp,
	 * and the held-diff buffer.
	 */
	private MiningSuccessGate miningGate;

	private int baselineMiningXp = -1;
	private int currentMiningXp = 0;

	@Override
	protected void startUp()
	{
		rollingWindow = new RollingWindow(config.afkThresholdSeconds() * 1000L);
		miningGate = new MiningSuccessGate();
		loadAttempted = false;
		lastKnownPlayerKey = null;
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			baselineMiningXp = client.getSkillExperience(Skill.MINING);
			currentMiningXp = baselineMiningXp;
			// If we're already logged in at plugin enable, attempt restore immediately.
			tryLoadPersistedSession();
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
		// Persist before clearing state so the next startUp can recover it.
		persistCurrentSession();
		overlayManager.remove(overlay);
		rollingWindow = null;
		miningGate = null;
		baselineMiningXp = -1;
		currentMiningXp = 0;
		lastKnownPlayerKey = null;
		loadAttempted = false;
		log.debug("Mining Stats stopped");
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer() || miningGate == null)
		{
			return;
		}
		int animId = client.getLocalPlayer().getAnimation();
		if (MiningAnimations.isMiningAnimation(animId))
		{
			miningGate.recordMiningAnimation(System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId() || miningGate == null
			|| rollingWindow == null)
		{
			return;
		}
		Map<Integer, Integer> current = countItems(event.getItemContainer());
		long now = System.currentTimeMillis();
		List<Integer> gained = miningGate.onInventoryChange(current, now);
		for (int itemId : gained)
		{
			rollingWindow.recordEvent(itemId, now);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.MINING)
		{
			return;
		}
		int previousXp = currentMiningXp;
		currentMiningXp = event.getXp();
		if (baselineMiningXp == -1)
		{
			// First Mining StatChanged after startUp on a not-yet-logged-in client.
			baselineMiningXp = currentMiningXp;
			return;
		}
		if (currentMiningXp > previousXp && miningGate != null && rollingWindow != null)
		{
			long now = System.currentTimeMillis();
			List<Integer> gained = miningGate.onMiningXpDelta(now);
			for (int itemId : gained)
			{
				rollingWindow.recordEvent(itemId, now);
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
			handlePossibleCharacterSwitch();
			tryLoadPersistedSession();
		}
		else if (state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST)
		{
			// Save before the disconnect blanks our state surface.
			persistCurrentSession();
			// Force re-baseline on the next inventory event so the post-login snapshot
			// doesn't get diffed against pre-logout state.
			if (miningGate != null)
			{
				miningGate.resetInventoryBaseline();
			}
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

	/** True if a mining animation has played within the gate's animation window. */
	public boolean isCurrentlyMining()
	{
		return miningGate != null && miningGate.isAnimationGateActive(System.currentTimeMillis());
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

	/**
	 * Display name for an item ID. Prefers the curated short name from {@link Ores}; falls
	 * back to the in-game item name via {@code ItemManager} for IDs not in the enum (Path B
	 * detection can record any item, not just enum members).
	 */
	public String displayNameFor(int itemId)
	{
		if (Ores.isOre(itemId))
		{
			return Ores.displayName(itemId);
		}
		try
		{
			String name = itemManager.getItemComposition(itemId).getName();
			if (name != null && !name.isEmpty() && !"null".equals(name))
			{
				return name;
			}
		}
		catch (RuntimeException ignored)
		{
			// Unknown item or cache miss — fall through to placeholder.
		}
		return "Item " + itemId;
	}

	/**
	 * Stable per-character ConfigManager key fragment. OSRS player names are alnum + spaces +
	 * underscores, max 12 chars; lowercase + whitespace→underscore yields a properties-safe
	 * fragment that survives display-capitalization changes.
	 */
	static String normalizePlayerName(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		return trimmed.toLowerCase().replace(' ', '_');
	}

	private void captureLastKnownPlayerKey()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		String key = normalizePlayerName(client.getLocalPlayer().getName());
		if (key != null)
		{
			lastKnownPlayerKey = key;
		}
	}

	/**
	 * Detect a character switch (Jagex-launcher hop or relog as a different character without
	 * fully restarting the client) and roll the in-memory window over to the new character.
	 *
	 * <p>Sequence: save current window under the OLD key, clear the window, update the key,
	 * reset the once-per-startup load flag so {@link #tryLoadPersistedSession} re-attempts for
	 * the new character.
	 *
	 * <p>If the local player isn't resolved yet, no-op — we'll re-check on the next event.
	 * If the key is unchanged (reconnect as same character), no-op — preserves any in-flight
	 * window state and avoids resetting the load flag prematurely.
	 */
	private void handlePossibleCharacterSwitch()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		String currentKey = normalizePlayerName(client.getLocalPlayer().getName());
		if (currentKey == null || currentKey.equals(lastKnownPlayerKey))
		{
			return;
		}
		if (lastKnownPlayerKey != null && rollingWindow != null)
		{
			// Save departing character's stats under their key BEFORE swapping.
			persistCurrentSession();
			rollingWindow.clear();
		}
		lastKnownPlayerKey = currentKey;
		loadAttempted = false;
	}

	private void tryLoadPersistedSession()
	{
		if (loadAttempted || rollingWindow == null || configManager == null)
		{
			return;
		}
		// Player name flickers null around state transitions; only act once we have it stable.
		captureLastKnownPlayerKey();
		if (lastKnownPlayerKey == null)
		{
			return;
		}
		// Don't clobber an in-progress session — only restore if we're starting from zero.
		if (rollingWindow.totalCount() != 0)
		{
			loadAttempted = true;
			return;
		}
		String blob = configManager.getConfiguration("miningstats", PERSIST_KEY_PREFIX + lastKnownPlayerKey);
		Optional<RollingWindow.Snapshot> snap = SessionPersistence.parse(
			blob, System.currentTimeMillis(), SessionPersistence.DEFAULT_MAX_AGE_MS);
		if (snap.isPresent())
		{
			rollingWindow.restoreFromSnapshot(snap.get());
			log.debug("Mining Stats restored {} events for {}", snap.get().events.size(), lastKnownPlayerKey);
		}
		// Whether or not the load succeeded, mark attempted so we don't retry every LOGGED_IN.
		loadAttempted = true;
	}

	private void persistCurrentSession()
	{
		if (rollingWindow == null || configManager == null || lastKnownPlayerKey == null)
		{
			return;
		}
		RollingWindow.Snapshot snap = rollingWindow.exportSnapshot();
		String serialized = SessionPersistence.serialize(snap, System.currentTimeMillis());
		String key = PERSIST_KEY_PREFIX + lastKnownPlayerKey;
		if (serialized == null)
		{
			// Empty session — clear any stale persisted blob so a future login starts fresh.
			configManager.unsetConfiguration("miningstats", key);
		}
		else
		{
			configManager.setConfiguration("miningstats", key, serialized);
		}
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
