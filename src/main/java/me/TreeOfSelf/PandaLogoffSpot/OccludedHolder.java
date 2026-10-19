package me.TreeOfSelf.PandaLogoffSpot;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// An ElementHolder that only shows to players PandaOcclusion says could see it (PandaOcclusionApi.canSee, the same
// check it hides entities behind walls with), rechecked every few ticks. Without PandaOcclusion it is a plain ElementHolder.
public class OccludedHolder extends ElementHolder {
	private static final int RECHECK_TICKS = 4;
	private static final MethodHandle CAN_SEE = findCanSee();

	private final ServerLevel level;
	private final double width;
	private final double height;
	private final double yOffset;
	// Everyone the attachment wants this shown to, including the ones it is hidden from right now. Polymer asks only
	// once (when a player starts tracking the chunk), so a refused player has to be remembered and asked about again.
	private final Set<ServerGamePacketListenerImpl> wanted = new HashSet<>();
	private long nextCheck;

	// The box PandaOcclusion checks: width x height, centered on the holder's position moved up by yOffset
	public OccludedHolder(ServerLevel level, double width, double height, double yOffset) {
		this.level = level;
		this.width = width;
		this.height = height;
		this.yOffset = yOffset;
	}

	@Override
	public boolean startWatching(ServerGamePacketListenerImpl player) {
		if (CAN_SEE == null) {
			return super.startWatching(player);
		}
		this.wanted.add(player);
		return canSee(player.getPlayer()) && super.startWatching(player);
	}

	@Override
	public boolean stopWatching(ServerGamePacketListenerImpl player) {
		this.wanted.remove(player);
		return super.stopWatching(player);
	}

	@Override
	public void tick() {
		super.tick();
		if (CAN_SEE == null || this.wanted.isEmpty()) {
			return;
		}
		long now = this.level.getServer().getTickCount();
		if (now < this.nextCheck) {
			return;
		}
		this.nextCheck = now + RECHECK_TICKS;
		this.wanted.removeIf(player -> player.getPlayer().hasDisconnected());
		for (ServerGamePacketListenerImpl player : new ArrayList<>(this.wanted)) {
			boolean watching = this.getWatchingPlayers().contains(player);
			boolean see = canSee(player.getPlayer());
			if (see && !watching) {
				super.startWatching(player);
			} else if (!see && watching) {
				super.stopWatching(player);
			}
		}
	}

	private boolean canSee(ServerPlayer player) {
		if (player.level() != this.level) {
			return false;
		}
		Vec3 pos = this.getPos();
		if (pos == null) {
			return true;
		}
		try {
			return (boolean) CAN_SEE.invokeExact(player, AABB.ofSize(pos.add(0, this.yOffset, 0), this.width, this.height, this.width));
		} catch (Throwable e) {
			return true;
		}
	}

	private static MethodHandle findCanSee() {
		if (!FabricLoader.getInstance().isModLoaded("panda-occlusion")) {
			return null;
		}
		try {
			return MethodHandles.publicLookup().findStatic(Class.forName("me.TreeOfSelf.PandaOcclusion.api.PandaOcclusionApi"),
				"canSee", MethodType.methodType(boolean.class, ServerPlayer.class, AABB.class));
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}
}
