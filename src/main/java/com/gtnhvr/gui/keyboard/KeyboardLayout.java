package com.gtnhvr.gui.keyboard;

import static com.gtnhvr.gui.keyboard.KeyboardRow.bottom;
import static com.gtnhvr.gui.keyboard.KeyboardRow.home;
import static com.gtnhvr.gui.keyboard.KeyboardRow.numbers;
import static com.gtnhvr.gui.keyboard.KeyboardRow.top;

public enum KeyboardLayout {
    ENGLISH("EN", "English",
            numbers("`1234567890-=", "~!@#$%^&*()_+"),
            top("qwertyuiop[]\\", "QWERTYUIOP{}|"),
            home("asdfghjkl;'", "ASDFGHJKL:\""),
            bottom("zxcvbnm,./", "ZXCVBNM<>?")),
    RUSSIAN("RU", "Russian",
            numbers("ё1234567890-=", "Ё!\"№;%:?*()_+"),
            top("йцукенгшщзхъ\\", "ЙЦУКЕНГШЩЗХЪ/"),
            home("фывапролджэ", "ФЫВАПРОЛДЖЭ"),
            bottom("ячсмитьбю.", "ЯЧСМИТЬБЮ,")),
    UKRAINIAN("UA", "Ukrainian",
            numbers("'1234567890-=", "₴!\"№;%:?*()_+"),
            top("йцукенгшщзхїґ", "ЙЦУКЕНГШЩЗХЇҐ"),
            home("фівапролджє", "ФІВАПРОЛДЖЄ"),
            bottom("ячсмитьбю.", "ЯЧСМИТЬБЮ,")),
    GERMAN("DE", "German",
            numbers("^1234567890ß´", "°!\"§$%&/()=?`"),
            top("qwertzuiopü+#", "QWERTZUIOPÜ*'"),
            home("asdfghjklöä", "ASDFGHJKLÖÄ"),
            bottom("yxcvbnm,.-", "YXCVBNM;:_")),
    FRENCH("FR", "French",
            numbers("²&é\"'(-è_çà)=", "³1234567890°+"),
            top("azertyuiop^$*", "AZERTYUIOP¨£µ"),
            home("qsdfghjklmù", "QSDFGHJKLM%"),
            bottom("wxcvbn,;:!", "WXCVBN?./§")),
    SPANISH("ES", "Spanish",
            numbers("º1234567890'¡", "ª!\"·$%&/()=?¿"),
            top("qwertyuiop`+ç", "QWERTYUIOP^*Ç"),
            home("asdfghjklñ´", "ASDFGHJKLÑ¨"),
            bottom("zxcvbnm,.-", "ZXCVBNM;:_")),
    ITALIAN("IT", "Italian",
            numbers("\\1234567890'ì", "|!\"£$%&/()=?^"),
            top("qwertyuiopè+ù", "QWERTYUIOPé*§"),
            home("asdfghjklòà", "ASDFGHJKLç°"),
            bottom("zxcvbnm,.-", "ZXCVBNM;:_")),
    PORTUGUESE("PT", "Portuguese",
            numbers("\\1234567890'«", "|!\"#$%&/()=?»"),
            top("qwertyuiop+´~", "QWERTYUIOP*`^"),
            home("asdfghjklçº", "ASDFGHJKLÇª"),
            bottom("zxcvbnm,.-", "ZXCVBNM;:_")),
    HUNGARIAN("HU", "Hungarian",
            numbers("0123456789öüó", "§'\"+!%/=()ÖÜÓ"),
            top("qwertzuiopőúű", "QWERTZUIOPŐÚŰ"),
            home("asdfghjkléá", "ASDFGHJKLÉÁ"),
            bottom("yxcvbnm,.-í", "YXCVBNM?:_Í"));

    private final String label;
    private final String displayName;
    private final KeyboardRow[] rows;

    KeyboardLayout(String label, String displayName, KeyboardRow... rows) {
        this.label = label;
        this.displayName = displayName;
        this.rows = rows;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayName() {
        return displayName;
    }

    public KeyboardRow[] getRows() {
        return rows;
    }

    public KeyboardLayout next() {
        KeyboardLayout[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static KeyboardLayout fromLangCode(String langCode) {
        if (langCode == null) return null;
        String lower = langCode.toLowerCase();
        if (lower.startsWith("en")) return ENGLISH;
        if (lower.startsWith("ru")) return RUSSIAN;
        if (lower.startsWith("uk")) return UKRAINIAN;
        if (lower.startsWith("de")) return GERMAN;
        if (lower.startsWith("fr")) return FRENCH;
        if (lower.startsWith("es")) return SPANISH;
        if (lower.startsWith("it")) return ITALIAN;
        if (lower.startsWith("pt")) return PORTUGUESE;
        if (lower.startsWith("hu")) return HUNGARIAN;
        return null;
    }
}
