package jp.mc.voicechat.server;

import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.VoiceKind;
import jp.mc.voicechat.VoiceRange;
import jp.mc.voicechat.net.CallPayload;
import jp.mc.voicechat.net.CallStatePayload;
import jp.mc.voicechat.net.HelloPayload;
import jp.mc.voicechat.net.MicAudioPayload;
import jp.mc.voicechat.net.PartyPayload;
import jp.mc.voicechat.net.PlayerAudioPayload;
import jp.mc.voicechat.net.RangePayload;
import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 声の中継と、通話・パーティーVCの受け付け。
 * <p>
 * 声の届け先:
 * <ul>
 * <li>ふだんの話し方で話しているとき: 話した人の範囲の内側にいて同じ次元にいる人と、通話の相手（次元も距離も問わない）</li>
 * <li>プッシュトゥトークのキーを押しているとき: 同じパーティーの人（次元も距離も問わない）</li>
 * </ul>
 * 音声はネットワークスレッドで受けるので、プレイヤーの位置は tick の終わりに写しを取っておき、それを読む
 * （ネットワークスレッドからワールドを直接触らないため）。通話とパーティーの台帳はスレッドをまたいで読める。
 */
public final class VoiceServer {
    /** tick の終わりに取ったプレイヤーの位置 */
    private record Snapshot(ServerPlayer player, ResourceKey<Level> dimension, double x, double y, double z) {
    }

    /** ボイスチャット対応のクライアントで入っている人と、その人の範囲 */
    private static final Map<UUID, VoiceRange> RANGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final CallBook CALLS = new CallBook();
    private static final PartyBook PARTIES = new PartyBook();

