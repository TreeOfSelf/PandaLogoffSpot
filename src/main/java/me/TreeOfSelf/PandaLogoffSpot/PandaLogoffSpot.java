package me.TreeOfSelf.PandaLogoffSpot;

import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import me.drex.vanish.api.VanishAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PandaLogoffSpot implements ModInitializer {
	public static final String MOD_ID = "panda-logoff-spot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final Map<UUID, LogoffDisplay> activeDisplays = new HashMap<>();

	@Override
	public void onInitialize() {
		PandaLogoffSpotConfig.load();
		ServerPlayerEvents.JOIN.register(this::onJoin);
		ServerPlayerEvents.LEAVE.register(this::onLeave);
		ServerTickEvents.END_SERVER_TICK.register(this::onTick);
	}

	// A ManualAttachment is never ticked by Polymer: tick the holders here (OccludedHolder rechecks who can see them) and
	// remove the ones that ran out
	private void onTick(MinecraftServer server) {
		Iterator<LogoffDisplay> iterator = activeDisplays.values().iterator();
		while (iterator.hasNext()) {
			LogoffDisplay display = iterator.next();
			if (server.getTickCount() >= display.expiresAt) {
				iterator.remove();
				display.attachment.destroy();
			} else {
				display.holder.tick();
			}
		}
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
		// Hidden from players PandaOcclusion says can't see a player standing there
		OccludedHolder holder = new OccludedHolder(logoffPlayer.level(), 0.6, 1.8, 0.0);
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

		long expiresAt = logoffPlayer.level().getServer().getTickCount() + PandaLogoffSpotConfig.getDurationSeconds() * 20L;
		LogoffDisplay display = new LogoffDisplay(holder, attachment, authorizedUuids, expiresAt);
		activeDisplays.put(playerId, display);
	}

	private void removeDisplay(UUID playerId) {
		LogoffDisplay display = activeDisplays.remove(playerId);
		if (display != null) {
			display.attachment.destroy();
		}
	}

	private static class LogoffDisplay {
		final OccludedHolder holder;
		final ManualAttachment attachment;
		final Set<UUID> authorizedViewers;
		final long expiresAt;

		LogoffDisplay(OccludedHolder holder, ManualAttachment attachment, Set<UUID> authorizedViewers, long expiresAt) {
			this.holder = holder;
			this.attachment = attachment;
			this.authorizedViewers = authorizedViewers;
			this.expiresAt = expiresAt;
		}
	}
}
