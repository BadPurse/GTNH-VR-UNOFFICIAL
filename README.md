# GTNH VR (Unofficial)

[![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-blue.svg)](https://www.minecraft.net/)
[![Java 17-25](https://img.shields.io/badge/Java-17--25-orange.svg)](https://adoptium.net/)
[![LWJGL 3](https://img.shields.io/badge/LWJGL-3.3.3-red.svg)](https://www.lwjgl.org/)
[![OpenXR](https://img.shields.io/badge/OpenXR-1.0-brightgreen.svg)](https://www.khronos.org/openxr/)
[![GT: New Horizons](https://img.shields.io/badge/GT%20New%20Horizons-2.9.0--RC--2-purple.svg)](https://www.gtnewhorizons.com/)

**GTNH VR** brings a complete, high-fidelity Virtual Reality experience to **GregTech: New Horizons** (Minecraft 1.7.10) using modern **OpenXR** standards and **LWJGL 3**.

This mod is an unofficial port and adaptation of the acclaimed [Visor](https://github.com/VisorModStudio/Visor) VR mod and its VisorEssentials suite, specifically redesigned and optimized for the modernized 1.7.10 runtime environment (`lwjgl3ify`, `Angelica`, `UniMixins`, `Backhand`).

---

## Key Features

### Full 6-DoF Room-Scale & Controller Tracking
- Native **OpenXR** integration with first-class support for Meta Quest (Touch / Touch Plus / Pro), Valve Index (Knuckles), HTC Vive, and Windows Mixed Reality.
- Smooth locomotion directed by controller or gaze, with snap turning or smooth turning.
- Parabolic teleportation mode for comfortable navigation.

### True 1:1 Visor Immersion
- **Dual 3D Hands:** Realistic arm and hand models reflecting your player skin for both main hand and offhand (`Backhand` support).
- **Interactive In-World Containers:** Opening chests, furnaces, machines, or entity inventories anchors them directly in 3D world space above the block, automatically closing if you walk away.
- **Floating High-Resolution Virtual Screen:** Complete GUI and HUD rendering with Visor 1:1 Drag Bar & Resize Handle, running at an autonomous crisp resolution decoupled from flat desktop monitor dimensions.
- **Inverted Aiming Crosshair:** Accurate 3D '+' crosshair dynamically projecting onto blocks and entities with instant inverted contrast.
- **Anti-XRay Collision Vignette:** Physical room-scale HMD head clipping detection prevents looking through walls and solid blocks by applying an in-block vignette.

### Physical Gameplay Tasks
- **Two-Handed Physical Boat Rowing:** Row naturally through water using both controllers with real-time paddle velocity physics and tactile haptic feedback.
- **Physical Bow Aiming:** Draw arrows with realistic 30-step ballistic parabolic trajectory ribbons and target impact indicators.
- **Immersive Block Mining:** Break blocks using physical swings with tools or fists. Block damage cracks persist across swings for heavier and slower tools.
- **Physical Room Interactions:** Real physical crouching, crawling under 1-block spaces, ladder climbing with controller grips, swimming strokes, and physical eating/drinking gestures.

### In-Game VR Controls & Tools
- **Radial Hotbar Selector:** Fast, gesture-based 3x3 circular hotbar selector opened with controller grips.
- **3D Laser Pointer & Touch Keyboard:** Full VR laser interaction with multi-language virtual keyboard (QWERTY, RU, UA) featuring automatic detection for Applied Energistics 2, Ender IO, NEI, books, and signs.
- **Wrist Status Overlays:** Check inventory, health, minimap, potion effects, and environmental data simply by turning your wrists.
- **Comprehensive In-Game VR Settings:** Full configuration menu for haptic feedback, eye offsets, render distance, UI scales, and control bindings directly in VR.

---

## Requirements

1. **Client Platform:**
   - Minecraft 1.7.10 with Forge.
   - **GregTech: New Horizons 2.9.0-RC-2** (or custom 1.7.10 modpack using `lwjgl3ify`).
2. **Java Runtime:**
   - **25** 64-bit (Eclipse Adoptium Temurin recommended).
3. **VR Hardware & Runtime:**
   - Any OpenXR-compliant VR runtime:
     - **SteamVR**
     - **Meta Quest Link / AirLink**
     - **Virtual Desktop** (with VDXR or SteamVR)
     - **Monado** (Linux)

---

## Installation

1. Make sure your GT New Horizons instance runs on **Java 25**.
2. Download the latest `GTNHVR-x.x.x.jar` from the [Releases](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/releases) page.
3. Place the `.jar` into your instance's `.minecraft/mods/` directory.
4. Launch your VR runtime.
5. Start Minecraft. Once the game reaches the main menu or a world is loaded, VR mode will activate automatically.

---

## Building from Source

This project uses Gradle with the GT New Horizons mod template:

```bash
git clone https://github.com/BadPurse/GTNH-VR-UNOFFICIAL.git
cd GTNH-VR-UNOFFICIAL
./gradlew setupDecompWorkspace
./gradlew build
```

The compiled mod jar will be in `build/libs/`.

---

## Credits & Attribution

This project wouldn't exist without the foundational work of the Minecraft VR and GTNH communities:

- **[Phoenix-Ra](https://github.com/Phoenix-Ra)** & **[VisorModStudio](https://github.com/VisorModStudio):**
  Huge gratitude and special thanks to Phoenix-Ra for designing and developing **[Visor](https://github.com/VisorModStudio/Visor)** and **VisorEssentials**. The architecture, visual design, task systems, radial hotbars, wrist overlays, and VR interaction principles in this mod are ported directly from and inspired by Visor's exceptional work.
- **[GT: New Horizons Team](https://github.com/GTNewHorizons):**
  For the modernization of Minecraft 1.7.10 to modern Java and OpenGL, including `lwjgl3ify` by eigenraven, `Angelica`, and `UniMixins`.
- **[Khronos Group](https://www.khronos.org/openxr/):**
  For the open, royalty-free OpenXR standard.

---

## License

This project is licensed under the terms of the GNU Lesser General Public License v3.0 (LGPL-3.0) or Mozilla Public License 2.0 (MPL-2.0) consistent with upstream Visor components and the GTNH mod environment.
