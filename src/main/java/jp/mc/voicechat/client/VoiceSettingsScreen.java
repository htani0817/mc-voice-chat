package jp.mc.voicechat.client;

import jp.mc.voicechat.VoiceRange;
import jp.mc.voicechat.client.ClientConfig.IconCorner;
import jp.mc.voicechat.client.ClientConfig.TalkMode;
import jp.mc.voicechat.client.audio.AudioDevices;
import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ボイスチャットの設定画面。V キーか、Mod 一覧の「設定」から開く。
 * 「全般」タブで話し方・音量・機器・アイコンを、「プレイヤー」タブで 1 人ずつの音量を変える。
 * 「通話」タブで発信・応答・切断を、「パーティー」タブでパーティーVCの作成・参加・変更・削除をする。
 */
public final class VoiceSettingsScreen extends Screen {
    public enum Tab { GENERAL, PLAYERS, CALL, PARTY }

    /** パーティー名とパスワードの入力の上限（サーバーの PartyBook と同じ） */
    static final int MAX_NAME = 24;
    static final int MAX_PASSWORD = 32;
    private static final int TAB_W = 76;

    private static final int COL = 150;
    private static final int GAP = 10;
    private static final int ROW = 24;
    private static final int TOP = 52;
    /** 音量メーターの目盛り（dB）。-60 で左端、0 で右端 */
    private static final double METER_MIN_DB = -60;

    private final @Nullable Screen parent;
    private Tab tab;
    private boolean audioChanged;
    private @Nullable ValueSlider threshold;
    private @Nullable PlayerVolumeList players;
    private @Nullable CallList calls;
    private @Nullable PartyList parties;
    /** 通話・パーティーの様子が変わったら作り直す */
    private int seenVersion;
    // パーティーの名前・パスワードの入力。作り直しても消えないように画面側に持つ
    private String nameDraft = "";
    private String passwordDraft = "";
    /** 入力欄に入れてあるのがどのパーティーの名前か（0 = 新しく作る用） */
    private int draftPartyId;
    private int meterX;
    private int meterY;
    private int statusY;

