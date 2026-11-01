package jp.mc.voicechat.client;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 設定画面の「プレイヤー」タブ。いまサーバーにいる自分以外のプレイヤーを並べ、1 人ずつ音量とミュートを変える。
 */
final class PlayerVolumeList extends ContainerObjectSelectionList<PlayerVolumeList.Row> {
    private static final int ROW_WIDTH = 310;

    PlayerVolumeList(Minecraft mc, int width, int height, int y) {
        super(mc, width, height, y, 24);
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) {
            return;
        }
        UUID self = mc.player.getUUID();
        connection.getOnlinePlayers().stream()
                .filter(info -> !info.getProfile().id().equals(self))
                .sorted(Comparator.comparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
                .forEach(info -> addEntry(new Row(info)));
    }

    boolean isEmpty() {
        return children().isEmpty();
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final UUID id;
        private final String name;
        private final ValueSlider volume;
        private final Button mute;

        Row(PlayerInfo info) {
            this.id = info.getProfile().id();
            this.name = info.getProfile().name();
            ClientConfig config = VoiceClient.get().config();
            this.volume = new ValueSlider(0, 0, 140, 0, 2, config.playerVolume(id),
                    v -> ValueSlider.percent("mc_voice_chat.settings.player_volume", v),
                    v -> config.setPlayerVolume(id, v));
            this.mute = Button.builder(muteLabel(), b -> {
                config.setPlayerMuted(id, !config.isPlayerMuted(id));
                b.setMessage(muteLabel());
            }).size(70, 20).build();
        }

        private Component muteLabel() {
            return Component.translatable(VoiceClient.get().config().isPlayerMuted(id)
                    ? "mc_voice_chat.settings.player_muted" : "mc_voice_chat.settings.player_unmuted");
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int right = getContentRight();
            int buttonY = getContentY() - 1;
            mute.setPosition(right - mute.getWidth(), buttonY);
            volume.setPosition(mute.getX() - 4 - volume.getWidth(), buttonY);
            mute.extractRenderState(g, mouseX, mouseY, a);
            volume.extractRenderState(g, mouseX, mouseY, a);
            int textY = getContentYMiddle() - 4;
            // 話し中なら名前の左に緑の点
            int dot = VoiceClient.get().isSpeaking(id) ? 0xFF4CD964 : 0xFF3A3A3A;
            g.fill(getContentX(), textY + 1, getContentX() + 6, textY + 7, dot);
            g.text(Minecraft.getInstance().font, name, getContentX() + 10, textY, 0xFFFFFFFF, true);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(volume, mute);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(volume, mute);
        }
    }
}
