package jp.mc.voicechat.client;

import jp.mc.voicechat.VoiceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {
    @TempDir
    Path dir;

    @Test
    void 無ければ既定値で作る() {
        Path file = dir.resolve("sub").resolve("client.json");
        ClientConfig c = ClientConfig.load(file);
        assertTrue(Files.exists(file));
        assertEquals(VoiceRange.NORMAL, c.range);
        assertEquals(ClientConfig.TalkMode.VOICE, c.talkMode);
        assertEquals(ClientConfig.IconCorner.BOTTOM_LEFT, c.iconCorner);
        assertFalse(c.muted);
    }

    @Test
    void 保存した値とプレイヤーごとの設定を読み戻せる() {
        Path file = dir.resolve("client.json");
        UUID alice = UUID.randomUUID();
        ClientConfig c = ClientConfig.load(file);
        c.range = VoiceRange.SHOUT;
        c.muted = true;
        c.setPlayerVolume(alice, 1.5);
        c.setPlayerMuted(alice, true);
        c.save();

        ClientConfig again = ClientConfig.load(file);
        assertEquals(VoiceRange.SHOUT, again.range);
        assertTrue(again.muted);
        assertEquals(1.5, again.playerVolume(alice));
        assertTrue(again.isPlayerMuted(alice));
        assertEquals(1.0, again.playerVolume(UUID.randomUUID()));
    }
}
