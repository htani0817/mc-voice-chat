package jp.mc.voicechat;

/**
 * 声の届く範囲。話す人が選び、サーバーはこの距離の内側にいる人にだけ声を送る。
 */
public enum VoiceRange {
    WHISPER("whisper", 5),
    NORMAL("normal", 10),
    SHOUT("shout", 20),
    SUPERSHOUT("supershout", 30);

    private final String label;
    private final int blocks;

    VoiceRange(String label, int blocks) {
        this.label = label;
        this.blocks = blocks;
    }

    public String label() {
        return label;
    }

    public int blocks() {
        return blocks;
    }

    /** 範囲切替キーを押したときの次の範囲（supershout の次は whisper に戻る） */
    public VoiceRange next() {
        VoiceRange[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 通信で受け取った番号から戻す。おかしな値は normal にする */
    public static VoiceRange byId(int id) {
        VoiceRange[] all = values();
        return id >= 0 && id < all.length ? all[id] : NORMAL;
    }
}
