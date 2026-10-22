# Visor / VisorEssentials parity audit for GTNH 2.9.0-RC-2

Audit baseline:
- Port: `BadPurse/GTNH-VR-UNOFFICIAL`, branch `Window-Fix` at the start of this audit; target is Minecraft 1.7.10 / Forge with GTNH's modernized LWJGL runtime.
- Upstream Visor: `VisorModStudio/Visor`, branch `dev`.
- Upstream add-on: `VisorModStudio/VisorEssentials`, branch `dev`.
- This is a behavior-parity audit, not a claim that current upstream sources can be copied verbatim into Minecraft 1.7.10. Upstream targets modern Minecraft releases (1.20.1 through 26.3 in its current build matrix), so loader APIs, rendering APIs, mixins, networking and GUI classes differ substantially.

## Java compatibility clarification

The port is **not built for a Java 8 runtime**. The README and `gradle.properties` identify **Java 25 as the verified in-game runtime**; Java 17 and 21 have not been verified for this port. The current `gradle.properties` setting is `enableModernJavaSyntax = modern`, which selects native modern Java bytecode. The values `downgradeTargetVersion = 8` and `jvmDowngraderMultiReleaseVersions = 21,25` are settings for the JVM Downgrader path; that path is not selected while `enableModernJavaSyntax = modern`. They must not be read as the project's runtime requirement. The Mixin `compatibilityLevel: "JAVA_8"` is a Mixin configuration compatibility setting, not the project's Java compiler target or minimum runtime.

The upstream Visor build matrix varies by Minecraft version (Java 17 for early 1.20.x targets, Java 21 for 1.20.5–1.21.x, and Java 25 for 26.x). That upstream matrix does not override this port's target runtime. Also, `compatibilityLevel: "JAVA_8"` in `mixins.gtnhvr.early.json` is a Mixin configuration compatibility setting, not the project's Java compiler target or minimum runtime. Keep Java 25 as the verified runtime; do not change the build to Java 8 based on either the inactive downgrade settings or the Mixin compatibility level.

## Confirmed defects fixed on the parity branch

1. **Early mixin loader did not register every mixin declared in its JSON.** The loader omitted `MixinItemBowAim`, `MixinGuiIngameForge`, `MixinGuiContainerMouseInput`, `MixinAEBaseGuiMouseInput` and `MixinLWJGLXMouse`. These entries are now included in `GTNHVRCoremod.getMixins`.
2. **Angelica font alpha compatibility mixin was never selected by the late loader.** `MixinBatchingFontRendererAlpha` existed in the late mixin config but was not returned by `GTNHVRLateMixins.getMixins`; it is now loaded whenever Angelica is present.
3. **Legacy config aliases could overwrite settings restored by a preset.** `VRPresetManager.Preset.apply` now syncs compatibility aliases from canonical fields before saving.
4. **The body type and the skin-arm style were conflated.** The `defaultVrBody` body-type key and the `vrBodyModel` skin-arm choice are now persisted separately. Old configs that stored a skin selector in `defaultVrBody` are migrated.
5. **“Hands Only” did not hide the arm geometry.** The renderer now skips player-skin arm boxes for that selection while still rendering held items.
6. **Linux OpenXR native payload was missing despite documented Monado support.** The build now shades the LWJGL OpenXR Linux native alongside Windows native libraries.
7. **Custom presets were dropping multiple settings.** Preset serialization/application now covers render effects, third-person and Mixed Reality values, body selection/calibration, immersion/world settings, treadmill/look settings, controller remappings, 14 per-hand item-offset groups, and all configurable overlay fields. Built-in presets also reset overlay defaults. The filename parser handles `.cfg`, `.txt` and `.yml` names consistently.
8. **Several menu settings appeared to save but were not persisted.** Mixed Reality aspect ratio, haptic strength, seated mode, and the anti-Xray vignette now round-trip through the Forge config; anti-Xray vignette is reset by the Defaults preset too.
9. **Russian localization was incomplete.** Added the missing Russian strings for height/body/world settings and overlay editor; static key comparison now reports 610 English keys, 610 Russian keys, zero missing Russian keys and no duplicate Russian keys.

The branch has passed GitHub Actions compile and post-build checks on code commit `db7e986830eb9744c8b50d84187edb8629e558a5` (run [39741338521](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/actions/runs/39741338521)). Compilation and post-build checks succeeded. The workflow is configured as client-only, so server startup and multiplayer runtime checks were skipped; headset behavior remains unverified.

## Behavior mapping and remaining parity work

