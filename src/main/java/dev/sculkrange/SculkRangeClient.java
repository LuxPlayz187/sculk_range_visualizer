package dev.sculkrange;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gizmos.Gizmos;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;

public class SculkRangeClient implements ClientModInitializer {
	public static final String MOD_ID = "sculkrange";

	private static final int SCAN_CHUNK_RADIUS = 3;
	private static final double MAX_DISTANCE = SCAN_CHUNK_RADIUS * 16.0;
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final int SENSOR_RANGE = 8;
	private static final int CALIBRATED_RANGE = 16;
	private static final int SEGMENTS = 24;
	private static final float LINE_WIDTH = 2.0f;
	private static final int COLOR_SENSOR = 0xB033E6E6;
	private static final int COLOR_CALIBRATED = 0xB02FBF9B;

	private record Sensor(Vec3 center, int radius, int color) {}

	private static final double[] COS = new double[SEGMENTS + 1];
	private static final double[] SIN = new double[SEGMENTS + 1];
	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double a = (Math.PI * 2.0 * i) / SEGMENTS;
			COS[i] = Math.cos(a);
			SIN[i] = Math.sin(a);
		}
	}

	private static KeyMapping toggleKey;
	private static boolean enabled = false;
	private static int tickCounter = 0;
	private static volatile List<Sensor> sensors = List.of();

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.sculkrange.toggle", InputConstants.KEY_V, category));

		ClientTickEvents.END_CLIENT_TICK.register(SculkRangeClient::onTick);
		LevelExtractionEvents.END_EXTRACTION.register(context -> drawSensors());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sensors = List.of());
	}

	private static void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			tickCounter = 0;
			if (enabled) {
				scan(client);
			} else {
				sensors = List.of();
			}
			if (client.player != null) {
				client.player.sendOverlayMessage(
					Component.translatable(enabled ? "message.sculkrange.enabled" : "message.sculkrange.disabled"));
			}
		}

		if (!enabled) {
			return;
		}
		if (client.level == null || client.player == null) {
			sensors = List.of();
			return;
		}
		if (++tickCounter >= SCAN_INTERVAL_TICKS) {
			tickCounter = 0;
			scan(client);
		}
	}

	private static void scan(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			sensors = List.of();
			return;
		}

		Vec3 eye = player.position();
		int pcx = player.blockPosition().getX() >> 4;
		int pcz = player.blockPosition().getZ() >> 4;
		double maxSq = MAX_DISTANCE * MAX_DISTANCE;

		List<Sensor> found = new ArrayList<>();
		for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
			for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
				ChunkAccess access = level.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
				if (!(access instanceof LevelChunk chunk)) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof SculkSensorBlockEntity)) {
						continue;
					}
					Vec3 center = Vec3.atCenterOf(be.getBlockPos());
					if (center.distanceToSqr(eye) > maxSq) {
						continue;
					}
					boolean calibrated = be instanceof CalibratedSculkSensorBlockEntity;
					found.add(new Sensor(center,
						calibrated ? CALIBRATED_RANGE : SENSOR_RANGE,
						calibrated ? COLOR_CALIBRATED : COLOR_SENSOR));
				}
			}
		}
		sensors = List.copyOf(found);
	}

	private static void drawSensors() {
		if (!enabled) {
			return;
		}
		for (Sensor s : sensors) {
			drawRings(s);
		}
	}

	private static void drawRings(Sensor s) {
		Vec3 c = s.center();
		double r = s.radius();
		for (int i = 0; i < SEGMENTS; i++) {
			double c0 = COS[i] * r, s0 = SIN[i] * r;
			double c1 = COS[i + 1] * r, s1 = SIN[i + 1] * r;
			Gizmos.line(c.add(c0, 0, s0), c.add(c1, 0, s1), s.color(), LINE_WIDTH);
			Gizmos.line(c.add(c0, s0, 0), c.add(c1, s1, 0), s.color(), LINE_WIDTH);
			Gizmos.line(c.add(0, c0, s0), c.add(0, c1, s1), s.color(), LINE_WIDTH);
		}
	}
}
