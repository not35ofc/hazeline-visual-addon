package com.hazeline.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

/**
 * Projects world positions to GUI (HUD) coordinates for this frame.
 *
 * <p>1.21.11 has no {@code Camera#getViewRotationProjectionMatrix} (that is 26.1+). Instead
 * {@link com.hazeline.mixin.WorldRendererMixin} records, at the start of every
 * {@code WorldRenderer#render}, the exact two matrices vanilla renders the world with: the view
 * rotation ({@code positionMatrix}, built by {@code GameRenderer#renderWorld} from the camera
 * rotation) and the projection (including view bobbing and the hurt tilt). Their product, applied
 * to positions relative to {@code Camera#getCameraPos()}, is the same transform the world uses.
 */
public final class ScreenProjector {
	private final Matrix4f matrix;
	private final Vec3d cam;
	private final float width;
	private final float height;
	private final float focal;
	private final Vector4f scratch = new Vector4f();

	private ScreenProjector(Matrix4f matrix, Vec3d cam, float width, float height, float focal) {
		this.focal = focal;
		this.matrix = matrix;
		this.cam = cam;
		this.width = width;
		this.height = height;
	}

	private static final Matrix4f FRAME_MATRIX = new Matrix4f();
	private static Vec3d frameCamera;
	private static float frameFocal = 1f;

	/** Called by {@code WorldRendererMixin} at the start of each world render (render thread). */
	public static void captureFrame(Matrix4f viewRotation, Matrix4f projection, Vec3d cameraPos) {
		FRAME_MATRIX.set(projection).mul(viewRotation);
		frameCamera = cameraPos;
		frameFocal = projection.m11(); // 1 / tan(vertical fov / 2), incl. FOV effects

	}

	/** Projector for the current frame, or null before the first world frame was rendered. */
	public static ScreenProjector create(DrawContext g) {
		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		if (!camera.isReady() || frameCamera == null) {
			return null;
		}
		return new ScreenProjector(new Matrix4f(FRAME_MATRIX), frameCamera, g.getScaledWindowWidth(), g.getScaledWindowHeight(), frameFocal);
	}

	public Vec3d cameraPosition() {
		return cam;
	}

	/**
	 * GUI pixels per world block for something facing the camera at view depth {@code depth}
	 * (the 5th value of {@link #project}), the same size vanilla's billboarded labels have there.
	 */
	public float pixelsPerBlock(float depth) {
		return depth <= 0.05f ? 0f : focal * (height / 2f) / depth;
	}

	/**
	 * GUI coordinates {x, y, ndcX, ndcY, depth} of a world position, or null if it is behind the camera.
	 * ndcX/ndcY are in [-1, 1] while on screen; depth is the distance along the view direction.
	 */
	public float[] project(double x, double y, double z) {
		scratch.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
		matrix.transform(scratch);
		if (scratch.w <= 0.05f) {
			return null;
		}
		float nx = scratch.x / scratch.w;
		float ny = scratch.y / scratch.w;
		return new float[] {(nx * 0.5f + 0.5f) * width, (1f - (ny * 0.5f + 0.5f)) * height, nx, ny, scratch.w};
	}
}
