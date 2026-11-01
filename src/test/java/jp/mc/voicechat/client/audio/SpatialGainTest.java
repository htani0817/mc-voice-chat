package jp.mc.voicechat.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialGainTest {
    @Test
    void 近くはそのまま_範囲の端と外は0() {
        assertEquals(1f, SpatialGain.distanceGain(0.5, 10));
        assertEquals(1f, SpatialGain.distanceGain(1, 10));
        assertEquals(0.5f, SpatialGain.distanceGain(5.5, 10), 1e-6);
        assertEquals(0f, SpatialGain.distanceGain(10, 10));
        assertEquals(0f, SpatialGain.distanceGain(12, 10));
    }

    @Test
    void 南を向いているとき東は左_西は右() {
        // yaw 0 = 南（+Z）向き。東は +X
        assertEquals(-1f, SpatialGain.pan(0, 0, 0f, 5, 0), 1e-6);
        assertEquals(1f, SpatialGain.pan(0, 0, 0f, -5, 0), 1e-6);
        assertEquals(0f, SpatialGain.pan(0, 0, 0f, 0, 5), 1e-6);
    }

    @Test
    void 西を向いているとき北は右() {
        // yaw 90 = 西（-X）向き。北は -Z
        assertEquals(1f, SpatialGain.pan(0, 0, 90f, 0, -5), 1e-6);
    }

    @Test
    void 正面は左右とも1_真横は反対の耳を少し残す() {
        assertEquals(1f, SpatialGain.left(0));
        assertEquals(1f, SpatialGain.right(0));
        assertEquals(1f, SpatialGain.right(1));
        float far = SpatialGain.left(1);
        assertTrue(far > 0 && far < 0.5f);
    }
}
