package jp.mc.voicechat.client.audio;

/**
 * 音声検知。マイクの音量がしきい値を超えたら送り始め、下回っても少しのあいだ（余韻）送り続ける。
 * 余韻が無いと、語尾や息継ぎのたびに声が途切れる。
 */
public final class VoiceActivity {
    /** 何も聞こえないときの音量（dB） */
    public static final double SILENCE_DB = -100;

    private final int hangFrames;
    private int remaining;

    /** @param hangFrames 余韻のフレーム数（20ms 単位） */
    public VoiceActivity(int hangFrames) {
        this.hangFrames = hangFrames;
    }

    /** 1 フレームぶん進めて、このフレームを送るかを返す */
    public boolean update(double levelDb, double thresholdDb) {
        if (levelDb >= thresholdDb) {
            remaining = hangFrames;
        } else if (remaining > 0) {
            remaining--;
        }
        return remaining > 0;
    }

    public void reset() {
        remaining = 0;
    }

    /** 1 フレームの音量（RMS を dBFS にしたもの）。最大で 0、無音で SILENCE_DB */
    public static double levelDb(short[] pcm, int samples) {
        if (samples <= 0) {
            return SILENCE_DB;
        }
        double sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        double rms = Math.sqrt(sum / samples);
        if (rms < 1) {
            return SILENCE_DB;
        }
        return Math.max(SILENCE_DB, 20 * Math.log10(rms / Short.MAX_VALUE));
    }
}
