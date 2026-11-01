package jp.mc.voicechat.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.mc.voicechat.McVoiceChat;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * キー割り当て。どれも「設定 → 操作設定 → キー割り当て」の「ボイスチャット」から変えられる。
 */
public final class VoiceKeys {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "main"));

    /** 範囲の切替。GLFW のキー番号は US 配列の位置で決まるので、JIS 配列の「@」は US の「[」の位置になる */
    public static final KeyMapping RANGE = key("range", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping MUTE = key("mute", GLFW.GLFW_KEY_M);
    public static final KeyMapping PUSH_TO_TALK = key("push_to_talk", GLFW.GLFW_KEY_CAPS_LOCK);
    public static final KeyMapping SETTINGS = key("settings", GLFW.GLFW_KEY_V);
    /** 着信に出る・通話を切る。既定は割り当てなし（設定画面の「通話」タブからでも操作できる） */
    public static final KeyMapping CALL = key("call", GLFW.GLFW_KEY_UNKNOWN);

    private VoiceKeys() {
    }

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping("key." + McVoiceChat.MODID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
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
