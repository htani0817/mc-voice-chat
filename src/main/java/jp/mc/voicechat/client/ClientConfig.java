package jp.mc.voicechat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.VoiceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * config/mc_voice_chat/client.json の中身。設定画面とキー操作で変わり、変わるたびに書き戻す。
 * 音声のスレッドからも読むので、プレイヤーごとの値は ConcurrentHashMap で持つ。
 */
public final class ClientConfig {
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public enum TalkMode { VOICE, PTT }

    public enum IconCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** 話し方。VOICE は音声検知、PTT はキーを押している間だけ */
    public TalkMode talkMode = TalkMode.VOICE;
    /** 音声検知のしきい値（dB）。これより大きい音で送り始める */
    public double thresholdDb = -40;
    /** マイクの増幅（1.0 = そのまま） */
    public double micGain = 1.0;
    /** 聞こえる声全体の大きさ（1.0 = そのまま） */
    public double masterVolume = 1.0;
    /** マイクとスピーカーの機器名。空なら OS の既定 */
    public String micDevice = "";
    public String speakerDevice = "";
    public boolean muted = false;
    public VoiceRange range = VoiceRange.NORMAL;
    public IconCorner iconCorner = IconCorner.BOTTOM_LEFT;
    public boolean showRangeLabel = true;
    /** プレイヤー（UUID の文字列）ごとの音量（1.0 = そのまま、最大 2.0） */
    public ConcurrentHashMap<String, Double> playerVolumes = new ConcurrentHashMap<>();
    /** プレイヤーごとのミュート */
    public ConcurrentHashMap<String, Boolean> mutedPlayers = new ConcurrentHashMap<>();

    private transient Path file;

    /** 読めないときは既定値で動かす（ゲームは止めない） */
    public static ClientConfig load(Path file) {
        ClientConfig c = null;
        if (Files.exists(file)) {
            try {
                c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ClientConfig.class);
            } catch (IOException | JsonParseException e) {
                McVoiceChat.LOGGER.warn("[mc-voice-chat] {} を読めないので既定値で動きます: {}", file, e.getMessage());
            }
        }
        if (c == null) {
            c = new ClientConfig();
        }
        c.file = file;
        c.fillDefaults();
        c.save();
        return c;
    }

    private void fillDefaults() {
        if (talkMode == null) {
            talkMode = TalkMode.VOICE;
        }
        if (range == null) {
            range = VoiceRange.NORMAL;
        }
        if (iconCorner == null) {
            iconCorner = IconCorner.BOTTOM_LEFT;
        }
        if (micDevice == null) {
            micDevice = "";
        }
        if (speakerDevice == null) {
            speakerDevice = "";
        }
        if (playerVolumes == null) {
            playerVolumes = new ConcurrentHashMap<>();
        }
        if (mutedPlayers == null) {
            mutedPlayers = new ConcurrentHashMap<>();
        }
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            McVoiceChat.LOGGER.warn("[mc-voice-chat] {} に書けませんでした: {}", file, e.getMessage());
        }
    }

    public double playerVolume(UUID id) {
        return playerVolumes.getOrDefault(id.toString(), 1.0);
    }

    public void setPlayerVolume(UUID id, double volume) {
        playerVolumes.put(id.toString(), volume);
    }

    public boolean isPlayerMuted(UUID id) {
        return mutedPlayers.getOrDefault(id.toString(), false);
    }

    public void setPlayerMuted(UUID id, boolean mute) {
        if (mute) {
            mutedPlayers.put(id.toString(), true);
        } else {
            mutedPlayers.remove(id.toString());
        }
    }
}
