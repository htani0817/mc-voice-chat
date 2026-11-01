package jp.mc.voicechat.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * マイク・スピーカーの機器の一覧と、名前を指定した開き方。
 */
public final class AudioDevices {
    /** マイク: 48kHz・16bit・モノラル */
    public static final AudioFormat MIC_FORMAT = new AudioFormat(OpusCodec.SAMPLE_RATE, 16, 1, true, false);
    /** スピーカー: 48kHz・16bit・ステレオ（左右に振るため） */
    public static final AudioFormat SPEAKER_FORMAT = new AudioFormat(OpusCodec.SAMPLE_RATE, 16, 2, true, false);

    private AudioDevices() {
    }

    public static List<String> microphones() {
        return names(new DataLine.Info(TargetDataLine.class, MIC_FORMAT));
    }

    public static List<String> speakers() {
        return names(new DataLine.Info(SourceDataLine.class, SPEAKER_FORMAT));
    }

    public static TargetDataLine openMicrophone(String name) throws LineUnavailableException {
        return (TargetDataLine) open(name, new DataLine.Info(TargetDataLine.class, MIC_FORMAT));
    }

    public static SourceDataLine openSpeaker(String name) throws LineUnavailableException {
        return (SourceDataLine) open(name, new DataLine.Info(SourceDataLine.class, SPEAKER_FORMAT));
    }

    private static List<String> names(DataLine.Info info) {
        List<String> list = new ArrayList<>();
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(info) && !list.contains(mi.getName())) {
                    list.add(mi.getName());
                }
            } catch (RuntimeException ignored) {
                // 一覧の途中で外された機器などは飛ばす
            }
        }
        return list;
    }

    /** 名前の機器が無いときは OS の既定を開く（USB マイクを抜いたときなど） */
    private static Line open(String name, DataLine.Info info) throws LineUnavailableException {
        if (name != null && !name.isEmpty()) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().equals(name)) {
                    Mixer mixer = AudioSystem.getMixer(mi);
                    if (mixer.isLineSupported(info)) {
                        return mixer.getLine(info);
                    }
                }
            }
        }
        return AudioSystem.getLine(info);
    }
}
