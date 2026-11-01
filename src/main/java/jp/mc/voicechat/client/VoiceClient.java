package jp.mc.voicechat.client;

import jp.mc.voicechat.CallState;
import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.VoiceKind;
import jp.mc.voicechat.VoiceRange;
import jp.mc.voicechat.client.audio.AudioMixer;
import jp.mc.voicechat.client.audio.MicCapture;
import jp.mc.voicechat.client.audio.VoiceActivity;
import jp.mc.voicechat.net.CallPayload;
import jp.mc.voicechat.net.CallStatePayload;
import jp.mc.voicechat.net.HelloPayload;
import jp.mc.voicechat.net.MicAudioPayload;
import jp.mc.voicechat.net.PartyPayload;
import jp.mc.voicechat.net.PlayerAudioPayload;
import jp.mc.voicechat.net.RangePayload;
import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クライアント側の状態のまとめ役。サーバーとのやり取り、ミュート・範囲、通話とパーティーVC、録音と再生のスレッドの起動停止を受け持つ。
 * <p>
 * 録音・再生はサーバーが対応しているとき（HelloPayload が届いたあと）か、設定画面を開いているあいだだけ動かす。
 */
public final class VoiceClient {
    /** この時間より最近に声が届いていたら「話し中」 */
    private static final long SPEAKING_NANOS = 300_000_000L;
    /** 呼び出し音を鳴らす間隔（tick） */
    private static final int RING_INTERVAL = 40;

    private static VoiceClient instance;

    private final ClientConfig config;
    /** サーバーがボイスチャットに対応していて、やり取りできる状態 */
    private volatile boolean serverReady;
    private volatile boolean pushToTalkDown;
    /** 自分の声を送っている（マイクのアイコンを緑にする） */
    private volatile boolean transmitting;
    /** 自分の声をパーティーに送っている */
    private volatile boolean partyTransmitting;
    private volatile double micLevelDb = VoiceActivity.SILENCE_DB;
    private volatile String micError;
    private volatile String speakerError;
    private volatile boolean micTest;
    // 聞き手（自分）の位置。tick ごとに写して、再生スレッドから読む
    private volatile double listenerX, listenerY, listenerZ;
    private volatile float listenerYaw;
    private final Map<UUID, Long> lastSpoke = new ConcurrentHashMap<>();

    // 通話（自分の）
    private volatile CallState callState = CallState.NONE;
    private volatile UUID callPeer;
    private long callStartMillis;
    private int ringTicks;

    // サーバー全体の様子。届くたびに丸ごと差し替える
    private volatile VoiceStatePayload world = VoiceStatePayload.EMPTY;
    private volatile VoiceStatePayload.Party myParty;
    private volatile Set<UUID> voiceUsers = Set.of();
    private volatile Set<UUID> inCall = Set.of();
    private volatile Map<UUID, String> partyNameByMember = Map.of();
    /** 通話かパーティーの様子が変わるたびに増やす。開いている画面が作り直すかの目印 */
    private volatile int stateVersion;

    private boolean previewing;
    private MicCapture mic;
    private volatile AudioMixer mixer;
    private int sendSeq;
    private int loopbackSeq;

    private VoiceClient(ClientConfig config) {
        this.config = config;
    }

    static void init(ClientConfig config) {
        instance = new VoiceClient(config);
    }

    public static VoiceClient get() {
        return instance;
    }

    public ClientConfig config() {
        return config;
    }

    // ---- サーバーとのやり取り ----

    void onHello(HelloPayload payload) {
        if (payload.protocol() != McVoiceChat.PROTOCOL) {
            McVoiceChat.LOGGER.warn("[mc-voice-chat] サーバーと版が合わないのでボイスチャットを使いません（サーバー {} / 手元 {}）",
                    payload.protocol(), McVoiceChat.PROTOCOL);
            overlay(Component.translatable("mc_voice_chat.message.version_mismatch"));
            return;
        }
        serverReady = true;
        ClientPacketDistributor.sendToServer(new RangePayload(config.range));
        updateAudio();
    }

    /** ネットワークスレッドから呼ばれる */
    void onAudio(PlayerAudioPayload p) {
        AudioMixer m = mixer;
        if (m != null) {
            m.push(p.speaker(), p.seq(), p.x(), p.y(), p.z(), p.range(), p.kind(), p.opus());
        }
    }

    void onCallState(CallStatePayload p) {
        CallState old = callState;
        callPeer = p.state() == CallState.NONE ? null : p.peer();
        callState = p.state();
        if (p.state() == CallState.ACTIVE && old != CallState.ACTIVE) {
            callStartMillis = System.currentTimeMillis();
        }
        ringTicks = 0;
        stateVersion++;
    }

