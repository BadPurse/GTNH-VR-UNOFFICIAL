package com.gtnhvr.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

public class BackhandCompat {
    private static boolean initialized = false;
    private static Method getOffhandItemMethod;
    private static Method swapToOffhandMethod;
    private static Method swapBackMethod;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> clazz = Class.forName("xonin.backhand.api.core.BackhandUtils");
            getOffhandItemMethod = clazz.getMethod("getOffhandItem", EntityPlayer.class);
            swapToOffhandMethod = clazz.getMethod("swapToOffhand", EntityPlayer.class);
            swapBackMethod = clazz.getMethod("swapBack", EntityPlayer.class, int.class);
        } catch (Throwable ignored) {}
    }

    public static ItemStack getOffhandItem(EntityPlayer player) {
        init();
        if (getOffhandItemMethod != null && player != null) {
            try {
                return (ItemStack) getOffhandItemMethod.invoke(null, player);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static int swapToOffhand(EntityPlayer player) {
        init();
        if (swapToOffhandMethod != null && player != null) {
            try {
                Object res = swapToOffhandMethod.invoke(null, player);
                if (res instanceof Integer) {
                    return (Integer) res;
                }
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    public static void swapBack(EntityPlayer player, int slot) {
        init();
        if (swapBackMethod != null && player != null && slot >= 0) {
            try {
                swapBackMethod.invoke(null, player, slot);
            } catch (Throwable ignored) {}
        }
    }
}