    public VoiceSettingsScreen(@Nullable Screen parent, Tab tab) {
        super(Component.translatable("mc_voice_chat.settings.title"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override
    public void added() {
        super.added();
        VoiceClient.get().setPreview(true);
    }

    @Override
    public void removed() {
        VoiceClient vc = VoiceClient.get();
        vc.config().save();
        if (audioChanged) {
            audioChanged = false;
            vc.restartAudio();
        }
        vc.setPreview(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // マイクテスト中もゲームを止めない
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    protected void init() {
        int cx = width / 2;
        Tab[] tabs = Tab.values();
        int tabsLeft = cx - (tabs.length * (TAB_W + 4) - 4) / 2;
        for (Tab t : tabs) {
            Button b = addRenderableWidget(Button.builder(
                            Component.translatable("mc_voice_chat.settings.tab." + t.name().toLowerCase(Locale.ROOT)), x -> switchTab(t))
                    .bounds(tabsLeft + t.ordinal() * (TAB_W + 4), 24, TAB_W, 20).build());
            b.active = tab != t;
        }
        seenVersion = VoiceClient.get().stateVersion();
        players = null;
        calls = null;
        parties = null;

        switch (tab) {
            case GENERAL -> initGeneral(cx);
            case PLAYERS -> players = addRenderableWidget(new PlayerVolumeList(minecraft, width, height - TOP - 36, TOP));
            case CALL -> initCall(cx);
            case PARTY -> initParty(cx);
        }

        int footerY = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.keybinds"),
                b -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(cx - COL - GAP / 2, footerY, COL, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + GAP / 2, footerY, COL, 20).build());
    }

    private void switchTab(Tab next) {
        tab = next;
        rebuildWidgets();
    }

    private void initGeneral(int cx) {
        VoiceClient vc = VoiceClient.get();
        ClientConfig config = vc.config();
        int left = cx - COL - GAP / 2;
        int right = cx + GAP / 2;

        // 1 行目: 話し方 / ミュート
        addRenderableWidget(CycleButton.<TalkMode>builder(
                        m -> Component.translatable("mc_voice_chat.talk_mode." + m.name().toLowerCase(Locale.ROOT)), config.talkMode)
                .withValues(TalkMode.values())
                .create(left, row(0), COL, 20, Component.translatable("mc_voice_chat.settings.talk_mode"), (b, v) -> {
                    config.talkMode = v;
                    if (threshold != null) {
                        threshold.active = v == TalkMode.VOICE;
                    }
                }));
        addRenderableWidget(CycleButton.onOffBuilder(config.muted)
                .create(right, row(0), COL, 20, Component.translatable("mc_voice_chat.settings.mute"), (b, v) -> vc.setMuted(v)));

        // 2 行目: 音声検知のしきい値 / 声の範囲
        threshold = addRenderableWidget(new ValueSlider(left, row(1), COL, METER_MIN_DB, 0, config.thresholdDb,
                v -> Component.translatable("mc_voice_chat.settings.threshold", Math.round(v)),
                v -> config.thresholdDb = Math.round(v)));
        threshold.active = config.talkMode == TalkMode.VOICE;
        addRenderableWidget(CycleButton.<VoiceRange>builder(r -> Component.literal(r.label() + " (" + r.blocks() + ")"), config.range)
                .withValues(VoiceRange.values())
                .create(right, row(1), COL, 20, Component.translatable("mc_voice_chat.settings.range"), (b, v) -> vc.setRange(v)));

        // 3 行目: マイクの音量メーター（描画だけ） / マイクテスト
        meterX = left;
        meterY = row(2);
        addRenderableWidget(CycleButton.onOffBuilder(vc.isMicTest())
                .create(right, row(2), COL, 20, Component.translatable("mc_voice_chat.settings.mic_test"), (b, v) -> vc.setMicTest(v)));

        // 4 行目: マイク音量 / 受信音量
        addRenderableWidget(new ValueSlider(left, row(3), COL, 0, 2, config.micGain,
                v -> ValueSlider.percent("mc_voice_chat.settings.mic_gain", v), v -> config.micGain = v));
        addRenderableWidget(new ValueSlider(right, row(3), COL, 0, 2, config.masterVolume,
                v -> ValueSlider.percent("mc_voice_chat.settings.master_volume", v), v -> config.masterVolume = v));

        // 5 行目: マイク / スピーカーの機器
        addRenderableWidget(deviceButton(left, row(4), "mc_voice_chat.settings.mic_device",
                AudioDevices.microphones(), config.micDevice, v -> config.micDevice = v));
        addRenderableWidget(deviceButton(right, row(4), "mc_voice_chat.settings.speaker_device",
                AudioDevices.speakers(), config.speakerDevice, v -> config.speakerDevice = v));

        // 6 行目: アイコンの位置 / 範囲名の表示
        addRenderableWidget(CycleButton.<IconCorner>builder(
                        c -> Component.translatable("mc_voice_chat.corner." + c.name().toLowerCase(Locale.ROOT)), config.iconCorner)
                .withValues(IconCorner.values())
                .create(left, row(5), COL, 20, Component.translatable("mc_voice_chat.settings.icon_corner"), (b, v) -> config.iconCorner = v));
        addRenderableWidget(CycleButton.onOffBuilder(config.showRangeLabel)
                .create(right, row(5), COL, 20, Component.translatable("mc_voice_chat.settings.range_label"), (b, v) -> config.showRangeLabel = v));

        statusY = row(6) + 4;
    }

    // ---- 通話タブ ----

    private void initCall(int cx) {
        VoiceClient vc = VoiceClient.get();
        int left = cx - COL - GAP / 2;
        int right = cx + GAP / 2;
        int y = TOP + 14;
        switch (vc.callState()) {
            case INCOMING -> {
                addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.call.accept"), b -> vc.acceptCall())
                        .bounds(left, y, COL, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.call.decline"), b -> vc.hangUp())
                        .bounds(right, y, COL, 20).build());
            }
            case OUTGOING -> addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.call.cancel"), b -> vc.hangUp())
                    .bounds(left, y, COL, 20).build());
            case ACTIVE -> addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.call.hang_up"), b -> vc.hangUp())
                    .bounds(left, y, COL, 20).build());
            case NONE -> {
            }
        }
        int listTop = y + ROW + 4;
        calls = addRenderableWidget(new CallList(minecraft, width, height - listTop - 36, listTop));
    }

    /** 通話タブの上に出す、いまの通話の様子 */
    private Component callStatus() {
        VoiceClient vc = VoiceClient.get();
        UUID peer = vc.callPeer();
        String name = peer == null ? "" : VoiceClient.nameOf(peer);
        return switch (vc.callState()) {
            case NONE -> Component.translatable("mc_voice_chat.settings.call.none");
            case OUTGOING -> Component.translatable("mc_voice_chat.panel.outgoing", name);
            case INCOMING -> Component.translatable("mc_voice_chat.panel.incoming", name);
            case ACTIVE -> {
                long s = vc.callSeconds();
                yield Component.translatable("mc_voice_chat.panel.active", name, String.format("%d:%02d", s / 60, s % 60));
            }
        };
    }

    // ---- パーティータブ ----

    private void initParty(int cx) {
        VoiceClient vc = VoiceClient.get();
        VoiceStatePayload.Party mine = vc.myParty();
        boolean owner = mine != null && mine.owner().equals(vc.selfId());
        int left = cx - COL - GAP / 2;
        int right = cx + GAP / 2;
        int y = TOP + 14;

        // 入力欄の中身を、いまの立場に合わせて入れ替える（作った人は今の名前から直す）
        int want = owner ? mine.id() : 0;
        if (draftPartyId != want) {
            draftPartyId = want;
            nameDraft = owner ? mine.name() : "";
            passwordDraft = "";
        }

        if (mine == null || owner) {
            EditBox name = addRenderableWidget(new EditBox(font, left, y, COL, 20, Component.translatable("mc_voice_chat.settings.party.name")));
            name.setMaxLength(MAX_NAME);
            name.setValue(nameDraft);
            name.setHint(Component.translatable("mc_voice_chat.settings.party.name"));
            name.setResponder(v -> nameDraft = v);
            EditBox password = addRenderableWidget(new EditBox(font, right, y, COL, 20, Component.translatable("mc_voice_chat.settings.party.password")));
            password.setMaxLength(MAX_PASSWORD);
            password.setValue(passwordDraft);
            password.setHint(Component.translatable("mc_voice_chat.settings.party.password_hint"));
            password.setResponder(v -> passwordDraft = v);
            y += ROW;
            if (mine == null) {
                addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.party.create"),
                        b -> vc.createParty(nameDraft, passwordDraft)).bounds(left, y, COL, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.party.rename"),
                        b -> vc.renameParty(nameDraft)).bounds(left, y, COL, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.party.set_password"),
                        b -> vc.setPartyPassword(passwordDraft)).bounds(right, y, COL, 20).build());
            }
            y += ROW;
        }
        if (mine != null) {
            addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.party.leave"), b -> vc.leaveParty())
                    .bounds(left, y, COL, 20).build());
            if (owner) {
                addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.party.delete"),
                        b -> confirmDelete(this, mine)).bounds(right, y, COL, 20).build());
            }
            y += ROW;
        }
        int listTop = y + 4;
        parties = addRenderableWidget(new PartyList(minecraft, width, height - listTop - 36, listTop, this));
    }

    /** パーティーを消す前に確かめる。どちらを押しても元の画面に戻る */
    static void confirmDelete(Screen back, VoiceStatePayload.Party party) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                VoiceClient.get().deleteParty(party.id());
            }
            mc.gui.setScreen(back);
        }, Component.translatable("mc_voice_chat.settings.party.delete_title"),
                Component.translatable("mc_voice_chat.settings.party.delete_message", party.name())));
    }

    /** パーティータブの上に出す、いまのパーティー */
    private Component partyStatus() {
        VoiceStatePayload.Party mine = VoiceClient.get().myParty();
        if (mine == null) {
            return Component.translatable("mc_voice_chat.settings.party.none");
        }
        return Component.translatable("mc_voice_chat.settings.party.current", mine.name(), VoiceClient.nameOf(mine.owner()));
    }

    @Override
    public void tick() {
        super.tick();
        // サーバーから通話・パーティーの様子が届いたら、ボタンと一覧を作り直す
        if ((tab == Tab.CALL || tab == Tab.PARTY) && VoiceClient.get().stateVersion() != seenVersion) {
            rebuildWidgets();
        }
    }

    private CycleButton<String> deviceButton(int x, int y, String key, List<String> found, String current,
                                             java.util.function.Consumer<String> apply) {
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(found);
        if (!values.contains(current)) {
            values.add(current); // いまは繋がっていない機器も、設定に残っていれば選べるようにする
        }
        return CycleButton.<String>builder(
                        name -> name.isEmpty() ? Component.translatable("mc_voice_chat.settings.device_default") : Component.literal(name), current)
                .withValues(values)
                .create(x, y, COL, 20, Component.translatable(key), (b, v) -> {
                    apply.accept(v);
                    audioChanged = true;
                });
    }

    private static int row(int i) {
        return TOP + i * ROW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        switch (tab) {
            case GENERAL -> {
                extractMeter(g);
                extractStatus(g);
            }
            case PLAYERS -> {
                if (players != null && players.isEmpty()) {
                    g.centeredText(font, Component.translatable("mc_voice_chat.settings.no_players"), width / 2, TOP + 20, 0xFFA0A0A0);
                }
            }
            case CALL -> {
                g.centeredText(font, callStatus(), width / 2, TOP + 2, 0xFFFFFFFF);
                if (calls != null && calls.isEmpty()) {
                    g.centeredText(font, Component.translatable("mc_voice_chat.settings.call.no_players"),
                            width / 2, calls.getY() + 8, 0xFFA0A0A0);
                }
                extractOffline(g);
            }
            case PARTY -> {
                g.centeredText(font, partyStatus(), width / 2, TOP + 2, 0xFFFFFFFF);
                if (parties != null && parties.isEmpty()) {
                    g.centeredText(font, Component.translatable("mc_voice_chat.settings.party.no_parties"),
                            width / 2, parties.getY() + 8, 0xFFA0A0A0);
                }
                extractOffline(g);
            }
        }
    }

    /** マイクの入力の大きさを横棒で出す。音声検知のときは、しきい値の位置に白い線を引く */
    private void extractMeter(GuiGraphicsExtractor g) {
        VoiceClient vc = VoiceClient.get();
        ClientConfig config = vc.config();
        g.text(font, Component.translatable("mc_voice_chat.settings.mic_level"), meterX, meterY + 1, 0xFFE0E0E0, true);
        int barY = meterY + 12;
        int barRight = meterX + COL;
        g.fill(meterX, barY, barRight, barY + 6, 0xFF1E1E1E);
        double level = Math.max(0, Math.min(1, (vc.micLevelDb() - METER_MIN_DB) / -METER_MIN_DB));
        boolean loud = config.talkMode == TalkMode.VOICE ? vc.micLevelDb() >= config.thresholdDb : vc.isPushToTalkDown();
        int fill = meterX + (int) Math.round(level * COL);
        if (fill > meterX) {
            g.fill(meterX, barY, fill, barY + 6, loud ? 0xFF4CD964 : 0xFF6B7F6E);
        }
        if (config.talkMode == TalkMode.VOICE) {
            int tx = meterX + (int) Math.round((config.thresholdDb - METER_MIN_DB) / -METER_MIN_DB * COL);
            g.fill(tx, barY - 2, tx + 1, barY + 8, 0xFFFFFFFF);
        }
    }

    /** サーバーが非対応なら、通話・パーティーが使えないことを下に出す */
    private void extractOffline(GuiGraphicsExtractor g) {
        if (!VoiceClient.get().isServerReady()) {
            g.centeredText(font, Component.translatable("mc_voice_chat.settings.offline"), width / 2, height - 44, 0xFFA0A0A0);
        }
    }

    private void extractStatus(GuiGraphicsExtractor g) {
        VoiceClient vc = VoiceClient.get();
        int y = statusY;
        if (vc.micError() != null) {
            g.centeredText(font, Component.translatable("mc_voice_chat.settings.mic_error", vc.micError()), width / 2, y, 0xFFFF6B6B);
            y += 11;
        }
        if (vc.speakerError() != null) {
            g.centeredText(font, Component.translatable("mc_voice_chat.settings.speaker_error", vc.speakerError()), width / 2, y, 0xFFFF6B6B);
            y += 11;
        }
        if (!vc.isServerReady()) {
            g.centeredText(font, Component.translatable("mc_voice_chat.settings.offline"), width / 2, y, 0xFFA0A0A0);
        }
    }
}