    void onVoiceState(VoiceStatePayload p) {
        UUID self = selfId();
        VoiceStatePayload.Party mine = null;
        Map<UUID, String> names = new HashMap<>();
        for (VoiceStatePayload.Party party : p.parties()) {
            for (UUID m : party.members()) {
                names.put(m, party.name());
                if (m.equals(self)) {
                    mine = party;
                }
            }
        }
        world = p;
        myParty = mine;
        voiceUsers = new HashSet<>(p.voiceUsers());
        inCall = new HashSet<>(p.inCall());
        partyNameByMember = names;
        stateVersion++;
    }

    void onLoggingOut() {
        serverReady = false;
        lastSpoke.clear();
        callState = CallState.NONE;
        callPeer = null;
        world = VoiceStatePayload.EMPTY;
        myParty = null;
        voiceUsers = Set.of();
        inCall = Set.of();
        partyNameByMember = Map.of();
        stateVersion++;
        updateAudio();
    }

    public boolean isServerReady() {
        return serverReady;
    }

    // ---- 毎 tick ----

    void tick(Minecraft mc) {
        if (mc.player != null) {
            listenerX = mc.player.getX();
            listenerY = mc.player.getEyeY();
            listenerZ = mc.player.getZ();
            listenerYaw = mc.player.getYRot();
        }
        while (VoiceKeys.RANGE.consumeClick()) {
            cycleRange();
        }
        while (VoiceKeys.MUTE.consumeClick()) {
            setMuted(!config.muted);
        }
        while (VoiceKeys.CALL.consumeClick()) {
            // 着信中なら出る。発信中・通話中なら切る
            switch (callState) {
                case INCOMING -> acceptCall();
                case OUTGOING, ACTIVE -> hangUp();
                case NONE -> {
                }
            }
        }
        while (VoiceKeys.SETTINGS.consumeClick()) {
            // 着信中は「通話」タブから開く
            mc.gui.setScreen(new VoiceSettingsScreen(null,
                    callState == CallState.INCOMING ? VoiceSettingsScreen.Tab.CALL : VoiceSettingsScreen.Tab.GENERAL));
        }
        pushToTalkDown = VoiceKeys.PUSH_TO_TALK.isDown();
        ring(mc);
    }

