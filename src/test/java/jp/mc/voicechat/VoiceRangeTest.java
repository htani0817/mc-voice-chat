package jp.mc.voicechat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceRangeTest {
    @Test
    void 範囲は要件どおりの距離() {
        assertEquals(5, VoiceRange.WHISPER.blocks());
        assertEquals(10, VoiceRange.NORMAL.blocks());
        assertEquals(20, VoiceRange.SHOUT.blocks());
        assertEquals(30, VoiceRange.SUPERSHOUT.blocks());
    }

    @Test
    void 切替は4段階を巡回する() {
        assertEquals(VoiceRange.NORMAL, VoiceRange.WHISPER.next());
        assertEquals(VoiceRange.SHOUT, VoiceRange.NORMAL.next());
        assertEquals(VoiceRange.SUPERSHOUT, VoiceRange.SHOUT.next());
        assertEquals(VoiceRange.WHISPER, VoiceRange.SUPERSHOUT.next());
    }

    @Test
    void おかしな番号はnormalにする() {
        assertEquals(VoiceRange.SHOUT, VoiceRange.byId(2));
        assertEquals(VoiceRange.NORMAL, VoiceRange.byId(-1));
        assertEquals(VoiceRange.NORMAL, VoiceRange.byId(9));
    }
}
