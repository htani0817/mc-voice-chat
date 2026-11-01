package jp.mc.voicechat.client;

import com.google.common.collect.ImmutableList;
import jp.mc.voicechat.CallState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 設定画面の「通話」タブの一覧。ボイスチャットの Mod を入れて入っている自分以外のプレイヤーを並べ、発信ボタンを付ける。
 */
final class CallList extends ContainerObjectSelectionList<CallList.Row> {
    private static final int ROW_WIDTH = 310;

    CallList(Minecraft mc, int width, int height, int y) {
        super(mc, width, height, y, 24);
        ClientPacketListener connection = mc.getConnection();
        VoiceClient vc = VoiceClient.get();
        if (connection == null || mc.player == null) {
            return;
        }
        UUID self = mc.player.getUUID();
        connection.getOnlinePlayers().stream()
                .filter(info -> !info.getProfile().id().equals(self) && vc.isVoiceUser(info.getProfile().id()))
                .sorted(Comparator.comparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
                .forEach(info -> addEntry(new Row(info.getProfile().id(), info.getProfile().name())));
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
        private final Button call;

        Row(UUID id, String name) {
            this.id = id;
            this.name = name;
            VoiceClient vc = VoiceClient.get();
            this.call = Button.builder(Component.translatable("mc_voice_chat.settings.call.dial"), b -> vc.startCall(id))
                    .size(70, 20).build();
            // 自分が通話していないときで、相手も通話中でないときだけかけられる
            this.call.active = vc.callState() == CallState.NONE && !vc.isInCall(id);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            VoiceClient vc = VoiceClient.get();
            call.setPosition(getContentRight() - call.getWidth(), getContentY() - 1);
            call.extractRenderState(g, mouseX, mouseY, a);
            int textY = getContentYMiddle() - 4;
            int dot = vc.isSpeaking(id) ? 0xFF4CD964 : 0xFF3A3A3A;
            g.fill(getContentX(), textY + 1, getContentX() + 6, textY + 7, dot);
            g.text(Minecraft.getInstance().font, name, getContentX() + 10, textY, 0xFFFFFFFF, true);
            if (vc.isInCall(id)) {
                g.text(Minecraft.getInstance().font, Component.translatable("mc_voice_chat.banner.in_call"),
                        getContentX() + 150, textY, 0xFF55FF55, true);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(call);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(call);
        }
    }
}
