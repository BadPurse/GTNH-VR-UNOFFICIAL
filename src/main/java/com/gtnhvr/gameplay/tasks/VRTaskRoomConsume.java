package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrPosef;

/**
 * 1:1 Visor TaskRoomConsume:
 * Allows the player to physically consume food and potions by bringing
 * the controller holding the food item to their mouth (<0.25m from HMD).
 * Delivers authentic haptic chewing/bite feedback pulses.
 * Connected directly to VRClientSettings (roomConsumeEnabled).
 */
@Lwjgl3Aware
public class VRTaskRoomConsume {
    private static final VRTaskRoomConsume INSTANCE = new VRTaskRoomConsume();

    private static final float MOUTH_DISTANCE = 0.25F;
    private static final float MOUTH_OFFSET_Y = 0.10F;

    private boolean isConsumingRight = false;
    private boolean isConsumingLeft = false;
    private int savedSlotLeft = -1;
    private long lastBiteTime = 0L;

    private VRTaskRoomConsume() {}

    public static VRTaskRoomConsume getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!VRClientSettings.roomConsumeEnabled && !VRClientSettings.roomConsume) {
            clear(mc);
            return;
        }

        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear(mc);
            return;
        }

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) return;

        Vector3f mouthPos = new Vector3f(
                headPose.position$().x(),
                headPose.position$().y() - MOUTH_OFFSET_Y,
                headPose.position$().z()
        );

        // 1. Check Right Hand
        ItemStack heldRight = mc.thePlayer.getHeldItem();
        boolean rightConsumable = isConsumable(heldRight);
        boolean rightInMouth = false;

        if (rightConsumable && handler.isRightHandTracked()) {
            Vector3f rightHandPos = handler.getRightHandPos();
            if (mouthPos.distance(rightHandPos) < MOUTH_DISTANCE) {
                rightInMouth = true;
            }
        }

        // 2. Check Left Hand
        ItemStack heldLeft = com.gtnhvr.gui.VRHotbarSelector.getInstance().getOffhandItemStack(mc);
        boolean leftConsumable = isConsumable(heldLeft);
        boolean leftInMouth = false;

        if (leftConsumable && handler.isLeftHandTracked()) {
            Vector3f leftHandPos = handler.getLeftHandPos();
            if (mouthPos.distance(leftHandPos) < MOUTH_DISTANCE) {
                leftInMouth = true;
            }
        }

        // Process Right Hand consuming
        if (rightInMouth && !isConsumingLeft) {
            if (!isConsumingRight) {
                isConsumingRight = true;
                // Trigger item use
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, heldRight);
            }

            // Chewing bite feedback pulse
            long now = System.currentTimeMillis();
            if (now - lastBiteTime > 200L) {
                lastBiteTime = now;
                handler.triggerHaptic(false, 6_000_000L, 0.50F, 0);
            }
            return;
        } else if (isConsumingRight) {
            isConsumingRight = false;
            if (mc.thePlayer.isUsingItem()) {
                mc.thePlayer.stopUsingItem();
            }
        }

        // Process Left Hand consuming
        if (leftInMouth && !isConsumingRight) {
            if (!isConsumingLeft) {
                isConsumingLeft = true;
                boolean handled = false;
                if (mc.thePlayer != null) {
                    int slot = com.gtnhvr.util.BackhandCompat.swapToOffhand(mc.thePlayer);
                    if (slot >= 0) {
                        savedSlotLeft = slot;
                        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                        handled = true;
                    }
                }
                if (!handled) {
                    int offSlot = com.gtnhvr.gui.VRHotbarSelector.getInstance().getOffhandSlot();
                    if (offSlot >= 0 && offSlot < 9 && mc.thePlayer.inventory != null) {
                        savedSlotLeft = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = offSlot;
                        if (mc.getNetHandler() != null) {
                            mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C09PacketHeldItemChange(offSlot));
                        }
                        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, heldLeft);
                    }
                }
            }

            // Chewing bite feedback pulse on left controller
            long now = System.currentTimeMillis();
            if (now - lastBiteTime > 200L) {
                lastBiteTime = now;
                handler.triggerHaptic(true, 6_000_000L, 0.50F, 0);
            }
            return;
        } else if (isConsumingLeft) {
            stopConsumingLeft(mc);
        }
    }

    private void stopConsumingLeft(Minecraft mc) {
        if (isConsumingLeft) {
            isConsumingLeft = false;
            if (mc != null && mc.thePlayer != null) {
                if (mc.thePlayer.isUsingItem()) {
                    mc.thePlayer.stopUsingItem();
                }
                if (savedSlotLeft != -1) {
                    com.gtnhvr.util.BackhandCompat.swapBack(mc.thePlayer, savedSlotLeft);
                    if (mc.thePlayer.inventory != null && mc.thePlayer.inventory.currentItem != savedSlotLeft) {
                        mc.thePlayer.inventory.currentItem = savedSlotLeft;
                        if (mc.getNetHandler() != null) {
                            mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C09PacketHeldItemChange(savedSlotLeft));
                        }
                    }
                    savedSlotLeft = -1;
                }
            }
        }
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        EnumAction action = stack.getItemUseAction();
        return (action == EnumAction.eat || action == EnumAction.drink);
    }

    public boolean isConsuming() {
        return isConsumingRight || isConsumingLeft;
    }

    public boolean isConsumingLeft() {
        return isConsumingLeft;
    }

    public void clear(Minecraft mc) {
        if (isConsumingRight) {
            isConsumingRight = false;
            if (mc != null && mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
                mc.thePlayer.stopUsingItem();
            }
        }
        if (isConsumingLeft) {
            stopConsumingLeft(mc);
        }
    }
}
