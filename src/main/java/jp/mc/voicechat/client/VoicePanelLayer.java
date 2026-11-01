package jp.mc.voicechat.client;

import jp.mc.voicechat.CallState;
import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 画面の右端中央に出す、通話とパーティーVCの様子。
 * <ul>
 * <li>通話: 着信中・発信中・通話中（経過時間）と相手の名前</li>
 * <li>パーティー: パーティー名、入っている人（話している人は名前の左の点が緑）、話すときのキー</li>
 * </ul>
 * どちらも無いときは何も出さない。F1 で HUD を隠すと一緒に消える。
 */
public final class VoicePanelLayer implements GuiLayer {
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int LINE = 11;
    private static final int DOT = 6;
    private static final int BACKDROP = 0x88000000;
    private static final int TITLE = 0xFF55FFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    private static final int CALL_ACTIVE = 0xFF55FF55;
    private static final int CALL_RINGING = 0xFFFFDD55;
    private static final int DOT_ON = 0xFF4CD964;
    private static final int DOT_OFF = 0xFF3A3A3A;

    /** 1 行分。dot が null なら点を付けない */
    private record Row(Component text, int color, Boolean dot) {
    }

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        VoiceClient vc = VoiceClient.get();
        if (mc.gui.hud.isHidden() || mc.player == null || vc == null || !vc.isServerReady()) {
            return;
        }
        List<Row> rows = new ArrayList<>();
        addCallRows(vc, rows);
        VoiceStatePayload.Party party = vc.myParty();
        if (party != null) {
            if (!rows.isEmpty()) {
                rows.add(null); // 区切りの空き
            }
            addPartyRows(vc, party, mc.player.getUUID(), rows);
        }
        if (rows.isEmpty()) {
            return;
        }
        draw(g, mc.font, rows);
    }

    private static void addCallRows(VoiceClient vc, List<Row> rows) {
        CallState state = vc.callState();
        UUID peer = vc.callPeer();
        if (state == CallState.NONE || peer == null) {
            return;
        }
        String name = VoiceClient.nameOf(peer);
        switch (state) {
            case INCOMING -> {
                rows.add(new Row(Component.translatable("mc_voice_chat.panel.incoming", name), CALL_RINGING, null));
                rows.add(new Row(answerHint(), HINT, null));
            }
            case OUTGOING -> rows.add(new Row(Component.translatable("mc_voice_chat.panel.outgoing", name), CALL_RINGING, null));
            case ACTIVE -> {
                long s = vc.callSeconds();
                String time = String.format("%d:%02d", s / 60, s % 60);
                rows.add(new Row(Component.translatable("mc_voice_chat.panel.active", name, time), CALL_ACTIVE, vc.isSpeaking(peer)));
            }
            case NONE -> {
            }
        }
    }

    /** 着信に出る方法。通話キーが割り当ててあればそのキー、なければ設定画面のキー */
    private static Component answerHint() {
        if (!VoiceKeys.CALL.isUnbound()) {
            return Component.translatable("mc_voice_chat.panel.answer_key", VoiceKeys.CALL.getTranslatedKeyMessage());
        }
        return Component.translatable("mc_voice_chat.panel.answer_screen", VoiceKeys.SETTINGS.getTranslatedKeyMessage());
    }

    private static void addPartyRows(VoiceClient vc, VoiceStatePayload.Party party, UUID self, List<Row> rows) {
        rows.add(new Row(Component.translatable("mc_voice_chat.panel.party", party.name(), party.members().size()), TITLE, null));
        for (UUID m : party.members()) {
            boolean speaking = m.equals(self) ? vc.isPartyTransmitting() : vc.isSpeaking(m);
            rows.add(new Row(Component.literal(VoiceClient.nameOf(m)), TEXT, speaking));
        }
        rows.add(new Row(Component.translatable("mc_voice_chat.panel.ptt", VoiceKeys.PUSH_TO_TALK.getTranslatedKeyMessage()), HINT, null));
    }

    private static void draw(GuiGraphicsExtractor g, Font font, List<Row> rows) {
        int width = 0;
        for (Row r : rows) {
            if (r != null) {
                width = Math.max(width, font.width(r.text()) + (r.dot() != null ? DOT + 4 : 0));
            }
        }
        int boxW = width + PAD * 2;
        int boxH = rows.size() * LINE + PAD * 2 - 2;
        int x = g.guiWidth() - MARGIN - boxW;
        int y = (g.guiHeight() - boxH) / 2;
        g.fill(x, y, x + boxW, y + boxH, BACKDROP);
        int ty = y + PAD;
        for (Row r : rows) {
            if (r != null) {
                int tx = x + PAD;
                if (r.dot() != null) {
                    g.fill(tx, ty + 1, tx + DOT, ty + 1 + DOT, r.dot() ? DOT_ON : DOT_OFF);
                    tx += DOT + 4;
                }
                g.text(font, r.text(), tx, ty, r.color(), true);
            }
            ty += LINE;
        }
    }
}
