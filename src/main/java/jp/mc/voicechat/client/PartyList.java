package jp.mc.voicechat.client;

import com.google.common.collect.ImmutableList;
import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 設定画面の「パーティー」タブの一覧。サーバーにあるパーティーを並べ、参加ボタンを付ける。
 * パスワード付きのパーティーは、参加を押すとパスワードを聞く画面を出す。作った人には削除ボタンも出す。
 */
final class PartyList extends ContainerObjectSelectionList<PartyList.Row> {
    private static final int ROW_WIDTH = 310;

    private final Screen screen;

    PartyList(Minecraft mc, int width, int height, int y, Screen screen) {
        super(mc, width, height, y, 24);
        this.screen = screen;
        for (VoiceStatePayload.Party p : VoiceClient.get().parties()) {
            addEntry(new Row(p));
        }
    }

    boolean isEmpty() {
        return children().isEmpty();
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final VoiceStatePayload.Party party;
        private final Button join;
        private final Button delete;

        Row(VoiceStatePayload.Party party) {
            this.party = party;
            VoiceClient vc = VoiceClient.get();
            UUID self = vc.selfId();
            boolean owner = party.owner().equals(self);
            this.join = Button.builder(Component.translatable("mc_voice_chat.settings.party.join"), b -> {
                if (party.locked() && !owner) {
                    Minecraft.getInstance().gui.setScreen(new PartyPasswordScreen(screen, party));
                } else {
                    vc.joinParty(party.id(), "");
                }
            }).size(50, 20).build();
            this.join.active = !party.members().contains(self);
            this.delete = owner
                    ? Button.builder(Component.translatable("mc_voice_chat.settings.party.delete"),
                            b -> VoiceSettingsScreen.confirmDelete(screen, party)).size(50, 20).build()
                    : null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int right = getContentRight();
            int buttonY = getContentY() - 1;
            join.setPosition(right - join.getWidth(), buttonY);
            join.extractRenderState(g, mouseX, mouseY, a);
            if (delete != null) {
                delete.setPosition(join.getX() - 4 - delete.getWidth(), buttonY);
                delete.extractRenderState(g, mouseX, mouseY, a);
            }
            var font = Minecraft.getInstance().font;
            int textY = getContentYMiddle() - 4;
            Component label = party.locked()
                    ? Component.translatable("mc_voice_chat.settings.party.locked_name", party.name())
                    : Component.literal(party.name());
            g.text(font, label, getContentX(), textY, 0xFFFFFFFF, true);
            g.text(font, Component.translatable("mc_voice_chat.settings.party.count", party.members().size()),
                    getContentX() + 150, textY, 0xFFA0A0A0, true);
        }

        private List<Button> buttons() {
            List<Button> out = new ArrayList<>(2);
            if (delete != null) {
                out.add(delete);
            }
            out.add(join);
            return out;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.copyOf(buttons());
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.copyOf(buttons());
        }
    }
}
