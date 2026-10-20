package com.gtnhvr.gui.settings.categories.controls;

import com.gtnhvr.gui.VRHandRenderer;
import com.gtnhvr.gui.VRHotbarSelector;
import com.gtnhvr.gui.VRSettingsScreen;
import com.gtnhvr.gui.settings.OptionWidget;
import com.gtnhvr.gui.settings.OptionWidgetPosition;
import com.gtnhvr.gui.settings.VROptionsSet;
import com.gtnhvr.settings.ItemHandOffset;
import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class VRSettingsItemOffsets extends VROptionsSet {

    public enum OffsetCategory {
        TOOLS("visor.options.item_offsets.cat.tools"),
        TINKER("visor.options.item_offsets.cat.tinker"),
        SWORDS("visor.options.item_offsets.cat.swords"),
        BLOCKS("visor.options.item_offsets.cat.blocks"),
        TORCHES("visor.options.item_offsets.cat.torches"),
        BOWS("visor.options.item_offsets.cat.bows"),
        ITEMS("visor.options.item_offsets.cat.items");

        public final String langKey;

        OffsetCategory(String langKey) {
            this.langKey = langKey;
        }

        public String getDisplayName() {
            return I18n.format(langKey);
        }
    }

    public enum HandTarget {
        RIGHT("visor.options.item_offsets.hand.right"),
        LEFT("visor.options.item_offsets.hand.left");

        public final String langKey;

        HandTarget(String langKey) {
            this.langKey = langKey;
        }

        public String getDisplayName() {
            return I18n.format(langKey);
        }
    }

    public static class PreviewOption {
        public final String name;
        public final ItemStack stack;

        public PreviewOption(String name, ItemStack stack) {
            this.name = name;
            this.stack = stack;
        }
    }

    private static OffsetCategory currentCategory = OffsetCategory.TOOLS;
    private static HandTarget currentHand = HandTarget.RIGHT;
    private static int currentPreviewIndex = 0;

    public VRSettingsItemOffsets(VRSettingsScreen screen, VROptionsSet previousSet) {
        super(screen, previousSet);
    }

    public static OffsetCategory getCurrentCategory() {
        return currentCategory;
    }

    public static HandTarget getCurrentHand() {
        return currentHand;
    }

    public static boolean matchesCategory(ItemStack stack, OffsetCategory cat) {
        if (stack == null || stack.getItem() == null) return false;
        VRHandRenderer.VRItemCategory itemCat = VRHandRenderer.getItemCategory(stack);
        return switch (cat) {
            case TOOLS -> itemCat == VRHandRenderer.VRItemCategory.TOOL;
            case TINKER -> itemCat == VRHandRenderer.VRItemCategory.TINKER;
            case SWORDS -> itemCat == VRHandRenderer.VRItemCategory.SWORD;
            case BLOCKS -> itemCat == VRHandRenderer.VRItemCategory.BLOCK_3D;
            case TORCHES -> itemCat == VRHandRenderer.VRItemCategory.TORCH;
            case BOWS -> itemCat == VRHandRenderer.VRItemCategory.BOW;
            case ITEMS -> itemCat == VRHandRenderer.VRItemCategory.CONSUMABLE || itemCat == VRHandRenderer.VRItemCategory.GENERIC;
        };
    }

    private static void addTinkerPreview(List<PreviewOption> list, String registryName, String fallbackName) {
        try {
            net.minecraft.item.Item item = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(registryName);
            if (item != null) {
                ArrayList<ItemStack> subList = new ArrayList<>();
                try {
                    item.getSubItems(item, item.getCreativeTab(), subList);
                } catch (Throwable ignored) {}
                if (!subList.isEmpty() && subList.get(0) != null) {
                    list.add(new PreviewOption(fallbackName, subList.get(0)));
                } else {
                    list.add(new PreviewOption(fallbackName, new ItemStack(item)));
                }
            }
        } catch (Throwable ignored) {}
    }

    public static List<PreviewOption> getPreviewList(OffsetCategory cat) {
        List<PreviewOption> list = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();

        switch (cat) {
            case TOOLS:
                list.add(new PreviewOption("Diamond Pickaxe", new ItemStack(Items.diamond_pickaxe)));
                list.add(new PreviewOption("Diamond Axe", new ItemStack(Items.diamond_axe)));
                list.add(new PreviewOption("Diamond Shovel", new ItemStack(Items.diamond_shovel)));
                list.add(new PreviewOption("Diamond Hoe", new ItemStack(Items.diamond_hoe)));
                list.add(new PreviewOption("Iron Pickaxe", new ItemStack(Items.iron_pickaxe)));
                break;
            case TINKER:
                addTinkerPreview(list, "TConstruct:pickaxe", "Tinkers' Pickaxe");
                addTinkerPreview(list, "TConstruct:broadsword", "Tinkers' Broadsword");
                addTinkerPreview(list, "TConstruct:hatchet", "Tinkers' Hatchet");
                addTinkerPreview(list, "TConstruct:cleaver", "Tinkers' Cleaver");
                addTinkerPreview(list, "TConstruct:hammer", "Tinkers' Hammer");
                addTinkerPreview(list, "TConstruct:lumberaxe", "Tinkers' Lumber Axe");
                addTinkerPreview(list, "TConstruct:mattock", "Tinkers' Mattock");
                addTinkerPreview(list, "TConstruct:frypan", "Tinkers' Frying Pan");
                addTinkerPreview(list, "TConstruct:rapier", "Tinkers' Rapier");
                addTinkerPreview(list, "TConstruct:dagger", "Tinkers' Dagger");
                break;
            case SWORDS:
                list.add(new PreviewOption("Diamond Sword", new ItemStack(Items.diamond_sword)));
                list.add(new PreviewOption("Iron Sword", new ItemStack(Items.iron_sword)));
                list.add(new PreviewOption("Golden Sword", new ItemStack(Items.golden_sword)));
                break;
            case BLOCKS:
                list.add(new PreviewOption("Chest", new ItemStack(Blocks.chest)));
                list.add(new PreviewOption("Crafting Table", new ItemStack(Blocks.crafting_table)));
                list.add(new PreviewOption("Furnace", new ItemStack(Blocks.furnace)));
                list.add(new PreviewOption("Stone", new ItemStack(Blocks.stone)));
                list.add(new PreviewOption("Oak Log", new ItemStack(Blocks.log)));
                break;
            case TORCHES:
                list.add(new PreviewOption("Torch", new ItemStack(Blocks.torch)));
                list.add(new PreviewOption("Redstone Torch", new ItemStack(Blocks.redstone_torch)));
                break;
            case BOWS:
                list.add(new PreviewOption("Bow", new ItemStack(Items.bow)));
                break;
            case ITEMS:
                list.add(new PreviewOption("Bread", new ItemStack(Items.bread)));
                list.add(new PreviewOption("Apple", new ItemStack(Items.apple)));
                list.add(new PreviewOption("Diamond", new ItemStack(Items.diamond)));
                list.add(new PreviewOption("Stick", new ItemStack(Items.stick)));
                list.add(new PreviewOption("Glass Bottle", new ItemStack(Items.glass_bottle)));
                list.add(new PreviewOption("Potion", new ItemStack(Items.potionitem)));
                break;
        }

        // Only add held items from player inventory if they match the active category!
        if (mc != null && mc.thePlayer != null) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (matchesCategory(held, cat)) {
                String label = I18n.format("visor.options.item_offsets.preview_item.held") + " (" + held.getDisplayName() + ")";
                list.add(0, new PreviewOption(label, held));
            }

            ItemStack offhand = VRHotbarSelector.getInstance().getOffhandItemStack(mc);
            if (matchesCategory(offhand, cat) && (held == null || !ItemStack.areItemStacksEqual(held, offhand))) {
                String label = I18n.format("visor.options.item_offsets.preview_item.offhand") + " (" + offhand.getDisplayName() + ")";
                list.add(new PreviewOption(label, offhand));
            }
        }

        return list;
    }

    public static ItemStack getActivePreviewItem() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return null;
        if (!(mc.currentScreen instanceof VRSettingsScreen)) return null;
        VRSettingsScreen screen = (VRSettingsScreen) mc.currentScreen;
        if (!(screen.getCurrentOptionsSet() instanceof VRSettingsItemOffsets)) return null;

        List<PreviewOption> list = getPreviewList(currentCategory);
        if (list.isEmpty()) return null;
        int idx = Math.abs(currentPreviewIndex) % list.size();
        return list.get(idx).stack;
    }

    public static String getActivePreviewItemName() {
        List<PreviewOption> list = getPreviewList(currentCategory);
        if (list.isEmpty()) return "";
        int idx = Math.abs(currentPreviewIndex) % list.size();
        return list.get(idx).name;
    }

    public static void cyclePreviewItem() {
        List<PreviewOption> list = getPreviewList(currentCategory);
        if (!list.isEmpty()) {
            currentPreviewIndex = (currentPreviewIndex + 1) % list.size();
        }
    }

    private ItemHandOffset getActiveOffset() {
        return VRClientSettings.getHandOffset(currentCategory, currentHand == HandTarget.RIGHT);
    }

    @Override
    protected void buildWidgets() {
        widgets.clear();

        // Row 0 LEFT: Category switcher
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 0,
                () -> "§e" + I18n.format("visor.options.item_offsets.category", currentCategory.getDisplayName()),
                () -> {
                    OffsetCategory[] cats = OffsetCategory.values();
                    int next = (currentCategory.ordinal() + 1) % cats.length;
                    currentCategory = cats[next];
                    currentPreviewIndex = 0;
                    this.init();
                },
                I18n.format("visor.options.item_offsets.category.tooltip")
        ));

        // Row 0 RIGHT: Hand switcher (Right / Left)
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 0,
                () -> "§6" + I18n.format("visor.options.item_offsets.hand", currentHand.getDisplayName()),
                () -> {
                    currentHand = (currentHand == HandTarget.RIGHT) ? HandTarget.LEFT : HandTarget.RIGHT;
                    this.init();
                },
                I18n.format("visor.options.item_offsets.hand.tooltip")
        ));

        // Row 1 LEFT: Preview item switcher
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 1,
                () -> "§b" + I18n.format("visor.options.item_offsets.preview_item", getActivePreviewItemName()),
                () -> {
                    cyclePreviewItem();
                    this.init();
                },
                I18n.format("visor.options.item_offsets.preview_item.tooltip")
        ));

        // Row 1 RIGHT: Scale (0.05 to 2.00)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 1,
                I18n.format("visor.options.item_offsets.scale"),
                0.05f, 2.00f, 0.01f,
                () -> getActiveOffset().scale,
                v -> {
                    getActiveOffset().scale = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.2f", v),
                I18n.format("visor.options.item_offsets.scale.tooltip")
        ));

        // Row 2 LEFT: Pos X (-0.500m to +0.500m)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 2,
                I18n.format("visor.options.item_offsets.pos_x"),
                -0.500f, 0.500f, 0.005f,
                () -> getActiveOffset().posX,
                v -> {
                    getActiveOffset().posX = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.3f m", v),
                I18n.format("visor.options.item_offsets.pos_x.tooltip")
        ));

        // Row 2 RIGHT: Pos Y (-0.500m to +0.500m)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 2,
                I18n.format("visor.options.item_offsets.pos_y"),
                -0.500f, 0.500f, 0.005f,
                () -> getActiveOffset().posY,
                v -> {
                    getActiveOffset().posY = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.3f m", v),
                I18n.format("visor.options.item_offsets.pos_y.tooltip")
        ));

        // Row 3 LEFT: Pos Z (-0.500m to +0.500m)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 3,
                I18n.format("visor.options.item_offsets.pos_z"),
                -0.500f, 0.500f, 0.005f,
                () -> getActiveOffset().posZ,
                v -> {
                    getActiveOffset().posZ = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.3f m", v),
                I18n.format("visor.options.item_offsets.pos_z.tooltip")
        ));

        // Row 3 RIGHT: Pitch (-180° to +180°)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 3,
                I18n.format("visor.options.item_offsets.pitch"),
                -180.0f, 180.0f, 1.0f,
                () -> getActiveOffset().pitch,
                v -> {
                    getActiveOffset().pitch = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f°", v),
                I18n.format("visor.options.item_offsets.pitch.tooltip")
        ));

        // Row 4 LEFT: Yaw (-180° to +180°)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.LEFT, 4,
                I18n.format("visor.options.item_offsets.yaw"),
                -180.0f, 180.0f, 1.0f,
                () -> getActiveOffset().yaw,
                v -> {
                    getActiveOffset().yaw = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f°", v),
                I18n.format("visor.options.item_offsets.yaw.tooltip")
        ));

        // Row 4 RIGHT: Roll (-180° to +180°)
        widgets.add(new OptionWidget.SliderWidget(
                OptionWidgetPosition.RIGHT, 4,
                I18n.format("visor.options.item_offsets.roll"),
                -180.0f, 180.0f, 1.0f,
                () -> getActiveOffset().roll,
                v -> {
                    getActiveOffset().roll = v;
                    VRClientSettings.save();
                },
                v -> String.format("%.0f°", v),
                I18n.format("visor.options.item_offsets.roll.tooltip")
        ));

        // Row 5 LEFT: Reset defaults for current category & hand
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.LEFT, 5,
                () -> "§c" + I18n.format("visor.options.item_offsets.reset"),
                () -> {
                    VRClientSettings.resetHandOffset(currentCategory, currentHand == HandTarget.RIGHT);
                    VRClientSettings.save();
                    this.init();
                },
                I18n.format("visor.options.item_offsets.reset.tooltip")
        ));

        // Row 5 RIGHT: Done / Back
        widgets.add(new OptionWidget.ButtonWidget(
                OptionWidgetPosition.RIGHT, 5,
                () -> "§a" + I18n.format("visor.options.item_offsets.done"),
                () -> {
                    VRClientSettings.save();
                    if (screen != null) {
                        screen.popOptionsSet();
                    }
                },
                I18n.format("visor.options.item_offsets.done.tooltip")
        ));
    }
}
