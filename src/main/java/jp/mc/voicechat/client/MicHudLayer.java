package jp.mc.voicechat.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * 画面の端に出すドット絵のマイク。1 ドットを GUI の 1px で塗るので、テクスチャを使わずににじまない。
 * <ul>
 * <li>待機中: 灰色</li>
 * <li>自分の声を送っている: 緑</li>
 * <li>ミュート中: 灰色に赤い斜線</li>
 * <li>サーバーが非対応: 暗い灰色</li>
 * </ul>
 */
public final class MicHudLayer implements GuiLayer {
    /** 16×16 のドット絵。o=カプセルの縁、b=カプセル、h=光、s=スタンド */
    private static final String[] MIC = {
            "................",
            "......oooo......",
            ".....obbbho.....",
            ".....obbbho.....",
            ".....obbbho.....",
            ".....obbbho.....",
            ".....obbbho.....",
            "...s.obbbho.s...",
            "...s.obbbho.s...",
            "...s..oooo..s...",
            "....s......s....",
            ".....ssssss.....",
            ".......ss.......",
            ".......ss.......",
            ".....ssssss.....",
            "................",
    };
    private static final int SIZE = 16;
    private static final int PAD = 2;
    private static final int MARGIN = 4;

    /** 状態ごとの色（縁・カプセル・光・スタンド） */
    private record Palette(int edge, int body, int light, int stand) {
    }

    private static final Palette IDLE = new Palette(0xFF8A8A8A, 0xFFC8C8C8, 0xFFFFFFFF, 0xFFB4B4B4);
    private static final Palette TALKING = new Palette(0xFF2E9E44, 0xFF4CD964, 0xFFC4F7CF, 0xFF6FE085);
    private static final Palette OFFLINE = new Palette(0xFF3C3C3C, 0xFF5A5A5A, 0xFF7A7A7A, 0xFF505050);
    private static final int BACKDROP = 0x66000000;
    private static final int SLASH = 0xFFE53935;
    private static final int SLASH_EDGE = 0xFF5C0F0E;

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        VoiceClient vc = VoiceClient.get();
        if (mc.gui.hud.isHidden() || mc.player == null || vc == null) {
            return;
        }
        ClientConfig config = vc.config();
        String label = config.showRangeLabel ? config.range.label() + " " + config.range.blocks() : null;
        int boxW = SIZE + PAD * 2;
        int labelW = label == null ? 0 : mc.font.width(label) + 4;
        int x = switch (config.iconCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> g.guiWidth() - MARGIN - boxW - labelW;
        };
        int y = switch (config.iconCorner) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> g.guiHeight() - MARGIN - boxW;
        };
        drawIcon(g, x, y, vc);
        if (label != null) {
            int color = vc.isServerReady() ? 0xFFFFFFFF : 0xFF9A9A9A;
            g.text(mc.font, label, x + boxW + 3, y + (boxW - 8) / 2, color, true);
        }
    }

    /** 背景の四角を含めて (x, y) から 20×20 に描く。設定画面の見本にも使う */
    static void drawIcon(GuiGraphicsExtractor g, int x, int y, VoiceClient vc) {
        boolean muted = vc.config().muted;
        Palette p = !vc.isServerReady() ? OFFLINE : (vc.isTransmitting() && !muted ? TALKING : IDLE);
        int box = SIZE + PAD * 2;
        // 角を 1px 落とした背景
        g.fill(x + 1, y, x + box - 1, y + box, BACKDROP);
        g.fill(x, y + 1, x + 1, y + box - 1, BACKDROP);
        g.fill(x + box - 1, y + 1, x + box, y + box - 1, BACKDROP);
        int ox = x + PAD;
        int oy = y + PAD;
        for (int row = 0; row < SIZE; row++) {
            String line = MIC[row];
            for (int col = 0; col < SIZE; col++) {
                int color = switch (line.charAt(col)) {
                    case 'o' -> p.edge();
                    case 'b' -> p.body();
                    case 'h' -> p.light();
                    case 's' -> p.stand();
                    default -> 0;
                };
                if (color != 0) {
                    g.fill(ox + col, oy + row, ox + col + 1, oy + row + 1, color);
                }
            }
        }
        if (muted) {
            // 左上から右下への 2 ドット幅の赤い斜線。両脇に暗い縁を付けて、どの背景でも見えるようにする
            for (int i = 1; i < SIZE - 1; i++) {
                g.fill(ox + i - 1, oy + i, ox + i, oy + i + 1, SLASH_EDGE);
                g.fill(ox + i, oy + i, ox + i + 2, oy + i + 1, SLASH);
                g.fill(ox + i + 2, oy + i, ox + i + 3, oy + i + 1, SLASH_EDGE);
            }
        }
    }
}
