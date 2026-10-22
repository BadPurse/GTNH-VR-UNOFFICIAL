# Visor / VisorEssentials parity audit for GTNH 2.9.0-RC-2

Audit baseline:
- Port: `BadPurse/GTNH-VR-UNOFFICIAL`, branch `Window-Fix` at the start of this audit; target is Minecraft 1.7.10 / Forge with GTNH's modernized LWJGL runtime.
- Upstream Visor: `VisorModStudio/Visor`, branch `dev`.
- Upstream add-on: `VisorModStudio/VisorEssentials`, branch `dev`.
- This is a behavior-parity audit, not a claim that current upstream sources can be copied verbatim into Minecraft 1.7.10. Upstream targets modern Minecraft releases (1.20.1 through 26.3 in its current build matrix), so loader APIs, rendering APIs, mixins, networking and GUI classes differ substantially.

## Confirmed defects fixed on the parity branch

1. **Early mixin loader did not register every mixin declared in its JSON.** The loader omitted `MixinItemBowAim`, `MixinGuiIngameForge`, `MixinGuiContainerMouseInput`, `MixinAEBaseGuiMouseInput` and `MixinLWJGLXMouse`. These entries are now included in `GTNHVRCoremod.getMixins`.
2. **Angelica font alpha compatibility mixin was never selected by the late loader.** `MixinBatchingFontRendererAlpha` existed in the late mixin config but was not returned by `GTNHVRLateMixins.getMixins`; it is now loaded whenever Angelica is present.
3. **Legacy config aliases could overwrite settings restored by a preset.** `VRPresetManager.Preset.apply` now syncs compatibility aliases from canonical fields before saving.
4. **The body type and the skin-arm style were conflated.** The `defaultVrBody` body-type key and the `vrBodyModel` skin-arm choice are now persisted separately. Old configs that stored a skin selector in `defaultVrBody` are migrated.
5. **“Hands Only” did not hide the arm geometry.** The renderer now skips player-skin arm boxes for that selection while still rendering held items.
6. **Linux OpenXR native payload was missing despite documented Monado support.** The build now shades the LWJGL OpenXR Linux native alongside Windows native libraries.
7. **Custom presets were dropping multiple settings.** Preset serialization/application now covers the body selector and calibration, more immersion/world options, treadmill/look settings, and additional GUI options; the filename parser also handles `.txt` presets consistently.

The relevant fixes are in the branch commits and source files linked below. These are source-level corrections; they have not yet been validated by a Gradle build or a runtime session.

## Behavior mapping and remaining parity work

| Area | Port evidence | Upstream evidence | Status |
| --- | --- | --- | --- |
| OpenXR runtime, eye rendering, camera and controller poses | `OpenXRRuntime`, `VREyeFramebuffer`, `VRControllerHandler` | `visor-core` OpenXR provider / renderer / pose packages | Implemented through a different 1.7.10 integration; runtime parity still needs headset testing. |
| Physical movement and interaction tasks | `gameplay/tasks/VRTask*` | Visor's registered task system and movement tasks | Broad equivalents exist; per-task parity tests are still needed. |
| Virtual screen, HUD, keyboard, radial hotbar and wrist overlays | `VRVirtualScreen`, `VRGuiRenderer`, `VRVirtualKeyboard`, `VRHotbarSelector`, `VRWrist*` | Visor overlay manager/registry/templates and keyboard/hotbar implementations | Broad equivalents exist; positioning, focus and control semantics need side-by-side testing. |
| Container and inventory UX | Port has virtual screen and wrist inventory implementations | VisorEssentials additionally has dedicated container/inventory overlays, dragged-item overlay, slot interaction logic, entity preview, double-click handling and recipe-book/container-screen mixins | Partial / unverified. Different 1.7.10 container classes require explicit per-screen adaptation; matching modern mixin names is not enough. |
| Add-on API and add-on lifecycle | No separate public API/add-on module in the port source tree | Upstream Visor has a dedicated `visor-api`, addon manager, task registration and overlay registries | Architectural gap. If add-on extensibility is part of the requirement, design a 1.7.10-compatible API rather than copying modern API classes. |
| Multiplayer VR pose / tracker synchronization | No corresponding packet/pose API package is present in the port tree | Visor exposes client/server networking and remote player/hand/tracker pose types; VisorEssentials has networked bow settings/tension payloads | Not at demonstrated parity. Requires a separate protocol and integrated/dedicated-server audit. |
| Full-body tracking, raw hand tracking and tracker APIs | Settings/UI fields exist in the port, but there is no one-to-one module boundary for upstream's raw-hand/raw-tracker pose providers | Upstream has raw HMD/hand/tracker pose classes and local/remote pose models | Not established as feature-complete by source structure alone; verify each setting against runtime behavior before advertising support. |
| Renderer compatibility | Port has late mixins for Angelica and selected Iris/Sodium option/hand paths | Upstream contains explicit integrations for Iris, Sodium, Distant Horizons, Create, Flashback, Immersive Portals, Nvidium, Voxy and other contemporary mods | Intentional target difference, but the supported GTNH renderer/mod matrix should be enumerated and tested. |
| Platform packaging | The port uses LWJGL 3 / lwjgl3ify and shaded AtumVR/OpenXR pieces | Upstream uses a version/loader-specific build matrix | Linux OpenXR payload was added; final native-loading test is still required. |

## Next verification gates

1. Build the new branch with the exact Java/Gradle/JVM arguments used by the GTNH 2.9.0-RC-2 client.
2. Check that all configured early mixins exactly match the early-loader list, and that all late mixins are actually enabled by the intended mod-presence conditions.
3. Run in-game checks for each settings toggle and preset: change the value, save, restart, and verify both behavior and config persistence.
4. Test controller mappings, hand styles, GUI focus/drag/drop/scrolling, container slots, inventory screens, and bow draw/release with the actual GTNH set of container mods.
5. Test startup and shutdown on Windows/OpenXR and Linux/Monado, including the client-only code path and a dedicated server.
6. Compare networking and remote-player behavior explicitly before claiming multiplayer parity.

## Source links

- [Upstream Visor build matrix](https://github.com/VisorModStudio/Visor/blob/dev/stonecutter.properties.toml)
- [Upstream Visor README and license note](https://github.com/VisorModStudio/Visor/blob/dev/README.md)
- [Upstream Visor API tree](https://github.com/VisorModStudio/Visor/tree/dev/visor-api/src/main/java)
- [Upstream VisorEssentials tree](https://github.com/VisorModStudio/VisorEssentials/tree/dev/essentials-core/src/main/java)
- [Port README](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/README.md)
- [Port early mixin loader](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/core/GTNHVRCoremod.java)
- [Port late mixin loader](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/late/GTNHVRLateMixins.java)
- [Port settings](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/settings/VRClientSettings.java)
- [Port presets](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/settings/VRPresetManager.java)
- [Port hand renderer](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/gui/VRHandRenderer.java)
- [Port OpenXR dependencies](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/dependencies.gradle)
