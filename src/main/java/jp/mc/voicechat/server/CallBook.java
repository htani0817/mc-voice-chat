package jp.mc.voicechat.server;

import jp.mc.voicechat.CallState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通話の台帳。1 人が持てる通話は 1 本だけ（発信中・着信中・通話中のどれか）。
 * <p>
 * 声の中継（ネットワークスレッド）からも読むので、すべて synchronized にしている。
 * 時刻は引数で受け取る（テストで時間を進めるため）。
 */
public final class CallBook {
    /** 応答がないまま、この時間が過ぎた呼び出しは取り消す（30 秒） */
    public static final long RING_NANOS = 30_000_000_000L;

    public enum Result { OK, SELF, BUSY_SELF, BUSY_PEER }

    /** 終わった通話。active が false なら応答される前に終わった */
    public record Ended(UUID caller, UUID callee, boolean active) {
        public UUID other(UUID who) {
            return who.equals(caller) ? callee : caller;
        }
    }

    /** ある人から見た通話の状態と相手 */
    public record View(CallState state, UUID peer) {
        public static final View NONE = new View(CallState.NONE, null);
    }

    private static final class Call {
        final UUID caller;
        final UUID callee;
        final long since;
        boolean active;

        Call(UUID caller, UUID callee, long since) {
            this.caller = caller;
            this.callee = callee;
            this.since = since;
        }
    }

    private final Map<UUID, Call> byPlayer = new HashMap<>();

    public synchronized Result call(UUID caller, UUID callee, long now) {
        if (caller.equals(callee)) {
            return Result.SELF;
        }
        if (byPlayer.containsKey(caller)) {
            return Result.BUSY_SELF;
        }
        if (byPlayer.containsKey(callee)) {
            return Result.BUSY_PEER;
        }
        Call c = new Call(caller, callee, now);
        byPlayer.put(caller, c);
        byPlayer.put(callee, c);
        return Result.OK;
    }

    /** 着信に応答する。応答できたら発信した人を返す（着信していなければ null） */
    public synchronized UUID accept(UUID callee) {
        Call c = byPlayer.get(callee);
        if (c == null || c.active || !c.callee.equals(callee)) {
            return null;
        }
        c.active = true;
        return c.caller;
    }

    /** 切る（発信の取り消し・着信の拒否・通話の切断のどれも）。通話していなければ null */
    public synchronized Ended hangup(UUID who) {
        Call c = byPlayer.get(who);
        if (c == null) {
            return null;
        }
        byPlayer.remove(c.caller);
        byPlayer.remove(c.callee);
        return new Ended(c.caller, c.callee, c.active);
    }

    /** 応答がないまま時間切れになった呼び出しを取り消して返す */
    public synchronized List<Ended> expire(long now) {
        List<Ended> out = new ArrayList<>();
        for (Call c : new HashSet<>(byPlayer.values())) {
            if (!c.active && now - c.since >= RING_NANOS) {
                byPlayer.remove(c.caller);
                byPlayer.remove(c.callee);
                out.add(new Ended(c.caller, c.callee, false));
            }
        }
        return out;
    }

    /** 通話中（応答済み）の相手。いなければ null */
    public synchronized UUID activePeer(UUID who) {
        Call c = byPlayer.get(who);
        if (c == null || !c.active) {
            return null;
        }
        return c.caller.equals(who) ? c.callee : c.caller;
    }

    public synchronized View view(UUID who) {
        Call c = byPlayer.get(who);
        if (c == null) {
            return View.NONE;
        }
        boolean isCaller = c.caller.equals(who);
        UUID peer = isCaller ? c.callee : c.caller;
        if (c.active) {
            return new View(CallState.ACTIVE, peer);
        }
        return new View(isCaller ? CallState.OUTGOING : CallState.INCOMING, peer);
    }

    /** 通話中（応答済み）の人。名前の上に「通話中」を出すのに使う */
    public synchronized Set<UUID> activePlayers() {
        Set<UUID> out = new HashSet<>();
        byPlayer.forEach((id, c) -> {
            if (c.active) {
                out.add(id);
            }
        });
        return out;
    }

    public synchronized void clear() {
        byPlayer.clear();
    }
}
