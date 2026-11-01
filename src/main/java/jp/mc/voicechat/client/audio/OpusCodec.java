package jp.mc.voicechat.client.audio;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusSignal;
import jp.mc.voicechat.McVoiceChat;

import java.util.Arrays;

/**
 * Opus の圧縮・展開（Concentus を使う）。48kHz・モノラル・20ms ごとに扱う。
 */
public final class OpusCodec {
    public static final int SAMPLE_RATE = 48000;
    /** 1 フレーム（20ms）のサンプル数 */
    public static final int FRAME_SAMPLES = SAMPLE_RATE / 50;
    /** 声には 24kbps で十分 */
    private static final int BITRATE = 24000;

    private OpusCodec() {
    }

    public static final class Encoder {
        private final OpusEncoder encoder;
        private final byte[] buffer = new byte[McVoiceChat.MAX_OPUS_BYTES];

        public Encoder() {
            try {
                encoder = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            } catch (OpusException e) {
                throw new IllegalStateException("Opus のエンコーダを作れません", e);
            }
            encoder.setBitrate(BITRATE);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            encoder.setComplexity(5);
        }

        public byte[] encode(short[] pcm) {
            try {
                int n = encoder.encode(pcm, 0, FRAME_SAMPLES, buffer, 0, buffer.length);
                return Arrays.copyOf(buffer, n);
            } catch (OpusException e) {
                return new byte[0];
            }
        }
    }

    public static final class Decoder {
        private final OpusDecoder decoder;

        public Decoder() {
            try {
                decoder = new OpusDecoder(SAMPLE_RATE, 1);
            } catch (OpusException e) {
                throw new IllegalStateException("Opus のデコーダを作れません", e);
            }
        }

        /**
         * 1 フレームを展開して out に入れる。opus が空（欠け）なら前後から補間した音を作る。
         *
         * @return 入れたサンプル数（失敗したら 0）
         */
        public int decode(byte[] opus, short[] out) {
            try {
                if (opus == null || opus.length == 0) {
                    return decoder.decode(null, 0, 0, out, 0, FRAME_SAMPLES, false);
                }
                return decoder.decode(opus, 0, opus.length, out, 0, FRAME_SAMPLES, false);
            } catch (OpusException | RuntimeException e) {
                return 0;
            }
        }
    }
}
