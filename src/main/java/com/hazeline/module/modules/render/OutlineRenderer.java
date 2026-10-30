package com.hazeline.module.modules.render;

import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import com.hazeline.gui.Colors;

/**
 * Geometry + colours for {@link BlockOutlines}. Everything is built per frame from the block's shape
 * (a few dozen vertices), so there is nothing to cache or clean up.
 */
final class OutlineRenderer {
	/** Longest line piece when colours change along an edge (smooth gradients / waves). */
	private static final double SEGMENT = 1.0 / 16.0;
	/** Fill boxes are pushed out by this much so they do not z-fight with the block's own faces. */
	private static final double FILL_INFLATE = 0.002;
	/** One full colour cycle takes 4 seconds at speed 1. */
	private static final double CYCLES_PER_SECOND = 0.25;
	private static final long START = System.nanoTime();

	private OutlineRenderer() {
	}

	/** Per-frame values shared by every vertex. */
	private record Frame(BlockOutlines m, int[] set, boolean rainbow, float sat, float bri, double time, boolean spatial,
			boolean breakColor, int finished, float progress, double cx, double cy, double cz, double halfHeight,
			double scale) {
	}

	static void render(BlockOutlines m, VertexConsumerProvider.Immediate consumers, MatrixStack matrices, boolean translucentPass,
			WorldRenderState worldState) {
		OutlineRenderState outline = worldState.outlineRenderState;
		if (outline == null || outline.isTranslucent() != translucentPass) {
			return; // same early-outs as vanilla
		}
		VoxelShape shape = m.shape.get() == BlockOutlines.ShapeMode.FULL_BLOCK ? VoxelShapes.fullCube() : outline.shape();
		if (shape == null || shape.isEmpty()) {
			return;
		}
		BlockPos pos = outline.pos();
		Vec3d cam = worldState.cameraRenderState.pos;
		double ox = pos.getX() - cam.x;
		double oy = pos.getY() - cam.y;
		double oz = pos.getZ() - cam.z;

		float progress = breakProgress(pos);
		BlockOutlines.BreakEffect effect = m.breakEffect.get();
		double scale = 1.0;
		if (progress > 0) {
			float eased = smooth(progress);
			if (effect == BlockOutlines.BreakEffect.GROW) {
				scale = 0.05 + 0.95 * eased;
			} else if (effect == BlockOutlines.BreakEffect.SHRINK) {
				scale = 1.0 - 0.95 * eased;
			}
		}

		Box bb = shape.getBoundingBox();
		double cx = (bb.minX + bb.maxX) / 2, cy = (bb.minY + bb.maxY) / 2, cz = (bb.minZ + bb.maxZ) / 2;
		BlockOutlines.Animation anim = m.animation.get();
		boolean multi = m.multiColor();
		double seconds = (System.nanoTime() - START) / 1.0e9;
		double time = multi && anim != BlockOutlines.Animation.STATIC
				? seconds * m.speed.get() * CYCLES_PER_SECOND * (m.reverse.get() ? -1 : 1) : 0;
		boolean spatial = multi && anim != BlockOutlines.Animation.FADE;
		Frame f = new Frame(m, m.colourSet(), m.palette.get() == OutlinePalette.RAINBOW,
				m.rainbowSaturation.getFloat() / 100f, m.rainbowBrightness.getFloat() / 100f, time, spatial,
				m.breakColor.get(), m.finishedColor.get(), progress, cx, cy, cz, Math.max(0.05, (bb.maxY - bb.minY) / 2), scale);

		MatrixStack.Entry entry = matrices.peek();
		boolean fillUp = effect == BlockOutlines.BreakEffect.FILL_UP && progress > 0;
		// While the break animation runs the box is inside the block (Grow / Shrink) or its top face is
		// (Fill up), so the block's own faces would hide it: draw it on top then.
		boolean animating = progress > 0 && effect != BlockOutlines.BreakEffect.OFF;
		BlockOutlines.OnTop onTopMode = m.onTop.get();
		boolean onTop = onTopMode == BlockOutlines.OnTop.ALWAYS || (onTopMode == BlockOutlines.OnTop.WHILE_BREAKING && animating);

		// Fill first, lines on top. Immediate#getBuffer draws the previous layer when the layer changes.
		if (m.mode.get().fill() || fillUp) {
			double clipTop = fillUp ? bb.minY + (bb.maxY - bb.minY) * progress : Double.POSITIVE_INFINITY;
			float alpha = m.fillOpacity.getFloat() / 100f;
			VertexConsumer quads = consumers.getBuffer(OutlinePipelines.fill(onTop));
			List<Box> boxes = shape.getBoundingBoxes();
			for (Box box : boxes) {
				fillBox(quads, entry, f, box, boxes, clipTop, alpha, ox, oy, oz);
			}
		}
		if (m.mode.get().outline()) {
			float alpha = m.outlineOpacity.getFloat() / 100f;
			float width = MinecraftClient.getInstance().getWindow().getMinimumLineWidth() * m.lineWidth.getFloat();
			VertexConsumer lines = consumers.getBuffer(OutlinePipelines.lines(onTop));
			shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> edge(lines, entry, f, x1, y1, z1, x2, y2, z2, alpha, width, ox, oy, oz));
		}
		consumers.drawCurrentLayer();
	}

	// ------------------------------------------------------------------ break progress

	/** 0 when not breaking this block, else the mining progress 0..1 interpolated inside the tick. */
	static float breakProgress(BlockPos pos) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerInteractionManager im = mc.interactionManager;
		if (im == null || mc.world == null || mc.player == null || !im.isBreakingBlock() || !pos.equals(im.currentBreakingPos)) {
			return 0f;
		}
		float progress = im.currentBreakingProgress;
		BlockState state = mc.world.getBlockState(pos);
		float perTick = state.calcBlockBreakingDelta(mc.player, mc.world, pos);
		if (Float.isFinite(perTick) && perTick > 0) {
			progress += perTick * mc.getRenderTickCounter().getTickProgress(true);
		}
		return Math.max(0f, Math.min(1f, progress));
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	// ------------------------------------------------------------------ colours

	/** Colour of a vertex at local block position (x, y, z) (before scaling). */
	private static int colorAt(Frame f, double x, double y, double z, float alpha) {
		double t = f.time;
		if (f.spatial) {
			// Position around the block: angle around the vertical centre line, plus a little height so
			// top and bottom edges differ. Goes 0..1 once around, so the colours travel round the outline.
			double around = Math.atan2(z - f.cz, x - f.cx) / (2 * Math.PI) + 0.5;
			double height = (y - f.cy) / f.halfHeight * 0.12;
			t += (around + height) * f.m.spread.get();
		}
		int c = sample(f, t);
		if (f.breakColor && f.progress > 0) {
			c = Colors.lerp(c, f.finished, f.progress);
		}
		return Colors.withAlpha(c, Math.round(Colors.alpha(c) * alpha));
	}

	/** Colour at cycle position t (wraps every 1.0). */
	private static int sample(Frame f, double t) {
		double u = t - Math.floor(t);
		if (f.rainbow) {
			return Colors.fromHsv((float) u, f.sat, f.bri, 255);
		}
		int[] set = f.set;
		if (set.length == 1) {
			return set[0];
		}
		double s = u * set.length;
		int i = (int) Math.floor(s) % set.length;
		float k = (float) (s - Math.floor(s));
		return Colors.lerp(set[i], set[(i + 1) % set.length], k);
	}

	// ------------------------------------------------------------------ geometry

	private static double sx(Frame f, double x) {
		return f.cx + (x - f.cx) * f.scale;
	}

	private static double sy(Frame f, double y) {
		return f.cy + (y - f.cy) * f.scale;
	}

	private static double sz(Frame f, double z) {
		return f.cz + (z - f.cz) * f.scale;
	}

	private static void edge(VertexConsumer vc, MatrixStack.Entry entry, Frame f, double x1, double y1, double z1, double x2,
			double y2, double z2, float alpha, float width, double ox, double oy, double oz) {
		double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-6) {
			return;
		}
		float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);
		int pieces = f.spatial ? Math.max(1, Math.min(16, (int) Math.ceil(len / SEGMENT))) : 1;
		for (int i = 0; i < pieces; i++) {
			double a = i / (double) pieces, b = (i + 1) / (double) pieces;
			double ax = x1 + dx * a, ay = y1 + dy * a, az = z1 + dz * a;
			double bx = x1 + dx * b, by = y1 + dy * b, bz = z1 + dz * b;
			vc.vertex(entry, (float) (sx(f, ax) + ox), (float) (sy(f, ay) + oy), (float) (sz(f, az) + oz))
					.color(colorAt(f, ax, ay, az, alpha)).normal(entry, nx, ny, nz).lineWidth(width);
			vc.vertex(entry, (float) (sx(f, bx) + ox), (float) (sy(f, by) + oy), (float) (sz(f, bz) + oz))
					.color(colorAt(f, bx, by, bz, alpha)).normal(entry, nx, ny, nz).lineWidth(width);
		}
	}

	/** Draws the visible faces of one box of the shape (faces shared with another box are skipped). */
	private static void fillBox(VertexConsumer vc, MatrixStack.Entry entry, Frame f, Box box, List<Box> all, double clipTop,
			float alpha, double ox, double oy, double oz) {
		double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = Math.min(box.maxY, clipTop), z1 = box.maxZ;
		if (y1 <= y0 + 1.0e-6) {
			return;
		}
		boolean clipped = y1 < box.maxY;
		double mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;
		double e = 1.0e-4;
		// -Y, +Y, -Z, +Z, -X, +X
		if (!covered(all, box, mx, y0 - e, mz)) face(vc, entry, f, alpha, ox, oy, oz, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
		if (clipped || !covered(all, box, mx, y1 + e, mz)) face(vc, entry, f, alpha, ox, oy, oz, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0);
		if (!covered(all, box, mx, my, z0 - e)) face(vc, entry, f, alpha, ox, oy, oz, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1);
		if (!covered(all, box, mx, my, z1 + e)) face(vc, entry, f, alpha, ox, oy, oz, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
		if (!covered(all, box, x0 - e, my, mz)) face(vc, entry, f, alpha, ox, oy, oz, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
		if (!covered(all, box, x1 + e, my, mz)) face(vc, entry, f, alpha, ox, oy, oz, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0);
	}

	/** Whether the point lies inside another box of the shape (that face is hidden inside the block). */
	private static boolean covered(List<Box> all, Box self, double x, double y, double z) {
		for (Box b : all) {
			if (b != self && x > b.minX && x < b.maxX && y > b.minY && y < b.maxY && z > b.minZ && z < b.maxZ) {
				return true;
			}
		}
		return false;
	}

	/** One quad; (nx, ny, nz) is the outward face direction used to push it off the block surface. */
	private static void face(VertexConsumer vc, MatrixStack.Entry entry, Frame f, float alpha, double ox, double oy, double oz,
			double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx,
			double dy, double dz, int nx, int ny, int nz) {
		quadVertex(vc, entry, f, alpha, ox, oy, oz, ax, ay, az, nx, ny, nz);
		quadVertex(vc, entry, f, alpha, ox, oy, oz, bx, by, bz, nx, ny, nz);
		quadVertex(vc, entry, f, alpha, ox, oy, oz, cx, cy, cz, nx, ny, nz);
		quadVertex(vc, entry, f, alpha, ox, oy, oz, dx, dy, dz, nx, ny, nz);
	}

	private static void quadVertex(VertexConsumer vc, MatrixStack.Entry entry, Frame f, float alpha, double ox, double oy, double oz,
			double x, double y, double z, int nx, int ny, int nz) {
		vc.vertex(entry, (float) (sx(f, x) + nx * FILL_INFLATE + ox), (float) (sy(f, y) + ny * FILL_INFLATE + oy),
				(float) (sz(f, z) + nz * FILL_INFLATE + oz)).color(colorAt(f, x, y, z, alpha));
	}
}
