package com.gtnhvr.gui.keyboard;

import com.gtnhvr.settings.VRClientSettings;
import net.minecraft.client.Minecraft;

import java.util.*;

public final class KeyboardLayouts {

    private static final Map<KeyboardLayout, KeyboardLayoutKeys> LAYOUT_KEYS = new EnumMap<>(KeyboardLayout.class);

    static {
        for (KeyboardLayout layout : KeyboardLayout.values()) {
            register(build(layout));
        }
    }

    private KeyboardLayouts() {}

    public static KeyboardLayoutKeys get(KeyboardLayout layout) {
        KeyboardLayoutKeys layoutKeys = LAYOUT_KEYS.get(layout);
        if (layoutKeys == null) {
            return getDefault();
        }
        return layoutKeys;
    }

    public static KeyboardLayoutKeys getDefault() {
        return get(KeyboardLayout.ENGLISH);
    }

    public static List<KeyboardLayout> getSelectableLayouts() {
        return Arrays.asList(KeyboardLayout.values());
    }

    public static List<KeyboardLayout> getSelected() {
        return deserialize(VRClientSettings.keyboardLayouts);
    }

    public static void setSelected(Collection<KeyboardLayout> layouts) {
        VRClientSettings.keyboardLayouts = serialize(layouts);
        VRClientSettings.save();
    }

    public static List<KeyboardLayout> getEffectiveSelected() {
        List<KeyboardLayout> base = getSelected();
        KeyboardLayout autoLayout = getAutoLayout();
        if (autoLayout == null) {
            if (base.isEmpty()) {
                return Collections.singletonList(KeyboardLayout.ENGLISH);
            }
            return base;
        }
        List<KeyboardLayout> merged = new ArrayList<>();
        merged.add(autoLayout);
        for (KeyboardLayout l : base) {
            if (l != autoLayout) {
                merged.add(l);
            }
        }
        return Collections.unmodifiableList(merged);
    }

    public static KeyboardLayout getAutoLayout() {
        if (!VRClientSettings.keyboardAutoLayout) return null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) return null;
        String langCode = mc.gameSettings.language;
        return KeyboardLayout.fromLangCode(langCode);
    }

    public static List<KeyboardLayout> deserialize(String rawValue) {
        Set<KeyboardLayout> result = new LinkedHashSet<>();
        if (rawValue != null && !rawValue.trim().isEmpty()) {
            for (String part : rawValue.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    KeyboardLayout layout = KeyboardLayout.valueOf(trimmed.toUpperCase());
                    result.add(layout);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (result.isEmpty()) {
            result.add(KeyboardLayout.ENGLISH);
            result.add(KeyboardLayout.RUSSIAN);
        }
        return new ArrayList<>(result);
    }

    public static String serialize(Iterable<KeyboardLayout> layouts) {
        Set<KeyboardLayout> normalized = new LinkedHashSet<>();
        for (KeyboardLayout layout : layouts) {
            if (layout != null) {
                normalized.add(layout);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (KeyboardLayout layout : normalized) {
            if (sb.length() > 0) sb.append(",");
            sb.append(layout.name());
        }
        return sb.toString();
    }

    private static void register(KeyboardLayoutKeys layoutKeys) {
        LAYOUT_KEYS.put(layoutKeys.getLayout(), layoutKeys);
    }

    private static KeyboardLayoutKeys build(KeyboardLayout layout) {
        KeyboardRow[] rows = layout.getRows();
        KeyboardKey[][] normalLayer = new KeyboardKey[rows.length][];
        KeyboardKey[][] shiftLayer = new KeyboardKey[rows.length][];

        for (int row = 0; row < rows.length; row++) {
            normalLayer[row] = buildRow(rows[row], false, 0);
            shiftLayer[row] = buildRow(rows[row], true, 1);
        }

        return new KeyboardLayoutKeys(layout, normalLayer, shiftLayer);
    }

    private static KeyboardKey[] buildRow(KeyboardRow row, boolean shifted, int fallbackModifiers) {
        KeyboardKey[] keys = new KeyboardKey[row.size()];
        for (int i = 0; i < keys.length; i++) {
            String symbol = row.symbol(i, shifted);
            keys[i] = new KeyboardKey(
                    symbol,
                    symbol,
                    row.keyCodeAt(i),
                    fallbackModifiers
            );
        }
        return keys;
    }
}
