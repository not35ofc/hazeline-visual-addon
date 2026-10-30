package com.hazeline.gui.text;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.texture.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

/**
 * Glyph atlas pages as GPU textures (1.21.11 blaze3d API).
 *
 * <ul>
 *   <li>Format: {@code RED8} (one byte of coverage per texel) when the coverage shader compiled,
 *       else {@code RGBA8} with white RGB and coverage in alpha for vanilla's
 *       {@code GUI_TEXTURED} pipeline (see {@link TextPipelines}).</li>
 *   <li>Sampling: linear min/mag filter, clamp to edge, no mipmaps
 *       ({@code SamplerCache#get(LINEAR)}, clamp to edge).</li>
 *   <li>Every page is zero-filled when created, so the padding between glyphs is really empty.</li>
 *   <li>Uploads go through {@code CommandEncoder#writeToTexture(ByteBuffer, ...)}; the GL backend
 *       sets {@code GL_UNPACK_ALIGNMENT} to the format's byte size, so tightly packed 1-byte rows
 *       are uploaded correctly.</li>
 * </ul>
 */
public final class GpuPageStore implements GlyphAtlas.PageStore {
	private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

	private record Page(GpuTexture texture, GpuTextureView view, int size) {
	}

	private final boolean singleChannel;
	private final Map<Integer, Page> pages = new HashMap<>();
	private int nextHandle = 1;
	private int pagesCreated;

	public GpuPageStore(boolean singleChannel) {
		this.singleChannel = singleChannel;
	}

	public boolean isSingleChannel() {
		return singleChannel;
	}

	public int pagesCreated() {
		return pagesCreated;
	}

	public int livePages() {
		return pages.size();
	}

	public GpuTextureView view(int handle) {
		Page page = pages.get(handle);
		return page == null ? null : page.view();
	}

	public static GpuSampler sampler() {
		return RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
	}

	@Override
	public int createPage(int size) {
		GpuDevice device = RenderSystem.getDevice();
		int handle = nextHandle++;
		GpuTexture texture = device.createTexture("Hazeline glyph atlas #" + handle, USAGE,
				singleChannel ? TextureFormat.RED8 : TextureFormat.RGBA8, size, size, 1, 1);
		GpuTextureView view = device.createTextureView(texture);
		ByteBuffer zeros = MemoryUtil.memCalloc(size * size * bytesPerTexel());
		try {
			device.createCommandEncoder().writeToTexture(texture, zeros, format(), 0, 0, 0, 0, size, size);
		} finally {
			MemoryUtil.memFree(zeros);
		}
		pages.put(handle, new Page(texture, view, size));
		pagesCreated++;
		return handle;
	}

	@Override
	public void upload(int handle, int x, int y, int width, int height, byte[] coverage) {
		Page page = pages.get(handle);
		if (page == null) {
			return;
		}
		ByteBuffer buffer = MemoryUtil.memAlloc(width * height * bytesPerTexel());
		try {
			if (singleChannel) {
				buffer.put(coverage, 0, width * height);
			} else {
				for (int i = 0; i < width * height; i++) {
					buffer.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(coverage[i]);
				}
			}
			buffer.flip();
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(page.texture(), buffer, format(), 0, 0, x, y, width, height);
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	@Override
	public void deletePage(int handle) {
		Page page = pages.remove(handle);
		if (page != null) {
			page.view().close();
			page.texture().close();
		}
	}

	@Override
	public int bytesPerTexel() {
		return singleChannel ? 1 : 4;
	}

	private NativeImage.Format format() {
		return singleChannel ? NativeImage.Format.LUMINANCE : NativeImage.Format.RGBA;
	}

	/** Deletes every page (used when the whole store is replaced). */
	public void closeAll() {
		for (Integer handle : pages.keySet().toArray(new Integer[0])) {
			deletePage(handle);
		}
	}
}
