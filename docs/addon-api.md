# GTNHVR add-on API (Forge 1.7.10)

The port exposes a small client-only extension API at `com.gtnhvr.api.VRAddonRegistry`. It is a GTNH/Forge-compatible adaptation, not a binary-compatible copy of Visor's modern `visor-api`.

## Register callbacks

Register from your add-on's **client proxy** only, after GTNHVR is loaded. Do not reference this client-only API from common code that can be loaded on a dedicated server.

```java
import com.gtnhvr.api.VRAddonRegistry;
import net.minecraft.client.Minecraft;

public final class ExampleVrAddon {
    public static void initializeClient() {
        VRAddonRegistry.registerClientTickListener("example-addon", (mc, controllers) -> {
            // Client logic while OpenXR is running.
            // Use controllers.isRightHandTracked(), getRightHandPos(), etc.
        });

        VRAddonRegistry.registerLocalPoseListener("example-addon", (mc, pose) -> {
            if (pose.head.tracked && pose.rightAim.tracked) {
                // Read copied room-space poses for the HMD and controllers.
                // Grip orientations are independently flagged because some runtimes omit them.
            }
        });

        VRAddonRegistry.registerWorldRenderListener("example-addon", (mc, eye, partialTicks, guiOpen) -> {
            // Optional render work for this eye pass.
            // This callback runs once per eye, so do not perform gameplay mutations here.
        });

        VRAddonRegistry.registerHandItemPoseProvider("example-addon",
                (mc, player, stack, rightHand, controllers, pose) -> {
                    if (stack.getItem() != net.minecraft.init.Items.stick) {
                        return false; // Let another provider or the built-in pose handle it.
                    }
                    pose.set(0.0F, 0.0F, -0.08F, 0.0F, 0.0F, 0.0F, 0.15F);
                    return true;
                });
    }

    public static void shutdownClient() {
        VRAddonRegistry.unregisterAddon("example-addon");
    }
}
```

## Contracts and limitations

- Each add-on ID may register multiple callbacks of each type. Registration of the same listener instance is deduplicated; `unregisterAddon(id)` removes that add-on's callbacks.
- Tick callbacks run on the client tick only while an OpenXR session is active.
- Render callbacks run inside each eye pass and have OpenGL state guarded/restored by GTNHVR. Keep them lightweight and draw only; don't change inventories, send packets or mutate world state there.
- Local pose listeners receive one immutable `PoseSnapshot` per active client tick, containing HMD, aim-controller and grip-controller positions/quaternions in OpenXR tracking-space metres. Each device has separate position/orientation tracking flags; providers may lack valid grip orientation. Values are copied, not references to the controller handler's mutable vectors.
- Hand item pose providers are tried in registration order; the first provider that returns `true` with finite transform values wins. If no provider supplies a pose, the built-in category transform is used.
- Translation is in metres, rotations are in degrees, and scale is per axis. A custom item pose replaces the category transform but still uses the same controller pose and item renderer.
- This API is client-only and does not itself provide multiplayer pose synchronization, full-body tracking, optical hand tracking, or a server API. Those remain separate parity items and must not be inferred from the existence of this registry.
