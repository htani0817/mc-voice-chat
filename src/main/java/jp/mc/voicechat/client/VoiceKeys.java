package jp.mc.voicechat.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.mc.voicechat.McVoiceChat;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * キー割り当て。どれも「設定 → 操作設定 → キー割り当て」の「ボイスチャット」から変えられる。
 * 26.3 から入力は SDL3 になり、キー番号は SDL のスキャンコード（キーの物理的な位置）になった。
 * 保存名（key.keyboard.left.bracket など）は 26.2 と同じなので、変更済みの割り当てはそのまま引き継がれる。
 */
public final class VoiceKeys {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "main"));

    /** 範囲の切替。スキャンコードは US 配列の位置で決まるので、JIS 配列の「@」は US の「[」の位置になる */
    public static final KeyMapping RANGE = key("range", InputConstants.KEY_LBRACKET);
    public static final KeyMapping MUTE = key("mute", InputConstants.KEY_M);
    public static final KeyMapping PUSH_TO_TALK = key("push_to_talk", InputConstants.KEY_CAPSLOCK);
    public static final KeyMapping SETTINGS = key("settings", InputConstants.KEY_V);
    /** 着信に出る・通話を切る。既定は割り当てなし（設定画面の「通話」タブからでも操作できる） */
    public static final KeyMapping CALL = key("call", InputConstants.UNKNOWN.getValue());

    private VoiceKeys() {
    }

    private static KeyMapping key(String name, int scancode) {
        return new KeyMapping("key." + McVoiceChat.MODID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYBOARD, scancode, CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(RANGE);
        event.register(MUTE);
        event.register(PUSH_TO_TALK);
        event.register(SETTINGS);
        event.register(CALL);
    }
}
