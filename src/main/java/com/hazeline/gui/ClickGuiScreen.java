package com.hazeline.gui;

import java.util.List;
import java.util.Optional;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.util.InputUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import com.hazeline.Hazeline;
import com.hazeline.appearance.Appearance;
import com.hazeline.appearance.SoundAction;
import com.hazeline.appearance.SoundManager;
import com.hazeline.config.ConfigManager;
import com.hazeline.gui.component.TextField;
import com.hazeline.gui.page.KeybindsPage;
import com.hazeline.gui.page.ModulePage;
import com.hazeline.gui.page.ModulesPage;
import com.hazeline.gui.page.Page;
import com.hazeline.gui.page.SettingsPage;
import com.hazeline.gui.page.ThemePage;
import com.hazeline.module.Category;
import com.hazeline.module.Module;
import com.hazeline.module.ModuleManager;
import com.hazeline.module.modules.music.MusicHud;
import com.hazeline.module.modules.render.HandView;
import com.hazeline.module.modules.settings.ClickGuiModule;

/**
 * The "Hazeline" menu: one centred glass window.
 *
 * <ul>
 *   <li><b>Sidebar</b>: the logo, the module categories (with how many modules each has) and the
 *       general pages Settings, Theme and Keybinds.</li>
 *   <li><b>Content</b>: a title, a search box and list/grid switch on module pages, and the
 *       current {@link Page} in a smoothly scrolling, clipped area.</li>
 *   <li><b>Effects</b>: background blur/dim, floating {@link Particles} behind the window, a soft
 *       light that follows the mouse inside it, a fade/slide when the menu opens and when pages
 *       change, and eased hover/switch animations everywhere ({@link Anim}).</li>
 * </ul>
 *
 * <p>1.21.11 API notes (Yarn): rendering is {@code render(DrawContext, ...)} after
 * {@code renderBackground}, and input arrives as {@link Click} / {@link KeyInput} /
 * {@link CharInput} records. The background blur uses {@code DrawContext#applyBlur()}.
 */
public class ClickGuiScreen extends Screen {
	/** Order of the module categories in the sidebar. */
	private static final List<Category> CATEGORIES = List.of(Category.RENDER, Category.SKY, Category.WEATHER, Category.MENUS, Category.SOUNDS, Category.MUSIC);

	private enum General {
		SETTINGS("Settings", Icons.Icon.SLIDERS), THEME("Theme", Icons.Icon.PALETTE), KEYBINDS("Keybinds", Icons.Icon.KEYBOARD);

		final String label;
		final Icons.Icon icon;

		General(String label, Icons.Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	// Remembered while the game runs, so the menu reopens where you left it.
	private static Category lastCategory = Category.RENDER;
	private static General lastGeneral;
	private static boolean gridView;

	private static String tooltip;

	private final Particles particles = new Particles();
	private final TextField search;
	private String query = "";
	private Page page;
	private Category category;
	private General general;
	private float scroll;
	private float scrollTarget;
	private long openedAt;
	private long pageChangedAt;
	/** Drawn over another screen by {@link GuiOverlay} instead of being the current screen. */
	private boolean overlayMode;

	public ClickGuiScreen() {
		super(Text.literal("Hazeline"));
		this.search = new TextField(40, cp -> cp >= 32 && cp != 127, text -> query = text);
		if (lastGeneral != null) {
			showGeneral(lastGeneral);
		} else {
			showCategory(lastCategory);
		}
	}

	/** Components call this while hovered during rendering; the last call wins. */
	public static void setTooltip(String text) {
		tooltip = text;
	}

	static String takeTooltip() {
		String t = tooltip;
		tooltip = null;
		return t;
	}

	static void clearTooltip() {
		tooltip = null;
	}

	void setOverlayMode(boolean overlay) {
		this.overlayMode = overlay;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
			Anim.reset();
		}
	}

	// ======================================================================== navigation

	private String currentQuery() {
		return search.isFocused() ? search.getText() : query;
	}

	private void showCategory(Category c) {
		category = c;
		general = null;
		lastCategory = c;
		lastGeneral = null;
		setPage(new ModulesPage(c, this::currentQuery, () -> gridView, this::openModule));
	}

	private void showGeneral(General g) {
		general = g;
		category = null;
		lastGeneral = g;
		setPage(switch (g) {
			case SETTINGS -> new SettingsPage();
			case THEME -> new ThemePage();
			case KEYBINDS -> new KeybindsPage();
		});
	}

