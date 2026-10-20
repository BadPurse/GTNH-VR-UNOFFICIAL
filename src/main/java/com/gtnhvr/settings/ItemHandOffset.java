package com.gtnhvr.settings;

public class ItemHandOffset {
    public float scale;
    public float posX;
    public float posY;
    public float posZ;
    public float pitch;
    public float yaw;
    public float roll;

    public ItemHandOffset(float scale, float posX, float posY, float posZ, float pitch, float yaw, float roll) {
        set(scale, posX, posY, posZ, pitch, yaw, roll);
    }

    public void set(float scale, float posX, float posY, float posZ, float pitch, float yaw, float roll) {
        this.scale = scale;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public void copyFrom(ItemHandOffset other) {
        set(other.scale, other.posX, other.posY, other.posZ, other.pitch, other.yaw, other.roll);
    }
}
