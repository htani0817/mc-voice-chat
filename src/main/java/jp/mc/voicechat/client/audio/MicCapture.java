package jp.mc.voicechat.client.audio;

import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.client.VoiceClient;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * 録音スレッド。20ms ずつマイクを読み、送るかどうかを決めて Opus にし、VoiceClient に渡す。
 */
public final class MicCapture implements Runnable {
    /** 音声検知の余韻（0.5 秒） */
    private static final int HANG_FRAMES = 25;

    private final VoiceClient client;
    private final String device;
    private volatile boolean running = true;
    private final Thread thread;

    public MicCapture(VoiceClient client, String device) {
        this.client = client;
        this.device = device;
        this.thread = new Thread(this, "mc-voice-chat mic");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    @Override
    public void run() {
        TargetDataLine line;
        try {
            line = AudioDevices.openMicrophone(device);
            // 内部バッファは 4 フレーム分（80ms）。大きいと遅れる
            line.open(AudioDevices.MIC_FORMAT, OpusCodec.FRAME_SAMPLES * 2 * 4);
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            McVoiceChat.LOGGER.warn("[mc-voice-chat] マイクを開けません: {}", e.toString());
            client.setMicError(e.getMessage() == null ? e.toString() : e.getMessage());
            return;
        }
        client.setMicError(null);
        line.start();
        byte[] raw = new byte[OpusCodec.FRAME_SAMPLES * 2];
        short[] pcm = new short[OpusCodec.FRAME_SAMPLES];
        OpusCodec.Encoder encoder = new OpusCodec.Encoder();
        VoiceActivity activity = new VoiceActivity(HANG_FRAMES);
        try {
            while (running) {
                int read = 0;
                while (read < raw.length && running) {
                    int n = line.read(raw, read, raw.length - read);
                    if (n <= 0) {
                        break;
                    }
                    read += n;
                }
                if (!running || read < raw.length) {
                    continue;
                }
                double gain = client.config().micGain;
                for (int i = 0; i < pcm.length; i++) {
                    // リトルエンディアンの 16bit を読み、増幅して丸める
                    int s = (short) ((raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8));
                    s = (int) Math.round(s * gain);
                    pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
                }
                double level = VoiceActivity.levelDb(pcm, pcm.length);
                boolean ptt = client.isPushToTalkDown();
                // open: ふだんの話し方（近くの人と通話相手へ）。party: パーティーへはキーを押している間だけ
                boolean open = switch (client.config().talkMode) {
                    case VOICE -> activity.update(level, client.config().thresholdDb);
                    case PTT -> ptt;
                };
                boolean party = ptt && client.isInParty();
                client.onMicFrame(level, open, party, open || party ? encoder.encode(pcm) : null);
            }
        } finally {
            line.stop();
            line.close();
            client.onMicFrame(VoiceActivity.SILENCE_DB, false, false, null);
        }
    }
}
