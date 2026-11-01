package jp.mc.voicechat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.mc.voicechat.McVoiceChat;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.List;

/**
 * プレイヤーの名前の上に、パーティー名と「通話中」を重ねて出す。
 * <p>
 * 名前を出すかどうかが決まるとき（CanRender）に出す行を描画の状態に書いておき、
 * 名前を描くとき（DoRender）にその上へ 1 行ずつ積む。名前が出ない距離やスニーク中は、この行も出ない。
 */
final class NameTagBanner {
    private static final ContextKey<List<Component>> LINES =
            new ContextKey<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "banner"));
    /** 名前 1 行分の高さ（バニラがスコアの行の上に名前を載せるときと同じ量） */
    private static final float LINE_HEIGHT = 9.0F * 1.15F * 0.025F;

    private NameTagBanner() {
    }

    static void onCanRender(RenderNameTagEvent.CanRender event) {
        VoiceClient vc = VoiceClient.get();
        if (vc == null || !(event.getEntity() instanceof Player player)) {
            return;
        }
        List<Component> lines = vc.bannerLines(player.getUUID());
        // 描画の状態は使い回されることがあるので、出さないときも null で上書きする
        event.getEntityRenderState().setRenderData(LINES, lines.isEmpty() ? null : lines);
    }

    static void onDoRender(RenderNameTagEvent.DoRender event) {
        EntityRenderState state = event.getEntityRenderState();
        // スコアの行（名前の下の行）のときは何もしない
        if (event.getContent() != state.nameTag || state.nameTagAttachment == null) {
            return;
        }
        List<Component> lines = state.getRenderData(LINES);
        if (lines == null) {
            return;
        }
        int offset = state instanceof AvatarRenderState avatar && avatar.showExtraEars ? -10 : 0;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        for (Component line : lines) {
            pose.translate(0.0F, LINE_HEIGHT, 0.0F);
            event.getSubmitNodeCollector().submitNameTag(pose, state.nameTagAttachment, offset, line,
                    !state.isDiscrete, state.lightCoords, event.getCameraRenderState());
        }
        pose.popPose();
    }
}