| Area | Port evidence | Upstream evidence | Status |
| --- | --- | --- | --- |
| OpenXR runtime, eye rendering, camera and controller poses | `OpenXRRuntime`, `VREyeFramebuffer`, `VRControllerHandler`; VR-held vanilla bow default scale reduced and legacy offset scaling clamped | `visor-core` OpenXR provider / renderer / pose packages | Implemented through a different 1.7.10 integration; bow size and remaining runtime parity still need headset testing. |
| Physical movement and interaction tasks | `gameplay/tasks/VRTask*`; `VRControllerHandler` trigger latches | Visor task registry, movement tasks and controller input mapping | Broad equivalents exist. Right/left trigger key release now requires two consecutive low samples to tolerate a single-frame OpenXR zero that would reset mining progress; this is defensive and still needs headset testing. Per-task parity tests remain open. |
| Virtual screen, HUD, keyboard, radial hotbar and wrist overlays | `VRVirtualScreen`, `VRGuiRenderer`, `VRVirtualKeyboard`, `VRHotbarSelector`, `VRWrist*` | Visor overlay manager/registry/templates and keyboard/hotbar implementations | Broad equivalents exist; positioning, focus and control semantics need side-by-side testing. |
| Container and inventory UX | Fixed-canvas virtual screen, wrist inventory, slot rendering, drag preview and tooltips; active `GuiContainer` screens are not reinitialized inside the VR framebuffer capture path | VisorEssentials also has dedicated container overlays, slot interaction, entity preview, double-click handling and modern recipe-book/container hooks | Source-level safeguard against expensive AE2/GTNH machine-screen rebuilds during `drawScreen()`. Recipe-book-specific hooks are not ported because GTNH uses NEI; AE2 crafting, machine screens and frame pacing still require runtime regression tests.
| Add-on API and add-on lifecycle | `com.gtnhvr.api.VRAddonRegistry` now provides client tick, per-eye render and held-item pose callbacks, including add-on unregister | Upstream Visor has a dedicated `visor-api`, add-on manager, task registration and overlay registries | Initial compatible extension foundation is implemented. It is not binary-compatible with upstream and does not yet include full component/task/overlay registration or a server-side API; compile and third-party integration testing remain required.
| Multiplayer VR pose / tracker synchronization | Version-3 opt-in handshake; 20 Hz visual HMD and aim-hand world positions, yaw/pitch and full orientation quaternions; server-side range/rate validation; relay only to clients that acknowledge the new pose capability; interpolation; remote skin-arm rendering; hide the matching vanilla arm and apply remote head yaw/pitch | Visor syncs raw HMD/hand/tracker pose, a remote player pose model and richer avatar state; VisorEssentials syncs bow tension/state | Partial. The full orientation quaternion preserves wrist roll for remote arms. The remote head mesh receives a clamped room-scale positional offset and yaw/pitch, but there is not yet a separate 6-DoF head/neck skeleton and the vanilla head model does not apply the transmitted roll. Held-item poses, grip poses over the network, finger joints and body trackers are not synchronized. Two-client VR, dedicated-server and old-server compatibility tests remain required. Pose sync only activates for a server that sends handshake version 3 and a client/server pose capability version 2. |
| Full-body tracking, raw hand tracking and tracker APIs | UI settings are retained for Visor-style config parity; English tooltips now clarify current limits | Upstream has raw HMD/hand/tracker pose classes and local/remote pose models | Not feature-complete: no XR finger-joint extension or body-tracker providers are wired. The port does not provide full-body skeleton rendering; bHaptics-style suits require a future add-on. |
| Renderer compatibility | Port has late mixins for Angelica and selected Iris/Sodium option/hand paths | Upstream contains explicit integrations for Iris, Sodium, Distant Horizons, Create, Flashback, Immersive Portals, Nvidium, Voxy and other contemporary mods | Intentional target difference, but the supported GTNH renderer/mod matrix should be enumerated and tested. |
| Platform packaging | The port uses LWJGL 3 / lwjgl3ify and shaded AtumVR/OpenXR pieces | Upstream uses a version/loader-specific build matrix | Linux OpenXR payload was added; final native-loading test is still required. |

## Next verification gates

1. Build the new branch with the exact Java/Gradle/JVM arguments used by the GTNH 2.9.0-RC-2 client.
2. Check that all configured early mixins exactly match the early-loader list, and that all late mixins are actually enabled by the intended mod-presence conditions.
3. Run in-game checks for each settings toggle and preset: change the value, save, restart, and verify both behavior and config persistence.
4. Test controller mappings, hand styles, GUI focus/drag/drop/scrolling, container slots, inventory screens, and bow draw/release with the actual GTNH set of container mods.
5. Test remote pose relay with two Java 25 GTNHVR clients on an integrated server and a dedicated GTNH server; also join with an older GTNHVR client and a non-GTNHVR client to verify handshake compatibility and packet gating.
6. Test startup and shutdown on Windows/OpenXR and Linux/Monado, including the client-only code path and a dedicated server.
7. Compare networking and remote-player behavior explicitly before claiming multiplayer parity.

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
- [Port networking and server bow synchronization](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/tree/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/network)
- [Server-side bow aim mixin](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/src/main/java/com/gtnhvr/mixin/early/MixinItemBowAimServer.java)
- [Forge 1.7.10 add-on API contract and example](https://github.com/BadPurse/GTNH-VR-UNOFFICIAL/blob/feature/visor-parity-2.9.0-rc2/docs/addon-api.md)
