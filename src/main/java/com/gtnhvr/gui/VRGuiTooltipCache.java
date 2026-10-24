package com.gtnhvr.gui;

import com.gtnhvr.OpenXRRuntime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Avoids rebuilding expensive modded item tooltips on every VR GUI frame.
 *
 * <p>GTNH items can run substantial mod integration code while building a tooltip. Vanilla asks
 * for that tooltip every frame while the cursor rests over a slot. Entries are keyed by the
 * tooltip-relevant stack state rather than ItemStack identity/count, so taking, splitting,
 * moving or merging an otherwise identical stack can reuse the existing tooltip. Damage and
 * NBT changes invalidate the entry. A small TTL refreshes third-party tooltips that depend on
 * context not represented by the stack.</p>
 *
 * <p>The cache is only used in VR; desktop GUI behavior stays unchanged.</p>
 */
public final class VRGuiTooltipCache {
    private static final int MAX_ENTRIES = 96;
    private static final long MAX_AGE_NANOS = 30_000_000_000L;
    private static final List<Entry> CACHE = new ArrayList<>();

    private VRGuiTooltipCache() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized List getTooltip(ItemStack stack, EntityPlayer player, boolean advanced) {
        if (stack == null) {
            return Collections.emptyList();
        }
        if (!OpenXRRuntime.isSessionRunning()) {
            return stack.getTooltip(player, advanced);
        }

        VRGuiProfiler.tooltipRequest();
        NBTTagCompound tag = stack.getTagCompound();
        int tagHash = tag == null ? 0 : tag.hashCode();
        Object item = stack.getItem();
        Object world = player == null ? null : player.worldObj;
        long now = System.nanoTime();

        for (int i = CACHE.size() - 1; i >= 0; i--) {
            Entry entry = CACHE.get(i);
            if (entry.matches(item, stack.getItemDamage(), tag, tagHash,
                    player, world, advanced) && now - entry.createdAt < MAX_AGE_NANOS) {
                CACHE.remove(i);
                CACHE.add(entry);
                VRGuiProfiler.tooltipCacheHit();
                // GuiScreen may recolor lines; GuiContainerCreative may append a tab label.
                // A copy prevents those per-screen edits from contaminating cached base data.
                return new ArrayList(entry.tooltip);
            }
        }

        long generationStarted = System.nanoTime();
        List generated = stack.getTooltip(player, advanced);
        VRGuiProfiler.tooltipCacheMiss(System.nanoTime() - generationStarted);
        List snapshot = generated == null ? Collections.emptyList() : new ArrayList(generated);
        CACHE.add(new Entry(item, stack.getItemDamage(), tag, tagHash, player, world,
                advanced, now, snapshot));
        if (CACHE.size() > MAX_ENTRIES) {
            CACHE.remove(0);
        }
        return new ArrayList(snapshot);
    }

    private static final class Entry {
        private final Object item;
        private final int damage;
        private final NBTTagCompound tagReference;
        private final NBTTagCompound tagSnapshot;
        private final int tagHash;
        private final EntityPlayer player;
        private final Object world;
        private final boolean advanced;
        private final long createdAt;
        private final List tooltip;

        private Entry(Object item, int damage, NBTTagCompound tag, int tagHash,
                      EntityPlayer player, Object world, boolean advanced,
                      long createdAt, List tooltip) {
            this.item = item;
            this.damage = damage;
            this.tagReference = tag;
            this.tagSnapshot = tag == null ? null : (NBTTagCompound) tag.copy();
            this.tagHash = tagHash;
            this.player = player;
            this.world = world;
            this.advanced = advanced;
            this.createdAt = createdAt;
            this.tooltip = tooltip;
        }

        private boolean matches(Object candidateItem, int candidateDamage,
                                NBTTagCompound candidateTag, int candidateTagHash,
                                EntityPlayer candidatePlayer, Object candidateWorld,
                                boolean candidateAdvanced) {
            boolean sameTag = tagReference == candidateTag
                    || (tagReference == null && candidateTag == null)
                    || (candidateTag != null && tagSnapshot != null && candidateTag.equals(tagSnapshot));
            return item == candidateItem
                    && damage == candidateDamage
                    && tagHash == candidateTagHash
                    && sameTag
                    && player == candidatePlayer
                    && world == candidateWorld
                    && advanced == candidateAdvanced;
        }
    }
}