    private VoiceServer() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, VoiceServer::onLogin);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, VoiceServer::onLogout);
        bus.addListener(ServerTickEvent.Post.class, VoiceServer::onTick);
        // パーティーと通話はメモリーにだけ持つので、サーバーを止めると消える
        bus.addListener(ServerStoppedEvent.class, e -> {
            RANGES.clear();
            SNAPSHOTS.clear();
            CALLS.clear();
            PARTIES.clear();
        });
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(HelloPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new HelloPayload(McVoiceChat.PROTOCOL));
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        boolean wasVoiceUser = RANGES.remove(id) != null;
        SNAPSHOTS.remove(id);
        CallBook.Ended ended = CALLS.hangup(id);
        if (ended != null) {
            UUID other = ended.other(id);
            sendCallState(player.level().getServer(), other);
            message(player.level().getServer(), other, Component.translatable("mc_voice_chat.call.peer_left", player.getName()));
        }
        PARTIES.leave(id);
        if (wasVoiceUser) {
            broadcastState(player.level().getServer());
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RANGES.containsKey(p.getUUID())) {
                SNAPSHOTS.put(p.getUUID(), new Snapshot(p, p.level().dimension(), p.getX(), p.getEyeY(), p.getZ()));
            }
        }
        // 1 秒ごとに、応答のない呼び出しを取り消す
        if (server.getTickCount() % 20 == 0) {
            for (CallBook.Ended e : CALLS.expire(System.nanoTime())) {
                sendCallState(server, e.caller());
                sendCallState(server, e.callee());
                message(server, e.caller(), Component.translatable("mc_voice_chat.call.no_answer", nameOf(server, e.callee())));
                message(server, e.callee(), Component.translatable("mc_voice_chat.call.missed", nameOf(server, e.caller())));
            }
        }
    }

    /** クライアントが範囲を知らせてきた（ログイン直後と切り替えたとき）。メインスレッドで呼ばれる */
    public static void onRange(RangePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            boolean joined = RANGES.put(player.getUUID(), payload.range()) == null;
            if (joined) {
                // 新しく入った人に全体の様子を渡し、ほかの人の「通話できる人」にも足す
                broadcastState(player.level().getServer());
            }
        }
    }

    // ---- 通話 ----

    /** 通話の操作。メインスレッドで呼ばれる */
    public static void onCall(CallPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer me) || !RANGES.containsKey(me.getUUID())) {
            return;
        }
        MinecraftServer server = me.level().getServer();
        UUID myId = me.getUUID();
        switch (payload.action()) {
            case CALL -> {
                ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
                if (target == null || !RANGES.containsKey(target.getUUID())) {
                    me.sendSystemMessage(Component.translatable("mc_voice_chat.call.unavailable"), true);
                    return;
                }
                switch (CALLS.call(myId, target.getUUID(), System.nanoTime())) {
                    case OK -> {
                        sendCallState(server, myId);
                        sendCallState(server, target.getUUID());
                        target.sendSystemMessage(Component.translatable("mc_voice_chat.call.incoming", me.getName()));
                    }
                    case BUSY_SELF -> me.sendSystemMessage(Component.translatable("mc_voice_chat.call.busy_self"), true);
                    case BUSY_PEER -> me.sendSystemMessage(Component.translatable("mc_voice_chat.call.busy_peer", target.getName()), true);
                    case SELF -> {
                    }
                }
            }
            case ACCEPT -> {
                UUID caller = CALLS.accept(myId);
                if (caller != null) {
                    sendCallState(server, myId);
                    sendCallState(server, caller);
                    broadcastState(server);
                }
            }
            case HANGUP -> {
                CallBook.Ended ended = CALLS.hangup(myId);
                if (ended == null) {
                    return;
                }
                UUID other = ended.other(myId);
                sendCallState(server, myId);
                sendCallState(server, other);
                String key;
                if (ended.active()) {
                    key = "mc_voice_chat.call.ended_by";
                } else if (ended.callee().equals(myId)) {
                    key = "mc_voice_chat.call.declined";
                } else {
                    key = "mc_voice_chat.call.cancelled";
                }
                message(server, other, Component.translatable(key, me.getName()));
                if (ended.active()) {
                    broadcastState(server);
                }
            }
        }
    }

    private static void sendCallState(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null || !RANGES.containsKey(id)) {
            return;
        }
        CallBook.View v = CALLS.view(id);
        PacketDistributor.sendToPlayer(p, new CallStatePayload(v.state(), v.peer() == null ? CallPayload.NOBODY : v.peer()));
    }

    // ---- パーティー ----

    /** パーティーVCの操作。メインスレッドで呼ばれる */
    public static void onParty(PartyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer me) || !RANGES.containsKey(me.getUUID())) {
            return;
        }
        MinecraftServer server = me.level().getServer();
        UUID myId = me.getUUID();
        List<UUID> deletedMembers = List.of();
        PartyBook.Result result = switch (payload.action()) {
            case CREATE -> PARTIES.create(myId, payload.name(), payload.password());
            case JOIN -> PARTIES.join(myId, payload.partyId(), payload.password());
            case LEAVE -> PARTIES.leave(myId);
            case RENAME -> PARTIES.rename(myId, payload.name());
            case SET_PASSWORD -> PARTIES.setPassword(myId, payload.password());
            case DELETE -> {
                deletedMembers = PARTIES.membersOf(payload.partyId());
                yield PARTIES.delete(myId, payload.partyId());
            }
        };
        if (result != PartyBook.Result.OK) {
            me.sendSystemMessage(Component.translatable("mc_voice_chat.party.error." + result.name().toLowerCase(Locale.ROOT),
                    PartyBook.MAX_NAME, PartyBook.MAX_PASSWORD), true);
            return;
        }
        switch (payload.action()) {
            case CREATE, JOIN, RENAME -> me.sendSystemMessage(
                    Component.translatable("mc_voice_chat.party.joined", PARTIES.partyNameOf(myId)), true);
            case LEAVE -> me.sendSystemMessage(Component.translatable("mc_voice_chat.party.left"), true);
            case SET_PASSWORD -> me.sendSystemMessage(Component.translatable(payload.password().isEmpty()
                    ? "mc_voice_chat.party.password_cleared" : "mc_voice_chat.party.password_set"), true);
            case DELETE -> {
                me.sendSystemMessage(Component.translatable("mc_voice_chat.party.deleted"), true);
                for (UUID m : deletedMembers) {
                    if (!m.equals(myId)) {
                        message(server, m, Component.translatable("mc_voice_chat.party.deleted_by", me.getName()));
                    }
                }
            }
        }
        broadcastState(server);
    }

    // ---- 全体の様子 ----

    /** ボイスチャット対応の全員に、全体の様子（通話できる人・通話中の人・パーティー）を送り直す */
    private static void broadcastState(MinecraftServer server) {
        List<VoiceStatePayload.Party> parties = PARTIES.list().stream()
                .map(p -> new VoiceStatePayload.Party(p.id(), p.name(), p.locked(), p.owner(), p.members()))
                .toList();
        VoiceStatePayload state = new VoiceStatePayload(List.copyOf(RANGES.keySet()), List.copyOf(CALLS.activePlayers()), parties);
        for (UUID id : RANGES.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                PacketDistributor.sendToPlayer(p, state);
            }
        }
    }

    private static void message(MinecraftServer server, UUID id, Component text) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            p.sendSystemMessage(text);
        }
    }

    private static Component nameOf(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p == null ? Component.literal("?") : p.getName();
    }

    // ---- 声の中継 ----

    /** マイクの音声が届いた。ネットワークスレッドで呼ばれる */
    public static void onMic(MicAudioPayload payload, IPayloadContext context) {
        UUID speakerId = context.player().getUUID();
        VoiceRange range = RANGES.get(speakerId);
        Snapshot speaker = SNAPSHOTS.get(speakerId);
        if (range == null || speaker == null || payload.opus().length == 0) {
            return;
        }
        // 聞く人ごとの届き方。複数の道で届く人には、通話 > パーティー > 近接 の順で 1 回だけ送る
        Map<UUID, VoiceKind> to = new HashMap<>();
        if (payload.open()) {
            double max = range.blocks();
            for (Snapshot listener : SNAPSHOTS.values()) {
                if (listener.player().getUUID().equals(speakerId) || !listener.dimension().equals(speaker.dimension())) {
                    continue;
                }
                double dx = listener.x() - speaker.x();
                double dy = listener.y() - speaker.y();
                double dz = listener.z() - speaker.z();
                if (dx * dx + dy * dy + dz * dz <= max * max) {
                    to.put(listener.player().getUUID(), VoiceKind.PROXIMITY);
                }
            }
            UUID peer = CALLS.activePeer(speakerId);
            if (peer != null) {
                to.put(peer, VoiceKind.CALL);
            }
        }
        if (payload.party()) {
            for (UUID mate : PARTIES.matesOf(speakerId)) {
                to.merge(mate, VoiceKind.PARTY, VoiceKind::stronger);
            }
        }
        if (to.isEmpty()) {
            return;
        }
        // 届き方ごとに 1 つ作って使い回す
        PlayerAudioPayload[] cache = new PlayerAudioPayload[VoiceKind.values().length];
        to.forEach((id, kind) -> {
            Snapshot listener = SNAPSHOTS.get(id);
            if (listener == null) {
                return;
            }
            PlayerAudioPayload out = cache[kind.ordinal()];
            if (out == null) {
                out = new PlayerAudioPayload(speakerId, payload.seq(), speaker.x(), speaker.y(), speaker.z(), range, kind, payload.opus());
                cache[kind.ordinal()] = out;
            }
            PacketDistributor.sendToPlayer(listener.player(), out);
        });
    }
}
