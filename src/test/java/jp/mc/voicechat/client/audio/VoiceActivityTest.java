package jp.mc.voicechat.client.audio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceActivityTest {
    @Test
    void しきい値を超えたら送り_余韻のあと止める() {
        VoiceActivity va = new VoiceActivity(3);
        assertFalse(va.update(-50, -40));
        assertTrue(va.update(-30, -40));
        assertTrue(va.update(-60, -40));
        assertTrue(va.update(-60, -40));
        assertFalse(va.update(-60, -40));
    }

    @Test
    void 無音は下限_最大振幅は0dB() {
        short[] silent = new short[960];
        assertEquals(VoiceActivity.SILENCE_DB, VoiceActivity.levelDb(silent, silent.length));
        short[] full = new short[960];
        Arrays.fill(full, Short.MAX_VALUE);
        assertEquals(0, VoiceActivity.levelDb(full, full.length), 1e-6);
        short[] half = new short[960];
        Arrays.fill(half, (short) (Short.MAX_VALUE / 2));
        assertEquals(-6.02, VoiceActivity.levelDb(half, half.length), 0.01);
    }
}
