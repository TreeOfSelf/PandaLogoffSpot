package me.TreeOfSelf.PandaLogoffSpot;

import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import me.drex.vanish.api.VanishAPI;

import java.util.*;
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

	private void onLeave(ServerPlayerEntity player) {
		if (FabricLoader.getInstance().isModLoaded("melius-vanish") && VanishAPI.isVanished(player)) {
			return;
		}

		UUID playerId = player.getUuid();
		String playerName = player.getGameProfile().getName();
		Vec3d position = player.getPos().add(new Vec3d(0, player.getBoundingBox(player.getPose()).maxY / 2, 0));

		int viewDistance = player.getServer().getPlayerManager().getViewDistance();
		double radiusBlocks = viewDistance * 16.0;

		Set<ServerPlayerEntity> nearbyPlayers = getNearbyPlayers(player, radiusBlocks);

		if (!nearbyPlayers.isEmpty()) {
			removeDisplay(playerId);
			createLogoffDisplay(playerId, playerName, position, nearbyPlayers, player);
		}
	}

	private void onJoin(ServerPlayerEntity player) {
		removeDisplay(player.getUuid());
	}

	private Set<ServerPlayerEntity> getNearbyPlayers(ServerPlayerEntity logoffPlayer, double radius) {
		Set<ServerPlayerEntity> nearbyPlayers = new HashSet<>();

		for (ServerPlayerEntity otherPlayer : logoffPlayer.getServer().getPlayerManager().getPlayerList()) {
			if (otherPlayer != logoffPlayer &&
					otherPlayer.getWorld() == logoffPlayer.getWorld()) {

				Vec3d logoffPos = logoffPlayer.getPos();
				Vec3d otherPos = otherPlayer.getPos();

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

	private void createLogoffDisplay(UUID playerId, String playerName, Vec3d position, Set<ServerPlayerEntity> authorizedViewers, ServerPlayerEntity logoffPlayer) {
		ElementHolder holder = new ElementHolder();
		TextDisplayElement textElement = new TextDisplayElement();

		Text nameText = Text.literal(playerName).styled(style -> style.withColor(PandaLogoffSpotConfig.getNameColor()).withBold(true));
		Text logoffText = Text.literal("\nLogoff Spot").styled(style -> style.withColor(0xFFFFFF));

		MutableText displayText = Text.empty().append(nameText).append(logoffText);

		if (PandaLogoffSpotConfig.shouldShowCoords()) {
			Text coordsText = Text.literal(String.format("\n%.1f, %.1f, %.1f", position.x, position.y, position.z))
					.styled(style -> style.withColor(0xAAAAAA));
			displayText = displayText.append(coordsText);
		}

		textElement.setText(displayText);
		textElement.setScale(new Vector3f(PandaLogoffSpotConfig.getScale(), PandaLogoffSpotConfig.getScale(), PandaLogoffSpotConfig.getScale()));
		textElement.setBillboardMode(DisplayEntity.BillboardMode.CENTER);

		holder.addElement(textElement);

		ManualAttachment attachment = new ManualAttachment(holder, logoffPlayer.getWorld(), () -> position);

		Set<UUID> authorizedUuids = new HashSet<>();

		for (ServerPlayerEntity viewer : authorizedViewers) {
			authorizedUuids.add(viewer.getUuid());
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