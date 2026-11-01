package jp.mc.voicechat.client.audio;

import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.VoiceKind;
import jp.mc.voicechat.VoiceRange;
import jp.mc.voicechat.client.ClientConfig;
import jp.mc.voicechat.client.VoiceClient;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 再生スレッド。話者ごとの声を 20ms ずつ取り出して、大きさと左右を付けて混ぜ、1 本のスピーカー出力に書く。
 * スピーカーへの書き込みが詰まるのを待つことで 20ms の歩調を取っている。
 */
public final class AudioMixer implements Runnable {
    /** マイクテストで自分の声を返すときの話者 ID */
    public static final UUID LOOPBACK = new UUID(0, 0);
    /** これだけ何も届かない話者は片付ける */
    private static final long FORGET_NANOS = 2_000_000_000L;

    private static final class Speaker {
        final JitterBuffer buffer = new JitterBuffer();
        final OpusCodec.Decoder decoder = new OpusCodec.Decoder();
        volatile double x, y, z;
        volatile VoiceRange range = VoiceRange.NORMAL;
        volatile VoiceKind kind = VoiceKind.PROXIMITY;
        volatile long lastPacket = System.nanoTime();
    }

    private final VoiceClient client;
    private final String device;
    private final Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private final Thread thread;

    public AudioMixer(VoiceClient client, String device) {
        this.client = client;
        this.device = device;
        this.thread = new Thread(this, "mc-voice-chat speaker");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    /** 誰かの声が届いた（ネットワークスレッドから呼ばれる） */
    public void push(UUID id, int seq, double x, double y, double z, VoiceRange range, VoiceKind kind, byte[] opus) {
        Speaker s = speakers.computeIfAbsent(id, k -> new Speaker());
        s.x = x;
        s.y = y;
        s.z = z;
        s.range = range;
        s.kind = kind;
        s.lastPacket = System.nanoTime();
        s.buffer.push(seq, opus);
    }

    @Override
    public void run() {
        SourceDataLine line;
        try {
            line = AudioDevices.openSpeaker(device);
            // 4 フレーム分（80ms）。小さすぎると途切れ、大きいと遅れる
            line.open(AudioDevices.SPEAKER_FORMAT, OpusCodec.FRAME_SAMPLES * 4 * 4);
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            McVoiceChat.LOGGER.warn("[mc-voice-chat] スピーカーを開けません: {}", e.toString());
            client.setSpeakerError(e.getMessage() == null ? e.toString() : e.getMessage());
            return;
        }
        client.setSpeakerError(null);
        line.start();
        int n = OpusCodec.FRAME_SAMPLES;
        float[] mixL = new float[n];
        float[] mixR = new float[n];
        short[] pcm = new short[n];
        byte[] out = new byte[n * 4];
        try {
            while (running) {
                Arrays.fill(mixL, 0f);
                Arrays.fill(mixR, 0f);
                mixSpeakers(mixL, mixR, pcm);
                for (int i = 0; i < n; i++) {
                    int l = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixL[i])));
                    int r = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixR[i])));
                    out[i * 4] = (byte) l;
                    out[i * 4 + 1] = (byte) (l >> 8);
                    out[i * 4 + 2] = (byte) r;
                    out[i * 4 + 3] = (byte) (r >> 8);
                }
                line.write(out, 0, out.length);
            }
        } finally {
            line.stop();
            line.close();
        }
    }

    private void mixSpeakers(float[] mixL, float[] mixR, short[] pcm) {
        ClientConfig config = client.config();
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, Speaker>> it = speakers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Speaker> e = it.next();
            UUID id = e.getKey();
            Speaker s = e.getValue();
            if (now - s.lastPacket > FORGET_NANOS && s.buffer.isIdle()) {
                it.remove();
                continue;
            }
            byte[] frame = s.buffer.poll();
            if (frame == null) {
                continue;
            }
            int got = s.decoder.decode(frame, pcm);
            if (got <= 0) {
                continue;
            }
            float gainL;
            float gainR;
            if (id.equals(LOOPBACK)) {
                gainL = gainR = (float) config.masterVolume;
            } else {
                if (frame != JitterBuffer.LOST) {
                    client.markSpeaking(id);
                }
                if (config.isPlayerMuted(id)) {
                    continue;
                }
                if (s.kind != VoiceKind.PROXIMITY) {
                    // 通話・パーティーは距離と向きを使わず、そのままの大きさで両耳に
                    gainL = gainR = (float) (config.masterVolume * config.playerVolume(id));
                    mix(mixL, mixR, pcm, got, gainL, gainR);
                    continue;
                }
                double dx = s.x - client.listenerX();
                double dy = s.y - client.listenerY();
                double dz = s.z - client.listenerZ();
                float volume = (float) (config.masterVolume * config.playerVolume(id))
                        * SpatialGain.distanceGain(Math.sqrt(dx * dx + dy * dy + dz * dz), s.range.blocks());
                float pan = SpatialGain.pan(client.listenerX(), client.listenerZ(), client.listenerYaw(), s.x, s.z);
                gainL = volume * SpatialGain.left(pan);
                gainR = volume * SpatialGain.right(pan);
            }
            mix(mixL, mixR, pcm, got, gainL, gainR);
        }
    }

    private static void mix(float[] mixL, float[] mixR, short[] pcm, int got, float gainL, float gainR) {
        if (gainL <= 0 && gainR <= 0) {
            return;
        }
        for (int i = 0; i < got; i++) {
            mixL[i] += pcm[i] * gainL;
            mixR[i] += pcm[i] * gainR;
        }
    }
}