	private void openModule(Module module) {
		Page parent = page;
		setPage(new ModulePage(module, parent, () -> setPage(parent)));
	}

	private void setPage(Page next) {
		page = next;
		scroll = 0;
		scrollTarget = 0;
		pageChangedAt = System.currentTimeMillis();
	}

	/** While text is typed into the search box, results come from every category. */
	private Page effectivePage() {
		if (page instanceof ModulesPage modules && !currentQuery().isBlank() && modules.category() != null) {
			if (searchPage == null) {
				searchPage = new ModulesPage(null, this::currentQuery, () -> gridView, this::openModule);
			}
			return searchPage;
		}
		return page;
	}

	private ModulesPage searchPage;

	// ======================================================================== layout

	private int windowW() {
		double pct = MathHelper.clamp(layout().windowSize.get(), 50, 95) / 100.0;
		return Math.min(width - Theme.px(8), Math.max(Theme.px(400), (int) Math.round(width * pct)));
	}

	private int windowH() {
		double pct = MathHelper.clamp(layout().windowSize.get(), 50, 95) / 100.0;
		return Math.min(height - Theme.px(8), Math.max(Theme.px(250), (int) Math.round(height * pct)));
	}

	private int windowX() {
		return (width - windowW()) / 2;
	}

	private int windowY() {
		return (height - windowH()) / 2;
	}

	private static int margin() {
		return Theme.px(7);
	}

	private int sidebarW() {
		return Math.min(Theme.px(118), windowW() / 3);
	}

	private int sidebarX() {
		return windowX() + margin();
	}

	private int sidebarY() {
		return windowY() + margin();
	}

	private int sidebarH() {
		return windowH() - margin() * 2;
	}

	private int contentX() {
		return sidebarX() + sidebarW() + Theme.px(12);
	}

	private int contentW() {
		return windowX() + windowW() - Theme.px(12) - contentX();
	}

	private int headerY() {
		return windowY() + Theme.px(12);
	}

	private int headerH() {
		return Theme.px(30);
	}

	private int viewportY() {
		return headerY() + headerH() + Theme.px(8);
	}

	private int viewportBottom() {
		return windowY() + windowH() - Theme.px(10);
	}

	private int viewportH() {
		return Math.max(0, viewportBottom() - viewportY());
	}

	private int[] searchRect() {
		int w = Math.min(Theme.px(130), contentW() / 2);
		int h = Theme.px(15);
		return new int[] {contentX() + contentW() - w, headerY() + Theme.px(3), w, h};
	}

	private int[] viewToggleRect(boolean grid) {
		int s = Theme.px(14);
		int x = contentX() + contentW() - s * 2 - Theme.px(3);
		int[] search = searchRect();
		return new int[] {grid ? x + s + Theme.px(3) : x, search[1] + search[3] + Theme.px(4), s, s};
	}

	private static ClickGuiModule layout() {
		return ModuleManager.get().get(ClickGuiModule.class);
	}

	private int scrollbarW() {
		return Theme.px(3);
	}

	private int maxScroll() {
		return Math.max(0, effectivePage().contentHeight(contentW() - scrollbarW() - Theme.px(4)) - viewportH());
	}

	// ======================================================================== rendering

	@Override
	public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
		if (overlayMode) {
			// Over another screen: only dim it (no blur - a frame may only be blurred once, and the
			// screen underneath may already have done it).
			if (!HandView.isPreviewing()) {
				Theme.setGlobalAlpha(openProgress());
				try {
					g.fill(0, 0, width, height, Theme.screenDim());
				} finally {
					Theme.setGlobalAlpha(1f);
				}
			}
			return;
		}
		if (MinecraftClient.getInstance().world == null) {
			return; // title screen: nothing to blur or dim
		}
		if (HandView.isPreviewing()) {
			return; // Hand View live preview: keep your hand clearly visible behind the menu
		}
		if (Appearance.get().backgroundBlur.get()) {
			g.applyBlur(); // strength = vanilla "Menu Background Blur" option
		}
		float open = openProgress();
		Theme.setGlobalAlpha(open);
		try {
			g.fill(0, 0, width, height, Theme.screenDim());
		} finally {
			Theme.setGlobalAlpha(1f);
		}
	}

