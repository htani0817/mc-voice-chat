package jp.mc.voicechat;

/**
 * 1 人のプレイヤーから見た通話の状態。
 */
public enum CallState {
    /** 通話していない */
    NONE,
    /** 自分から発信して、相手の応答を待っている */
    OUTGOING,
    /** 相手から着信している */
    INCOMING,
    /** 通話中 */
    ACTIVE;

    public static CallState byId(int id) {
        CallState[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}
