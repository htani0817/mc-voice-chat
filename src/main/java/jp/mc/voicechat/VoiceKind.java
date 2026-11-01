package jp.mc.voicechat;

/**
 * 声の届き方。サーバーが聞く人ごとに決め、聞く側はこれで距離による減衰と左右の振りを付けるかを決める。
 * 同じ声が複数の道で届くときは、後ろのもの（ordinal が大きいもの）を優先する。
 */
public enum VoiceKind {
    /** 近くにいる（距離で小さくし、向きで左右に振る） */
    PROXIMITY,
    /** パーティーVC（距離に関係なくそのままの大きさ） */
    PARTY,
    /** 通話（距離に関係なくそのままの大きさ） */
    CALL;

    /** 通信で受け取った番号から戻す。おかしな値は近接にする */
    public static VoiceKind byId(int id) {
        VoiceKind[] all = values();
        return id >= 0 && id < all.length ? all[id] : PROXIMITY;
    }

    /** 2 つのうち優先するほう */
    public static VoiceKind stronger(VoiceKind a, VoiceKind b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
