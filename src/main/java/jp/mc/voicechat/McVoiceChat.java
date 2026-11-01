package jp.mc.voicechat;

import com.mojang.logging.LogUtils;
import jp.mc.voicechat.net.CallPayload;
import jp.mc.voicechat.net.CallStatePayload;
import jp.mc.voicechat.net.HelloPayload;
import jp.mc.voicechat.net.MicAudioPayload;
import jp.mc.voicechat.net.PartyPayload;
import jp.mc.voicechat.net.PlayerAudioPayload;
import jp.mc.voicechat.net.RangePayload;
import jp.mc.voicechat.net.VoiceStatePayload;
import jp.mc.voicechat.server.VoiceServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * 近接ボイスチャット。サーバーとクライアントの両方で読み込まれる入口。
 * クライアント専用の処理は client/McVoiceChatClient に分けている。
 */
@Mod(McVoiceChat.MODID)
public class McVoiceChat {
    public static final String MODID = "mc_voice_chat";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 通信の形を変えたら上げる。サーバーとクライアントで違うと声をやり取りしない */
    public static final int PROTOCOL = 2;
    /** Opus 1 フレームの上限。24kbps・20ms なら 60 バイト前後なので十分に大きい */
    public static final int MAX_OPUS_BYTES = 1024;

    public McVoiceChat(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, McVoiceChat::registerPayloads);
        VoiceServer.register(NeoForge.EVENT_BUS);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: Mod を入れていないクライアントもサーバーに入れる（声が聞こえないだけ）
        PayloadRegistrar registrar = event.registrar(String.valueOf(PROTOCOL)).optional();
        registrar.playToClient(HelloPayload.TYPE, HelloPayload.STREAM_CODEC);
        registrar.playToClient(PlayerAudioPayload.TYPE, PlayerAudioPayload.STREAM_CODEC);
        registrar.playToClient(CallStatePayload.TYPE, CallStatePayload.STREAM_CODEC);
        registrar.playToClient(VoiceStatePayload.TYPE, VoiceStatePayload.STREAM_CODEC);
        registrar.playToServer(RangePayload.TYPE, RangePayload.STREAM_CODEC, VoiceServer::onRange);
        registrar.playToServer(CallPayload.TYPE, CallPayload.STREAM_CODEC, VoiceServer::onCall);
        registrar.playToServer(PartyPayload.TYPE, PartyPayload.STREAM_CODEC, VoiceServer::onParty);
        // 音声は 1 秒に 50 個届くので、メインスレッドを待たずにネットワークスレッドで中継する
        registrar.executesOn(HandlerThread.NETWORK)
                .playToServer(MicAudioPayload.TYPE, MicAudioPayload.STREAM_CODEC, VoiceServer::onMic);
    }
}
