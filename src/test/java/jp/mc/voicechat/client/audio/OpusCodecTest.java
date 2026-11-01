package jp.mc.voicechat.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpusCodecTest {
    @Test
    void 圧縮して戻すと同じくらいの大きさの音になる() {
        OpusCodec.Encoder enc = new OpusCodec.Encoder();
        OpusCodec.Decoder dec = new OpusCodec.Decoder();
        short[] in = new short[OpusCodec.FRAME_SAMPLES];
        short[] out = new short[OpusCodec.FRAME_SAMPLES];
        double inDb = 0;
        double outDb = 0;
        int phase = 0;
        // 立ち上がりを除くため 10 フレーム流して最後を比べる
        for (int frame = 0; frame < 10; frame++) {
            for (int i = 0; i < in.length; i++, phase++) {
                in[i] = (short) (8000 * Math.sin(2 * Math.PI * 440 * phase / OpusCodec.SAMPLE_RATE));
            }
            byte[] opus = enc.encode(in);
            assertTrue(opus.length > 0 && opus.length < 200, "1 フレームの大きさ: " + opus.length);
            assertEquals(OpusCodec.FRAME_SAMPLES, dec.decode(opus, out));
            inDb = VoiceActivity.levelDb(in, in.length);
            outDb = VoiceActivity.levelDb(out, out.length);
        }
        assertEquals(inDb, outDb, 3.0);
    }

    @Test
    void 欠けたフレームも補間して1フレーム分を返す() {
        OpusCodec.Encoder enc = new OpusCodec.Encoder();
        OpusCodec.Decoder dec = new OpusCodec.Decoder();
        short[] pcm = new short[OpusCodec.FRAME_SAMPLES];
        dec.decode(enc.encode(pcm), pcm);
        assertEquals(OpusCodec.FRAME_SAMPLES, dec.decode(JitterBuffer.LOST, pcm));
    }
}