	private float openProgress() {
		if (!Theme.animations()) {
			return 1f;
		}
		return Anim.easeOutCubic((System.currentTimeMillis() - openedAt) / 260f);
	}

	private float pageProgress() {
		if (!Theme.animations()) {
			return 1f;
		}
		return Anim.easeOutCubic((System.currentTimeMillis() - pageChangedAt) / 200f);
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		Theme.beginFrame();
		Anim.beginFrame();
		clearTooltip();
		float open = openProgress();
		try {
			particles.render(g, width, height, mouseX, mouseY, open);
			Theme.setGlobalAlpha(open);
			Matrix3x2fStack pose = g.getMatrices();
			pose.pushMatrix();
			pose.translate(0f, (1f - open) * Theme.px(10));
			renderWindow(g, mouseX, mouseY, delta, open);
			pose.popMatrix();
			// Music HUD popup on top of the menu (in a world), where it can be dragged.
			MusicHud.renderInClickGui(g, mouseX, mouseY);
			Theme.setGlobalAlpha(open);
			super.render(g, mouseX, mouseY, delta);
			Theme.tooltip(g, takeTooltip(), mouseX, mouseY, width, height);
			Theme.debugOverlay(g, width, height);
		} finally {
			Theme.setGlobalAlpha(1f);
		}
	}

	private void renderWindow(DrawContext g, int mouseX, int mouseY, float delta, float open) {
		int x = windowX();
		int y = windowY();
		int w = windowW();
		int h = windowH();
		int r = Math.round(Theme.cornerRadius() * 1.6f);

		// Soft shadow, glass body, outline.
		for (int i = 3; i >= 1; i--) {
			int s = Theme.px(2.5f) * i;
			Theme.roundedRect(g, x - s, y - s + Theme.px(2), x + w + s, y + h + s + Theme.px(2), r + s,
					Theme.fade(Colors.withAlpha(0xFF000000, 18 - i * 4)));
		}
		Theme.roundedRect(g, x, y, x + w, y + h, r, Theme.panelBackground());
		Theme.roundedOutline(g, x, y, w, h, r, Theme.border());

		// Light that follows the mouse inside the window, and a faint fixed one top-left.
		g.enableScissor(x, y, x + w, y + h);
		Shapes.glow(g, x + w * 0.18f, y + h * 0.05f, Math.max(w, h) * 0.45f, Theme.accent(28));
		if (Theme.isHovered(mouseX, mouseY, x, y, w, h)) {
			float gx = Anim.approach(this, 0, mouseX, 10f);
			float gy = Anim.approach(this, 1, mouseY, 10f);
			Shapes.glow(g, gx, gy, Theme.px(130), Theme.accent(34));
		}
		g.disableScissor();

		renderSidebar(g, mouseX, mouseY);
		renderHeader(g, mouseX, mouseY);
		renderContent(g, mouseX, mouseY, delta, open);
	}

	// ------------------------------------------------------------------------ sidebar

