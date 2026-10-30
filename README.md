# Hazeline

A client-side **Fabric customization mod** for **Minecraft Java Edition 1.21.11**. Soft, atmospheric visuals: custom sky, weather, menus, outlines and a smooth click GUI. Everything is visual / customization only - no cheats, nothing sent to servers.

**Current version: 1.44.0** · download: [`release/hazeline-1.44.0.jar`](release/hazeline-1.44.0.jar)

## Features

- **Click GUI** (open with **Right Shift**): one window with a sidebar, search, list/grid view, smooth animations and glowing particles. Opens in game and on top of any menu (title screen, server list, inventory, chests...) without closing it.
- **Weather** (new in 1.44.0): force Clear / Rain / Thunderstorm / Snow or follow the server, with smooth fades and its own rain/wind ambience. Plus **Glossy Rain** (wet, shiny ground and ripples), **Mist** (haze, puffs, sun rays) and **Smooth Thunder** (branching lightning bolts, flashes, delayed thunder). Client-side only, Overworld only.
- **Custom Sky** (Overworld): gradient sky with day/night blending and falling stars, 5 presets plus your own.
- **Custom Menus**: loading screen, main menu and other menus in the Custom Sky look, with a glowing progress bar and your own wordmark text.
- **Render**: Hand View, Swing Speed, **Custom Block Outlines** (outline / fill, custom colours, Rainbow and colour sets, animations, break animation), **Hit Boxes** (entity types you pick, look direction line, hurt flash, smooth fade; never through walls), **Name Tag** (style your own name with colours, gradients and animations - only on your screen, your account is unchanged).
- **Sounds and Clicks**: keyboard click sounds for the click GUI, in game and in menus, 8 real switch recordings.
- **Music HUD** (Windows): a "now playing" card for whatever plays on your PC (Spotify, YouTube Music in a browser...) with cover, progress, controls and volume. No account linking.
- **Appearance**: 5 fonts, 16 theme presets, colour picker and theme codes.

## Install

1. Use a **Fabric Loader 0.19.5 or newer** profile for **Minecraft 1.21.11**.
2. Put `hazeline-1.44.0.jar` and **Fabric API** (0.141.6+1.21.11 or newer) in the profile's `mods` folder. Remove any older Hazeline / `pastaclient` jar.
3. Close the game before replacing the jar.

## Build from source

Requires **JDK 21**.

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

The jar ends up in `build/libs/hazeline-<version>.jar`.

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Mappings | Yarn `1.21.11+build.6` |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.141.6+1.21.11 |
| Fabric Loom | 1.18-SNAPSHOT (`net.fabricmc.fabric-loom-remap`) |
| Java | 21 |

## Licence

The source is public to read, but Hazeline is **not open source**: all rights reserved, see [`LICENSE`](LICENSE). You may download and play the released mod; copying, modifying, re-uploading or reusing the code needs permission. Bundled fonts keep their SIL Open Font License 1.1 and the sounds their MIT licence (kbsim / Mechvibes); their licence files are in `src/main/resources` and inside the jar.
