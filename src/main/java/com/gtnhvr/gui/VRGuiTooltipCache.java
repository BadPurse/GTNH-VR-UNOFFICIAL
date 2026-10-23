package com.gtnhvr.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Avoids rebuilding expensive modded item tooltips on every GUI frame.
 *
 * <p>GTNH items can run a substantial amount of mod integration code while building their
 * tooltip. GuiScreen normally asks for that tooltip again for every frame while the cursor
 * rests over a slot. Entries are invalidated when the stack, damage, count, NBT, player,
 * world or advanced-tooltip option changes and expire after a short interval so dynamic
 * third-party tooltips are refreshed too.</p>
 *
 * <p>The cache is deliberately small: the same few slots are normally hovered repeatedly,
 * and it must not become another owner of the entire creative item registry.</p>
 */
public final class VRGuiTooltipCache {
    private static final int MAX_ENTRIES = 96;
    private static final long MAX_AGE_NANOS = 1_500_000_000L;
    private static final List<Entry> CACHE = new ArrayList<>();

    private VRGuiTooltipCache() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized List getTooltip(ItemStack stack, EntityPlayer player, boolean advanced) {
        if (stack == null) {
            return Collections.emptyList();
        }

        NBTTagCompound tag = stack.getTagCompound();
        int tagHash = tag == null ? 0 : tag.hashCode();
        Object item = stack.getItem();
        Object world = player == null ? null : player.worldObj;
        long now = System.nanoTime();

        for (int i = CACHE.size() - 1; i >= 0; i--) {
            Entry entry = CACHE.get(i);
            if (entry.stack != stack) {
                continue;
            }

            if (entry.matches(item, stack.stackSize, stack.getItemDamage(), tag, tagHash,
                    player, world, advanced) && now - entry.createdAt < MAX_AGE_NANOS) {
                // Move to the end to maintain a tiny LRU without allocating a map/key per frame.
                CACHE.remove(i);
                CACHE.add(entry);
                // Callers (notably GuiContainerCreative) may append screen-specific lines.
                // Return a copy so cached base tooltip data can never accumulate those lines.
                return new ArrayList(entry.tooltip);
            }

            CACHE.remove(i);
            break;
        }

        List generated = stack.getTooltip(player, advanced);
        List snapshot = generated == null ? Collections.emptyList() : new ArrayList(generated);
        CACHE.add(new Entry(stack, item, stack.stackSize, stack.getItemDamage(), tag, tagHash,
                player, world, advanced, now, snapshot));
        if (CACHE.size() > MAX_ENTRIES) {
            CACHE.remove(0);
        }
        return new ArrayList(snapshot);
    }

    private static final class Entry {
        private final ItemStack stack;
        private final Object item;
        private final int stackSize;
        private final int damage;
        private final NBTTagCompound tag;
        private final int tagHash;
        private final EntityPlayer player;
        private final Object world;
        private final boolean advanced;
        private final long createdAt;
        private final List tooltip;

        private Entry(ItemStack stack, Object item, int stackSize, int damage,
                      NBTTagCompound tag, int tagHash, EntityPlayer player, Object world,
                      boolean advanced, long createdAt, List tooltip) {
            this.stack = stack;
            this.item = item;
            this.stackSize = stackSize;
            this.damage = damage;
            this.tag = tag;
            this.tagHash = tagHash;
            this.player = player;
            this.world = world;
            this.advanced = advanced;
            this.createdAt = createdAt;
            this.tooltip = tooltip;
        }

        private boolean matches(Object candidateItem, int candidateSize, int candidateDamage,
                                NBTTagCompound candidateTag, int candidateTagHash,
                                EntityPlayer candidatePlayer, Object candidateWorld,
                                boolean candidateAdvanced) {
            return item == candidateItem
                    && stackSize == candidateSize
                    && damage == candidateDamage
                    && tag == candidateTag
                    && tagHash == candidateTagHash
                    && player == candidatePlayer
                    && world == candidateWorld
                    && advanced == candidateAdvanced;
        }
    }
}
