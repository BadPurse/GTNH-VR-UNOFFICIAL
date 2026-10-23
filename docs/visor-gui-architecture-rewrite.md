# VR GUI architecture rewrite: Visor-style render lifecycle

## Why the previous path was unstable

The old path treated a virtual screen as a desktop screen temporarily resized during rendering. Several independent hooks could own the same frame:

- `VRGuiRenderer` captured a screen to an FBO, changed GUI/window bookkeeping, and could call `setWorldAndResolution` from the capture start.
- `VRHudRenderer` allocated a second FBO and separately spoofed `Minecraft.displayWidth/displayHeight`.
- `GuiEventHandler` attempted to fit large containers by changing `gameSettings.guiScale`, reading private `GuiContainer` fields, and adjusting layout from init events.
- `MixinMinecraft` had another virtual-screen-size scope inside `displayGuiScreen`; a later injection variant failed to match and crashed mixin application.
- Cursor coordinates were partly normalized through physical window pixels and then manually rescaled, even though Minecraft's regular mouse path already uses `ScaledResolution`.
- `MixinGui` altered alpha writes for ordinary rectangles and gradients. Those drawing calls are also used by modded machine screens, tabs, progress bars, and tooltips, so suppressing their alpha channel could make actual GUI content disappear or become translucent.

Those systems touched the same frame state but did not share one owner. This is fundamentally different from Visor, where the GUI target manager owns render targets and the overlay manager brackets a target render before restoring the prior GL state.

## New rendering rules

### 1. One target manager, two named passes

`VRGuiRenderPipeline` now owns the GL resources for both the active game screen and the in-game HUD. Each target has its own color texture, framebuffer, packed depth/stencil renderbuffer, and `VRGuiFramebuffer` adapter. `VRGuiRenderer` and `VRHudRenderer` are target-specific facades; they no longer allocate or delete GL objects themselves.

A pass is acquired once, binds its target and framebuffer adapter, installs a target-sized orthographic projection, and clears the target. A second pass cannot silently steal an active one. Ending the pass restores the previous Minecraft framebuffer reference, projection/model-view stacks, GL attributes, framebuffer binding, and prior logical-resolution marker. The screen and HUD targets are separate but use the same resource and state lifecycle.

### 2. Virtual sizing is resolved at the source

`MixinScaledResolution` overrides the width and height arguments to Minecraft's `ScaledResolution(Minecraft, int, int)` constructor while OpenXR is active. Vanilla, Forge and mod GUI code therefore receive a consistent logical canvas, while `Minecraft.displayWidth` and `displayHeight` always retain the actual lwjgl3ify window size.

The UI does not need to change the global window dimensions merely to build an inventory or lay out a settings screen. The ordinary mouse pipeline uses the same logical `ScaledResolution`; the controller continues to publish physical window pixels, and Minecraft performs the physical-to-logical conversion once. The old manual coordinate rescaling is no longer called.

`MixinGuiScreen` records when a screen is initialized under virtual sizing. If VR starts while a screen that was created on the desktop is already open, the renderer may reinitialize that specific screen once on transition. It does not call `setWorldAndResolution` every frame. When the VR session ends, the screen is restored to physical desktop dimensions once.

### 3. Screen and HUD render through one scoped pipeline

Forge's `DrawScreenEvent.Pre` starts the game-screen pass. The renderer tail closes it after the rest of the frame, so Forge listeners and mod-provided tooltips that draw later in the same GUI frame remain in the target. The Forge HUD hook starts and ends its own HUD pass around `GuiIngameForge.renderGameOverlay`.

While a pass is active, `Minecraft.getFramebuffer()` returns the active GUI/HUD target before it considers the OpenXR eye framebuffer. This prevents a mod that obtains the current framebuffer through Minecraft from accidentally drawing its GUI elements into an eye image instead of the 2D render target.

The HUD and screen continue to use distinct textures for their existing world-space presentation; only their target allocation and GL-state ownership have been consolidated.

### 4. Mod widgets keep normal RGBA semantics

`MixinGui` now suppresses only a full-screen in-world darkening backdrop. It no longer modifies the color mask for ordinary `drawRect` or `drawGradientRect` calls. The alpha-safe blend mixins remain responsible for source-over alpha accumulation in the off-screen texture; they do not replace the widget's own RGBA content.

### 5. Remove layout mutations instead of working around them

The container auto-fit code that changed `gameSettings.guiScale`, reflected into `GuiContainer.xSize/ySize/guiLeft/guiTop`, and clamped layout during init has been removed. A GUI's sizing comes from its actual logical canvas, just as it should in an overlay renderer. This avoids changing a player's setting or repeatedly reinitializing complex AE2/NEI/ModularUI screens while opening them.

The duplicate keyboard visibility hook in `MixinMinecraft.displayGuiScreen` is also removed. GUI-open and text-field lifecycle handling remains in the Forge GUI events and text-field hooks.

## Files responsible for the new path

- `gui/VRGuiRenderPipeline.java`: shared target registry, FBO lifetime, pass ownership, clear/projection setup, exact state restoration.
- `gui/VRGuiRenderer.java`: active game-screen target facade and transition-time logical-screen bookkeeping.
- `gui/VRHudRenderer.java`: HUD target facade and 3D HUD presentation.
- `mixin/early/MixinScaledResolution.java`: central logical size conversion.
- `mixin/early/MixinMinecraft.java`: active 2D target takes precedence over the current OpenXR eye framebuffer.
- `mixin/early/MixinGuiScreen.java`: remembers normal screen initialization and preserves the intentional world-background suppression.
- `gui/GuiEventHandler.java`: starts screen capture from the Forge event and restores dimensions on VR session end; no container auto-fit or repeated size mutation.
- `mixin/early/MixinGui.java`: narrow backdrop suppression with ordinary mod-widget alpha left intact.

## Validation checklist

Build/CI confirms source compatibility, but only a running GTNH 2.9.0-RC-2 instance can confirm full mod compatibility. Test the following against the same 1920×1080 VR canvas while the physical desktop window remains at 2388×1668:

1. Vanilla inventory and pause/settings menus: buttons, hover, tooltips, scrolling lists, and keyboard focus.
2. GregTech machine GUI and ModularUI widgets: slot clicks, progress/energy bars, tab gradients, dragging, and held-button release.
3. AE2 terminal/crafting screen and NEI overlays/tooltips: no one-frame loading screen, no capture escape to the desktop, and no repeated GUI initialization/FPS collapse.
4. GTNH custom main menu and Angelica settings: no clipping or coordinate drift.
5. Open VR with a desktop screen already open, close VR while a GUI is open, and change GUI scale; there must be no global display-dimension mutation left behind.
6. HUD and screen passes in the same frame: correct alpha, correct target selection, and restored GL state after both passes.

A successful compile is not a substitute for the runtime checks above; the remaining interoperability boundary is third-party GUI code that bypasses Minecraft/Forge's normal `ScaledResolution` and framebuffer access paths.
