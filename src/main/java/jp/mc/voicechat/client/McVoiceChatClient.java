package jp.mc.voicechat.client;

import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.net.CallStatePayload;
import jp.mc.voicechat.net.HelloPayload;
import jp.mc.voicechat.net.PlayerAudioPayload;
import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.HandlerThread;

/**
 * クライアント側の入口。キー、画面端のマイクと通話・パーティーの表示、名前の上の表示、設定画面、
 * サーバーから届く通信の受け口を登録する。
 */
@Mod(value = McVoiceChat.MODID, dist = Dist.CLIENT)
public class McVoiceChatClient {
    public McVoiceChatClient(IEventBus modBus, ModContainer container) {
        VoiceClient.init(ClientConfig.load(FMLPaths.CONFIGDIR.get().resolve(McVoiceChat.MODID).resolve("client.json")));

        modBus.addListener(RegisterKeyMappingsEvent.class, VoiceKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, e ->
                e.registerAboveAll(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "mic"), new MicHudLayer()));
        modBus.addListener(RegisterGuiLayersEvent.class, e ->
                e.registerAboveAll(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "panel"), new VoicePanelLayer()));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(HelloPayload.TYPE, (p, ctx) -> VoiceClient.get().onHello(p));
            e.register(CallStatePayload.TYPE, (p, ctx) -> VoiceClient.get().onCallState(p));
            e.register(VoiceStatePayload.TYPE, (p, ctx) -> VoiceClient.get().onVoiceState(p));
            // 声は再生スレッドに渡すだけなので、メインスレッドを待たない
            e.register(PlayerAudioPayload.TYPE, HandlerThread.NETWORK, (p, ctx) -> VoiceClient.get().onAudio(p));
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> VoiceClient.get().tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> VoiceClient.get().onLoggingOut());
        NeoForge.EVENT_BUS.addListener(RenderNameTagEvent.CanRender.class, NameTagBanner::onCanRender);
        NeoForge.EVENT_BUS.addListener(RenderNameTagEvent.DoRender.class, NameTagBanner::onDoRender);

        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new VoiceSettingsScreen(parent, VoiceSettingsScreen.Tab.GENERAL));
    }
}
