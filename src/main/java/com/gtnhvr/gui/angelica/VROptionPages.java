package com.gtnhvr.gui.angelica;

import com.google.common.collect.ImmutableList;
import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.settings.MirrorMode;
import com.gtnhvr.settings.RotationMode;
import com.gtnhvr.settings.VRClientSettings;
import com.gtnhvr.settings.VRPlayMode;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.SubScreenOption;
import me.jellysquid.mods.sodium.client.gui.options.binding.GenericBinding;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public class VROptionPages {

    private static final OptionStorage<VRClientSettings> STORAGE = new OptionStorage<VRClientSettings>() {
        @Override
        public VRClientSettings getData() {
            return null;
        }

        @Override
        public void save() {
            VRClientSettings.save();
        }
    };

    public static OptionPage vr(GuiScreen parent) {
        String pageTitle = I18n.format("visor.options.main.button");
        if (pageTitle == null || pageTitle.equals("visor.options.main.button")) {
            pageTitle = "VR";
        }

        return new OptionPage(
                pageTitle,
                ImmutableList.of(
                        OptionGroup.createBuilder()
                                .add(new SubScreenOption(
                                        pageTitle,
                                        "Open full Visor VR Configuration & Calibration interface",
                                        VRSettingsScreen::new
                                ))
                                .build(),
                        OptionGroup.createBuilder()
                                .add(OptionImpl.createBuilder(VRPlayMode.class, STORAGE)
                                        .setName("VR Mode")
                                        .setTooltip("Enable or disable Virtual Reality OpenXR mode")
                                        .setControl(opt -> new CyclingControl<>(opt, VRPlayMode.class, new String[]{
                                                "Disabled", "Standing", "Seated", "Room-Scale"
                                        }))
                                        .setBinding(new GenericBinding<>(
                                                (s, val) -> VRClientSettings.vrPlayMode = val,
                                                s -> VRClientSettings.vrPlayMode
                                        ))
                                        .setImpact(OptionImpact.HIGH)
                                        .build()
                                )
                                .add(OptionImpl.createBuilder(MirrorMode.class, STORAGE)
                                        .setName("Desktop Mirror")
                                        .setTooltip("Mirror VR rendering to the desktop monitor")
                                        .setControl(opt -> new CyclingControl<>(opt, MirrorMode.class, new String[]{
                                                "Off", "GUI", "Cropped", "Single Eye", "Dual Eye", "First Person", "Third Person", "Mixed Reality"
                                        }))
                                        .setBinding(new GenericBinding<>(
                                                (s, val) -> VRClientSettings.mirrorMode = val,
                                                s -> VRClientSettings.mirrorMode
                                        ))
                                        .setImpact(OptionImpact.LOW)
                                        .build()
                                )
                                .add(OptionImpl.createBuilder(RotationMode.class, STORAGE)
                                        .setName("Rotation Mode")
                                        .setTooltip("Which VR device controls your player's head rotation")
                                        .setControl(opt -> new CyclingControl<>(opt, RotationMode.class, new String[]{
                                                "Offhand", "Main Hand", "HMD"
                                        }))
                                        .setBinding(new GenericBinding<>(
                                                (s, val) -> VRClientSettings.rotationMode = val,
                                                s -> VRClientSettings.rotationMode
                                        ))
                                        .setImpact(OptionImpact.LOW)
                                        .build()
                                )
                                .build()
                )
        );
    }
}