	private static String version() {
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Hazeline.MOD_ID);
		return mod.map(m -> "v" + m.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	private int itemH() {
		return Theme.px(17);
	}

	private int logoH() {
		return Theme.px(30);
	}

	private int sectionH() {
		return Theme.px(16);
	}

	/** Y of the category item {@code index}. */
	private int categoryY(int index) {
		return sidebarY() + logoH() + sectionH() + index * itemH();
	}

	private int generalY(int index) {
		return categoryY(CATEGORIES.size()) + Theme.px(6) + sectionH() + index * itemH();
	}

	private void renderSidebar(DrawContext g, int mouseX, int mouseY) {
		int x = sidebarX();
		int y = sidebarY();
		int w = sidebarW();
		int h = sidebarH();
		int r = Math.round(Theme.cornerRadius() * 1.3f);
		Theme.roundedRect(g, x, y, x + w, y + h, r, Theme.sidebar());
		Theme.roundedOutline(g, x, y, w, h, r, Colors.withAlpha(Theme.border(), Colors.alpha(Theme.border()) * 2 / 3));

		// Logo.
		int icon = Theme.px(11);
		int lx = x + Theme.px(8);
		int ly = y + Theme.px(9);
		Shapes.glow(g, lx + icon / 2f, ly + icon / 2f, icon * 1.4f, Theme.accent(70));
		Icons.draw(g, Icons.Icon.LOGO, lx, ly, icon, Theme.accent());
		int tx = lx + icon + Theme.px(5);
		Theme.textOf(g, "Hazeline", tx, ly - Theme.px(1), Theme.text(), 1.05, true);
		Theme.textOf(g, " Client", tx + Theme.widthOf("Hazeline", 1.05, true), ly - Theme.px(1), Theme.accent(), 1.05, false);
		Theme.textOf(g, version(), tx, ly - Theme.px(1) + Theme.heightOf(1.05) + Theme.px(0.5f), Theme.textSecondary(), 0.65, false);

		sectionLabel(g, "MODULES", x, categoryY(0) - sectionH());
		for (int i = 0; i < CATEGORIES.size(); i++) {
			Category c = CATEGORIES.get(i);
			int count = (int) ModuleManager.get().getModules(c).stream().filter(m -> m.getCategory() != Category.SETTINGS).count();
			sidebarItem(g, c, Icons.of(c), c.getDisplayName(), String.valueOf(count), categoryY(i), c == category && searchQueryEmpty(),
					mouseX, mouseY);
		}
		sectionLabel(g, "GENERAL", x, generalY(0) - sectionH());
		General[] generals = General.values();
		for (int i = 0; i < generals.length; i++) {
			sidebarItem(g, generals[i], generals[i].icon, generals[i].label, null, generalY(i), generals[i] == general, mouseX, mouseY);
		}
	}

	private boolean searchQueryEmpty() {
		return currentQuery().isBlank();
	}

	private void sectionLabel(DrawContext g, String label, int x, int y) {
		Theme.boxTextOf(g, label, x + Theme.px(9), y + Theme.px(3), sectionH() - Theme.px(3), Theme.textSecondary(), 0.62, true);
	}

	private void sidebarItem(DrawContext g, Object key, Icons.Icon icon, String label, String count, int iy, boolean selected,
			int mouseX, int mouseY) {
		int x = sidebarX() + Theme.px(4);
		int w = sidebarW() - Theme.px(8);
		int h = itemH() - Theme.px(2);
		boolean hovered = Theme.isHovered(mouseX, mouseY, x, iy, w, h);
		float hover = Anim.hover(key, hovered);
		float sel = Anim.approach(key, 1, selected ? 1f : 0f, 14f);
		int r = Math.min(Theme.cornerRadius(), h / 2);
		if (sel > 0.01f) {
			Theme.roundedRect(g, x, iy, x + w, iy + h, r, Theme.accent(Math.round(70 * sel)));
			Theme.roundedRect(g, x, iy + Theme.px(4), x + Math.max(2, Theme.px(2)), iy + h - Theme.px(4), Theme.px(1),
					Theme.accent(Math.round(255 * sel)));
		}
		if (hover > 0.01f && !selected) {
			Theme.roundedRect(g, x, iy, x + w, iy + h, r, Colors.withAlpha(Theme.hover(), Math.round(Colors.alpha(Theme.hover()) * hover * 1.4f)));
		}
		int is = Theme.px(8);
		float shift = hover * Theme.px(1.5f);
		int iconColor = Colors.lerp(Colors.lerp(Theme.textSecondary(), Theme.text(), hover), Theme.accent(), sel);
		Icons.draw(g, icon, x + Theme.px(6) + shift, iy + (h - is) / 2f, is, iconColor);
		int textColor = Colors.lerp(Colors.lerp(Theme.textSecondary(), Theme.text(), hover), Theme.text(), sel);
		Theme.boxTextOf(g, label, x + Theme.px(6) + is + Theme.px(6) + shift, iy, h, textColor, 0.92, selected);
		if (count != null) {
			Theme.boxTextOf(g, count, x + w - Theme.px(6) - Theme.widthOf(count, 0.7, false), iy, h, Theme.textSecondary(), 0.7, false);
		}
	}

	// ------------------------------------------------------------------------ header

	private void renderHeader(DrawContext g, int mouseX, int mouseY) {
		Page p = effectivePage();
		int x = contentX();
		int y = headerY();
		String accentWord = p.titleAccent();
		float titleY = y;
		int titleMax = contentW() - (p.showsSearch() ? searchRect()[2] + Theme.px(10) : 0);
		float tx = x;
		if (!accentWord.isEmpty()) {
			Theme.textOf(g, accentWord, tx, titleY, Theme.accent(), Theme.TITLE, true);
			tx += Theme.widthOf(accentWord + " ", Theme.TITLE, true);
		}
		Theme.textOf(g, Theme.fitOf(p.title(), (int) (x + titleMax - tx), Theme.TITLE, true), tx, titleY, Theme.text(), Theme.TITLE, true);
		Theme.textOf(g, Theme.fitOf(p.subtitle(), titleMax, Theme.SMALL, false), x, titleY + Theme.heightOf(Theme.TITLE) + Theme.px(1),
				Theme.textSecondary(), Theme.SMALL, false);

		if (!p.showsSearch()) {
			return;
		}
		int[] s = searchRect();
		int is = Theme.px(8);
		search.render(g, s[0], s[1], s[2], s[3], query.isEmpty() ? "Search modules" : query, is + Theme.px(4));
		Icons.draw(g, Icons.SEARCH, s[0] + Theme.px(5), s[1] + (s[3] - is) / 2f, is,
				search.isFocused() ? Theme.accent() : Theme.textSecondary());

		for (boolean grid : new boolean[] {false, true}) {
			int[] v = viewToggleRect(grid);
			boolean active = gridView == grid;
			boolean hovered = Theme.isHovered(mouseX, mouseY, v[0], v[1], v[2], v[3]);
			float hover = Anim.hover(grid ? Icons.GRID : Icons.LIST, hovered);
			int r = Math.min(Theme.cornerRadius(), v[3] / 3);
			if (active || hover > 0.01f) {
				Theme.roundedRect(g, v[0], v[1], v[0] + v[2], v[1] + v[3], r, active ? Theme.accent(60) : Colors.withAlpha(Theme.hover(),
						Math.round(Colors.alpha(Theme.hover()) * hover * 1.5f)));
			}
			int ic = Theme.px(8);
			Icons.draw(g, grid ? Icons.GRID : Icons.LIST, v[0] + (v[2] - ic) / 2f, v[1] + (v[3] - ic) / 2f, ic,
					active ? Theme.text() : Colors.lerp(Theme.textSecondary(), Theme.text(), hover));
			if (hovered) {
				setTooltip(grid ? "Grid view" : "List view");
			}
		}
	}

	// ------------------------------------------------------------------------ content

	private void renderContent(DrawContext g, int mouseX, int mouseY, float delta, float open) {
		Page p = effectivePage();
		p.tick();
		int x = contentX();
		int vy = viewportY();
		int vh = viewportH();
		int cw = contentW() - scrollbarW() - Theme.px(4);
		int max = maxScroll();
		scrollTarget = MathHelper.clamp(scrollTarget, 0, max);
		scroll = Theme.animations() ? scroll + (scrollTarget - scroll) * (1f - (float) Math.exp(-18f * Anim.dt())) : scrollTarget;
		if (Math.abs(scrollTarget - scroll) < 0.3f) {
			scroll = scrollTarget;
		}
		scroll = MathHelper.clamp(scroll, 0, max);

		float pageP = pageProgress();
		Theme.setGlobalAlpha(open * pageP);
		boolean inside = Theme.isHovered(mouseX, mouseY, x, vy, contentW(), vh);
		int mx = inside ? mouseX : -10000;
		int my = inside ? mouseY : -10000;
		Matrix3x2fStack pose = g.getMatrices();
		pose.pushMatrix();
		pose.translate(0f, (1f - pageP) * Theme.px(6));
		// Small margin so outlines and hover glows at the edges are not cut off.
		int edge = Theme.px(2);
		g.enableScissor(x - edge, vy, x + contentW(), vy + vh);
		p.render(g, x, vy - Math.round(scroll), cw, mx, my, delta);
		g.disableScissor();
		pose.popMatrix();
		Theme.setGlobalAlpha(open);

		// Scrollbar.
		if (max > 0) {
			int total = max + vh;
			int bh = Math.max(Theme.px(16), Math.round(vh * (vh / (float) total)));
			int by = vy + Math.round((vh - bh) * (scroll / max));
			int bx = x + contentW() - scrollbarW();
			Theme.roundedRect(g, bx, vy, bx + scrollbarW(), vy + vh, scrollbarW() / 2, Colors.withAlpha(Theme.border(), Colors.alpha(Theme.border()) / 2));
			Theme.roundedRect(g, bx, by, bx + scrollbarW(), by + bh, scrollbarW() / 2, Theme.accent(170));
		}
	}

	// ======================================================================== input

	private boolean inViewport(double mx, double my) {
		return Theme.isHovered(mx, my, contentX(), viewportY(), contentW(), viewportH());
	}

	@Override
	public boolean mouseClicked(Click event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		if (MusicHud.clickGuiMouseClicked(mx, my, button)) {
			return true;
		}
		Page p = effectivePage();
		p.onGlobalClick(mx, my);

		// Search box.
		if (p.showsSearch()) {
			int[] s = searchRect();
			if (Theme.isHovered(mx, my, s[0], s[1], s[2], s[3])) {
				if (button == 1) {
					query = "";
					search.cancel();
					search.focus("");
				} else if (!search.isFocused()) {
					search.focus(query);
				}
				SoundManager.get().play(SoundAction.CLICK);
				return true;
			}
			if (search.isFocused()) {
				search.submit();
			}
			for (boolean grid : new boolean[] {false, true}) {
				int[] v = viewToggleRect(grid);
				if (button == 0 && Theme.isHovered(mx, my, v[0], v[1], v[2], v[3])) {
					if (gridView != grid) {
						gridView = grid;
						pageChangedAt = System.currentTimeMillis();
						SoundManager.get().play(SoundAction.CLICK);
					}
					return true;
				}
			}
		} else if (search.isFocused()) {
			search.submit();
		}

		// Sidebar.
		if (button == 0) {
			int x = sidebarX() + Theme.px(4);
			int w = sidebarW() - Theme.px(8);
			int h = itemH() - Theme.px(2);
			for (int i = 0; i < CATEGORIES.size(); i++) {
				if (Theme.isHovered(mx, my, x, categoryY(i), w, h)) {
					clearSearch();
					showCategory(CATEGORIES.get(i));
					SoundManager.get().play(SoundAction.CLICK);
					return true;
				}
			}
			General[] generals = General.values();
			for (int i = 0; i < generals.length; i++) {
				if (Theme.isHovered(mx, my, x, generalY(i), w, h)) {
					clearSearch();
					showGeneral(generals[i]);
					SoundManager.get().play(SoundAction.CLICK);
					return true;
				}
			}
		}

		if (inViewport(mx, my) && p.mouseClicked(mx, my, button)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void clearSearch() {
		query = "";
		search.cancel();
		searchPage = null;
	}

	@Override
	public boolean mouseReleased(Click event) {
		if (MusicHud.clickGuiMouseReleased(event.x(), event.y())) {
			return true;
		}
		effectivePage().mouseReleased(event.x(), event.y(), event.button());
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(Click event, double dragX, double dragY) {
		if (MusicHud.clickGuiMouseDragged(event.x(), event.y())) {
			return true;
		}
		effectivePage().mouseDragged(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget = MathHelper.clamp(scrollTarget - (float) scrollY * Theme.px(28), 0, maxScroll());
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		Page p = effectivePage();
		// A focused text field / listening keybind gets the key first (including Escape).
		if (p.isCapturingKeyboard()) {
			p.keyPressed(event);
			return true;
		}
		if (search.isFocused()) {
			if (event.key() == InputUtil.GLFW_KEY_ESCAPE) {
				clearSearch();
				return true;
			}
			search.keyPressed(event);
			return true;
		}
		if (event.key() == InputUtil.GLFW_KEY_ESCAPE && page instanceof ModulePage modulePage) {
			setPage(modulePage.parent());
			return true;
		}
		// The rebindable "open menu" key also closes it.
		if (Hazeline.getOpenGuiKey().matchesKey(event)) {
			close();
			return true;
		}
		return super.keyPressed(event); // Escape closes
	}

	@Override
	public boolean charTyped(CharInput event) {
		Page p = effectivePage();
		if (p.isCapturingKeyboard()) {
			return p.charTyped(event.codepoint());
		}
		if (search.isFocused()) {
			return search.charTyped(event.codepoint());
		}
		return super.charTyped(event);
	}

	@Override
	public void close() {
		if (overlayMode) {
			GuiOverlay.close(); // only the overlay closes, the screen underneath stays open
			return;
		}
		SoundManager.get().play(SoundAction.CLOSE);
		super.close();
	}

	@Override
	public void removed() {
		ConfigManager.get().save();
		super.removed();
	}
}
