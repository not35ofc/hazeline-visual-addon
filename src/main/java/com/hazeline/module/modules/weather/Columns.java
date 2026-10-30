package com.hazeline.module.modules.weather;

import java.util.Arrays;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * Per-column facts the weather renderers need (where rain stops, how bright it is there, what falls
 * there, what the ground is made of), cached for a few ticks so thousands of drops per frame cost
 * only hash lookups. A direct-mapped 256x256 grid: indexes are the low 8 bits of x and z, so any
 * window up to 256 blocks wide never collides with itself.
 */
final class Columns {
	static final int SIZE = 256;
	/** Ticks a cached column stays valid. */
	private static final int LIFETIME = 12;

	static final byte KIND_NONE = 0, KIND_RAIN = 1, KIND_SNOW = 2;
	/** Ground type of the surface block. */
	static final byte MAT_NONE = 0, MAT_HARD = 1, MAT_NATURAL = 2, MAT_WATER = 3;

	private static final int[] KX = new int[SIZE * SIZE];
	private static final int[] KZ = new int[SIZE * SIZE];
	private static final int[] STAMP = new int[SIZE * SIZE];
	/** First free Y above the highest motion-blocking block (where rain stops). */
	static final int[] TOP = new int[SIZE * SIZE];
	/** Packed block/sky light at the top of the column. */
	static final int[] LIGHT = new int[SIZE * SIZE];
	/** Y of the surface the sheen / ripples sit on (the water level for water). */
	static final float[] SURFACE = new float[SIZE * SIZE];
	static final byte[] KIND = new byte[SIZE * SIZE];
	static final byte[] MAT = new byte[SIZE * SIZE];

	private static ClientWorld world;
	private static int tick;
	private static final BlockPos.Mutable POS = new BlockPos.Mutable();

	private Columns() {
	}

	/** Call once per frame before any {@link #get}. */
	static void frame(ClientWorld w) {
		if (w != world) {
			world = w;
			Arrays.fill(STAMP, Integer.MIN_VALUE / 2);
		}
		tick = (int) w.getTime();
	}

	static int index(int x, int z) {
		return ((x & 255) << 8) | (z & 255);
	}

	/** Cache slot of the column, filled if needed; -1 when the chunk is not loaded. */
	static int get(ClientWorld w, int x, int z) {
		int i = index(x, z);
		if (KX[i] == x && KZ[i] == z && Math.abs(tick - STAMP[i]) < LIFETIME) {
			return i;
		}
		if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
			return -1;
		}
		fill(w, i, x, z);
		return i;
	}

	private static void fill(ClientWorld w, int i, int x, int z) {
		int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		KX[i] = x;
		KZ[i] = z;
		STAMP[i] = tick;
		TOP[i] = top;
		POS.set(x, top, z);
		LIGHT[i] = WorldRenderer.getLightmapCoordinates(w, POS);
		Biome biome = w.getBiome(POS).value();
		Biome.Precipitation p = biome.getPrecipitation(POS, w.getSeaLevel());
		KIND[i] = p == Biome.Precipitation.RAIN ? KIND_RAIN : p == Biome.Precipitation.SNOW ? KIND_SNOW : KIND_NONE;

		POS.set(x, top - 1, z);
		BlockState state = w.getBlockState(POS);
		FluidState fluid = state.getFluidState();
		SURFACE[i] = top;
		if (!fluid.isEmpty()) {
			MAT[i] = MAT_WATER;
			SURFACE[i] = top - 1 + fluid.getHeight(w, POS);
		} else if (state.isAir() || !state.isSideSolidFullSquare(w, POS, Direction.UP)) {
			MAT[i] = MAT_NONE;
		} else if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND)) {
			MAT[i] = MAT_NATURAL;
		} else {
			MAT[i] = MAT_HARD;
		}
	}

	/** The cache as a {@link WeatherGeometry.ColumnSource} for one world. */
	static WeatherGeometry.ColumnSource source(ClientWorld w) {
		return new WeatherGeometry.ColumnSource() {
			@Override
			public int get(int x, int z) {
				return Columns.get(w, x, z);
			}

			@Override
			public int top(int slot) {
				return TOP[slot];
			}

			@Override
			public int light(int slot) {
				return LIGHT[slot];
			}

			@Override
			public byte kind(int slot) {
				return KIND[slot];
			}

			@Override
			public byte mat(int slot) {
				return MAT[slot];
			}

			@Override
			public float surface(int slot) {
				return SURFACE[slot];
			}
		};
	}
}
