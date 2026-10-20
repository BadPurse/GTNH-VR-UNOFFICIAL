package com.gtnhvr;

import cpw.mods.fml.client.registry.ClientRegistry;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@Lwjgl3Aware
public final class KeyBindings {
    public static final KeyBinding keyVRSettings = new KeyBinding(
            "key.gtnhvr.settings",
            Keyboard.KEY_NONE,
            "key.categories.gtnhvr"
    );

    private KeyBindings() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(keyVRSettings);
    }
}
