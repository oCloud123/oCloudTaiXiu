package com.ocloud.taixiu.game;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaiXiuDisplayFrameTest {
    private static final double EPSILON = 0.00001d;

    @Test
    void ignoresPitchAndNeutralizesEntityRotation() {
        TaiXiuDisplayFrame.Frame lookingDown = TaiXiuDisplayFrame.fromEye(
                new Location(null, 12.5d, 80.0d, -4.5d, 37.0f, 89.0f));
        TaiXiuDisplayFrame.Frame lookingUp = TaiXiuDisplayFrame.fromEye(
                new Location(null, 12.5d, 80.0d, -4.5d, 37.0f, -89.0f));

        assertVectorEquals(lookingDown.forward(), lookingUp.forward());
        assertVectorEquals(lookingDown.right(), lookingUp.right());
        assertEquals(0.0f, lookingDown.anchor().getYaw(), EPSILON);
        assertEquals(0.0f, lookingDown.anchor().getPitch(), EPSILON);
        assertEquals(12.5d, lookingDown.anchor().getX(), EPSILON);
        assertEquals(80.0d, lookingDown.anchor().getY(), EPSILON);
        assertEquals(-4.5d, lookingDown.anchor().getZ(), EPSILON);
    }

    @Test
    void buildsExpectedScreenAxesForCardinalDirections() {
        assertAxes(0.0f, new Vector(0, 0, 1), new Vector(-1, 0, 0));
        assertAxes(90.0f, new Vector(-1, 0, 0), new Vector(0, 0, -1));
        assertAxes(-90.0f, new Vector(1, 0, 0), new Vector(0, 0, 1));
        assertAxes(180.0f, new Vector(0, 0, -1), new Vector(1, 0, 0));
    }

    @Test
    void rotationMapsLocalBoardAxesWithoutTiltingUp() {
        TaiXiuDisplayFrame.Frame frame = TaiXiuDisplayFrame.fromEye(
                new Location(null, 0, 64, 0, 63.0f, 72.0f));
        Vector3f transformedRight = frame.rotation().transform(new Vector3f(1, 0, 0));
        Vector3f transformedUp = frame.rotation().transform(new Vector3f(0, 1, 0));

        assertEquals(frame.right().getX(), transformedRight.x, EPSILON);
        assertEquals(0.0d, transformedRight.y, EPSILON);
        assertEquals(frame.right().getZ(), transformedRight.z, EPSILON);
        assertEquals(0.0d, transformedUp.x, EPSILON);
        assertEquals(1.0d, transformedUp.y, EPSILON);
        assertEquals(0.0d, transformedUp.z, EPSILON);
    }

    private static void assertAxes(float yaw, Vector expectedForward, Vector expectedRight) {
        TaiXiuDisplayFrame.Frame frame = TaiXiuDisplayFrame.fromEye(
                new Location(null, 0, 64, 0, yaw, 45.0f));
        assertVectorEquals(expectedForward, frame.forward());
        assertVectorEquals(expectedRight, frame.right());
        assertVectorEquals(new Vector(0, 1, 0), frame.up());
    }

    private static void assertVectorEquals(Vector expected, Vector actual) {
        assertEquals(expected.getX(), actual.getX(), EPSILON);
        assertEquals(expected.getY(), actual.getY(), EPSILON);
        assertEquals(expected.getZ(), actual.getZ(), EPSILON);
    }
}
