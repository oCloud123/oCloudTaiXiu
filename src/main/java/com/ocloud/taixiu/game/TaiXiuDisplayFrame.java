package com.ocloud.taixiu.game;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

final class TaiXiuDisplayFrame {
    private TaiXiuDisplayFrame() {
    }

    static Frame fromEye(Location eye) {
        float yaw = eye.getYaw();
        double radians = Math.toRadians(yaw);

        Vector forward = new Vector(-Math.sin(radians), 0.0d, Math.cos(radians)).normalize();
        Vector right = new Vector(-forward.getZ(), 0.0d, forward.getX()).normalize();
        Vector up = new Vector(0.0d, 1.0d, 0.0d);

        Location anchor = eye.clone();
        anchor.setYaw(0.0f);
        anchor.setPitch(0.0f);

        float boardYaw = (float) Math.atan2(-right.getZ(), right.getX());
        Quaternionf rotation = new Quaternionf().rotationY(boardYaw);
        return new Frame(anchor, forward, right, up, rotation);
    }

    record Frame(Location anchor, Vector forward, Vector right, Vector up, Quaternionf rotation) {
        Frame {
            anchor = anchor.clone();
            forward = forward.clone();
            right = right.clone();
            up = up.clone();
            rotation = new Quaternionf(rotation);
        }
    }
}
