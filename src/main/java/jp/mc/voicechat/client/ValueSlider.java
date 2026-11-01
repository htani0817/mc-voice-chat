package jp.mc.voicechat.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * min〜max の値を動かすスライダー。表示の文言と、動かしたときの処理を渡して使う。
 */
final class ValueSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer onChange;

    ValueSlider(int x, int y, int width, double min, double max, double initial,
                DoubleFunction<Component> label, DoubleConsumer onChange) {
        super(x, y, width, 20, Component.empty(), (clamp(initial, min, max) - min) / (max - min));
        this.min = min;
        this.max = max;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    double current() {
        return min + value * (max - min);
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(current()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(current());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 0.0〜2.0 の倍率を「100%」の形で出す */
    static Component percent(String key, double v) {
        return Component.translatable(key, Math.round(v * 100));
    }
}