    /** 着信中はベル、発信中は小さめの呼び出し音を 2 秒ごとに鳴らす */
    private void ring(Minecraft mc) {
        CallState s = callState;
        if (s != CallState.INCOMING && s != CallState.OUTGOING) {
            return;
        }
        if (ringTicks++ % RING_INTERVAL == 0) {
            mc.getSoundManager().play(s == CallState.INCOMING
                    ? SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.8f)
                    : SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 0.35f));
        }
    }

    // ---- 操作（キー・設定画面から） ----

    public void cycleRange() {
        setRange(config.range.next());
    }

    public void setRange(VoiceRange range) {
        config.range = range;
        config.save();
        if (serverReady) {
            ClientPacketDistributor.sendToServer(new RangePayload(range));
        }
        overlay(Component.translatable("mc_voice_chat.message.range", range.label(), range.blocks()));
    }

    public void setMuted(boolean muted) {
        config.muted = muted;
        config.save();
        overlay(Component.translatable(muted ? "mc_voice_chat.message.muted" : "mc_voice_chat.message.unmuted"));
    }

    public void startCall(UUID target) {
        sendCall(CallPayload.Action.CALL, target);
    }

    public void acceptCall() {
        sendCall(CallPayload.Action.ACCEPT, CallPayload.NOBODY);
    }

    public void hangUp() {
        sendCall(CallPayload.Action.HANGUP, CallPayload.NOBODY);
    }

    private void sendCall(CallPayload.Action action, UUID target) {
        if (serverReady) {
            ClientPacketDistributor.sendToServer(new CallPayload(action, target));
        }
    }

    public void createParty(String name, String password) {
        sendParty(PartyPayload.Action.CREATE, 0, name, password);
    }

    public void joinParty(int id, String password) {
        sendParty(PartyPayload.Action.JOIN, id, "", password);
    }

    public void leaveParty() {
        sendParty(PartyPayload.Action.LEAVE, 0, "", "");
    }

    public void renameParty(String name) {
        sendParty(PartyPayload.Action.RENAME, 0, name, "");
    }

    public void setPartyPassword(String password) {
        sendParty(PartyPayload.Action.SET_PASSWORD, 0, "", password);
    }

    public void deleteParty(int id) {
        sendParty(PartyPayload.Action.DELETE, id, "", "");
    }

    private void sendParty(PartyPayload.Action action, int id, String name, String password) {
        if (serverReady) {
            ClientPacketDistributor.sendToServer(new PartyPayload(action, id, name, password));
        }
    }

    public void setMicTest(boolean on) {
        micTest = on;
    }

    public boolean isMicTest() {
        return micTest;
    }

    /** 設定画面を開いているあいだは、サーバーが非対応でもマイクの音量メーターとマイクテストを動かす */
    void setPreview(boolean on) {
        previewing = on;
        if (!on) {
            micTest = false;
        }
        updateAudio();
    }

    /** 機器を変えたときに録音・再生を開き直す */
    public void restartAudio() {
        stopAudio();
        updateAudio();
    }

    private void updateAudio() {
        boolean want = serverReady || previewing;
        if (want && mic == null) {
            AudioMixer m = new AudioMixer(this, config.speakerDevice);
            m.start();
            mixer = m;
            mic = new MicCapture(this, config.micDevice);
            mic.start();
        } else if (!want && mic != null) {
            stopAudio();
        }
    }

    private void stopAudio() {
        if (mic != null) {
            mic.stop();
            mic = null;
        }
        AudioMixer m = mixer;
        mixer = null;
        if (m != null) {
            m.stop();
        }
        transmitting = false;
        partyTransmitting = false;
    }

    // ---- 通話とパーティーの様子（画面・名前の上の表示から） ----

    public CallState callState() {
        return callState;
    }

    public UUID callPeer() {
        return callPeer;
    }

    /** 通話が始まってからの秒数 */
    public long callSeconds() {
        return Math.max(0, (System.currentTimeMillis() - callStartMillis) / 1000);
    }

    public VoiceStatePayload.Party myParty() {
        return myParty;
    }

    public boolean isInParty() {
        return myParty != null;
    }

    public List<VoiceStatePayload.Party> parties() {
        return world.parties();
    }

    /** ボイスチャットの Mod を入れて入っている人（通話をかけられる人） */
    public boolean isVoiceUser(UUID id) {
        return voiceUsers.contains(id);
    }

    public boolean isInCall(UUID id) {
        return inCall.contains(id);
    }

    public int stateVersion() {
        return stateVersion;
    }

    /** 名前の上に重ねる行（下から順）。パーティー名、その上に「通話中」 */
    public List<Component> bannerLines(UUID id) {
        String party = partyNameByMember.get(id);
        boolean calling = inCall.contains(id);
        if (party == null && !calling) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(2);
        if (party != null) {
            out.add(Component.literal(party).withStyle(ChatFormatting.AQUA));
        }
        if (calling) {
            out.add(Component.translatable("mc_voice_chat.banner.in_call").withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    public UUID selfId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    /** タブ一覧からプレイヤー名を引く。見つからなければ UUID の頭 8 文字 */
    public static String nameOf(UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getProfile().name() : id.toString().substring(0, 8);
    }

    // ---- 録音スレッドから ----

    /**
     * 20ms ごとのマイクの結果。open か party が true なら opus を送る（ミュート中は送らない）。
     * マイクテスト中はミュートに関係なく自分に返す。
     *
     * @param open  ふだんの話し方で声が出ている（近くの人と通話相手へ）
     * @param party プッシュトゥトークのキーを押していて、パーティーに入っている（パーティーへ）
     */
    public void onMicFrame(double levelDb, boolean open, boolean party, byte[] opus) {
        micLevelDb = levelDb;
        boolean send = (open || party) && opus != null && opus.length > 0;
        boolean live = send && !config.muted && serverReady;
        transmitting = live;
        partyTransmitting = live && party;
        if (live) {
            try {
                ClientPacketDistributor.sendToServer(new MicAudioPayload(sendSeq++, open, party, opus));
            } catch (RuntimeException e) {
                // 切断の直後などで接続が無い
            }
        }
        AudioMixer m = mixer;
        if (send && micTest && m != null) {
            m.push(AudioMixer.LOOPBACK, loopbackSeq++, 0, 0, 0, VoiceRange.NORMAL, VoiceKind.PROXIMITY, opus);
        }
    }

    public void setMicError(String error) {
        micError = error;
    }

    public void setSpeakerError(String error) {
        speakerError = error;
    }

    public String micError() {
        return micError;
    }

    public String speakerError() {
        return speakerError;
    }

    public double micLevelDb() {
        return micLevelDb;
    }

    public boolean isTransmitting() {
        return transmitting;
    }

    public boolean isPartyTransmitting() {
        return partyTransmitting;
    }

    public boolean isPushToTalkDown() {
        return pushToTalkDown;
    }

    // ---- 再生スレッドから ----

    public void markSpeaking(UUID id) {
        lastSpoke.put(id, System.nanoTime());
    }

    public boolean isSpeaking(UUID id) {
        Long t = lastSpoke.get(id);
        return t != null && System.nanoTime() - t < SPEAKING_NANOS;
    }

    public double listenerX() {
        return listenerX;
    }

    public double listenerY() {
        return listenerY;
    }

    public double listenerZ() {
        return listenerZ;
    }

    public float listenerYaw() {
        return listenerYaw;
    }

    private static void overlay(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(message);
        }
    }
}
