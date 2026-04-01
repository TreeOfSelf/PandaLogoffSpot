package me.TreeOfSelf.PandaLogoffSpot;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import me.drex.vanish.api.VanishAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PandaLogoffSpot implements ModInitializer {
	public static final String MOD_ID = "panda-logoff-spot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final Map<UUID, LogoffDisplay> activeDisplays = new HashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	@Override
	public void onInitialize() {
		PandaLogoffSpotConfig.load();
		ServerPlayerEvents.JOIN.register(this::onJoin);
		ServerPlayerEvents.LEAVE.register(this::onLeave);
	}

	private void onLeave(ServerPlayer player) {
		if (FabricLoader.getInstance().isModLoaded("melius-vanish") && VanishAPI.isVanished(player)) {
			return;
		}

		UUID playerId = player.getUUID();
		String playerName = player.getGameProfile().name();
		float halfHeight = player.getDimensions(player.getPose()).height() / 2.0F;
		Vec3 position = player.position().add(0.0, halfHeight, 0.0);

		int viewDistance = player.level().getServer().getPlayerList().getViewDistance();
		double radiusBlocks = viewDistance * 16.0;

		Set<ServerPlayer> nearbyPlayers = getNearbyPlayers(player, radiusBlocks);

		if (!nearbyPlayers.isEmpty()) {
			removeDisplay(playerId);
			createLogoffDisplay(playerId, playerName, position, nearbyPlayers, player);
		}
	}

	private void onJoin(ServerPlayer player) {
		removeDisplay(player.getUUID());
	}

	private Set<ServerPlayer> getNearbyPlayers(ServerPlayer logoffPlayer, double radius) {
		Set<ServerPlayer> nearbyPlayers = new HashSet<>();

		for (ServerPlayer otherPlayer : logoffPlayer.level().getServer().getPlayerList().getPlayers()) {
			if (otherPlayer != logoffPlayer &&
					otherPlayer.level() == logoffPlayer.level()) {

				Vec3 logoffPos = logoffPlayer.position();
				Vec3 otherPos = otherPlayer.position();

				double deltaX = logoffPos.x - otherPos.x;
				double deltaZ = logoffPos.z - otherPos.z;
				double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

				if (horizontalDistance <= radius) {
					nearbyPlayers.add(otherPlayer);
				}
			}
		}

		return nearbyPlayers;
	}

	private void createLogoffDisplay(UUID playerId, String playerName, Vec3 position, Set<ServerPlayer> authorizedViewers, ServerPlayer logoffPlayer) {
		ElementHolder holder = new ElementHolder();
		TextDisplayElement textElement = new TextDisplayElement();

		Component nameText = Component.literal(playerName).withStyle(style -> style.withColor(PandaLogoffSpotConfig.getNameColor()).withBold(true));
		Component logoffText = Component.literal("\nLogoff Spot").withStyle(style -> style.withColor(0xFFFFFF));

		MutableComponent displayText = Component.empty().append(nameText).append(logoffText);

		if (PandaLogoffSpotConfig.shouldShowCoords()) {
			Component coordsText = Component.literal(String.format("\n%.1f, %.1f, %.1f", position.x, position.y, position.z))
					.withStyle(style -> style.withColor(0xAAAAAA));
			displayText = displayText.append(coordsText);
		}

		textElement.setText(displayText);
		textElement.setScale(new Vector3f(PandaLogoffSpotConfig.getScale(), PandaLogoffSpotConfig.getScale(), PandaLogoffSpotConfig.getScale()));
		textElement.setBillboardMode(Display.BillboardConstraints.CENTER);

		holder.addElement(textElement);

		ManualAttachment attachment = new ManualAttachment(holder, logoffPlayer.level(), () -> position);

		Set<UUID> authorizedUuids = new HashSet<>();

		for (ServerPlayer viewer : authorizedViewers) {
			authorizedUuids.add(viewer.getUUID());
			attachment.startWatching(viewer);
		}

		ScheduledFuture<?> removalTask = scheduler.schedule(() -> {
			removeDisplay(playerId);
		}, PandaLogoffSpotConfig.getDurationSeconds(), TimeUnit.SECONDS);

		LogoffDisplay display = new LogoffDisplay(holder, attachment, authorizedUuids, removalTask);
		activeDisplays.put(playerId, display);
	}

	private void removeDisplay(UUID playerId) {
		LogoffDisplay display = activeDisplays.remove(playerId);
		if (display != null) {
			display.removalTask.cancel(false);
			display.attachment.destroy();
		}
	}

	private static class LogoffDisplay {
		final ElementHolder holder;
		final ManualAttachment attachment;
		final Set<UUID> authorizedViewers;
		final ScheduledFuture<?> removalTask;

		LogoffDisplay(ElementHolder holder, ManualAttachment attachment, Set<UUID> authorizedViewers, ScheduledFuture<?> removalTask) {
			this.holder = holder;
			this.attachment = attachment;
			this.authorizedViewers = authorizedViewers;
			this.removalTask = removalTask;
		}
	}
}
