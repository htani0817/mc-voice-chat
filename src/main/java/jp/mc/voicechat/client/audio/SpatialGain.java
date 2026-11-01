package jp.mc.voicechat.client.audio;

/**
 * 聞こえ方の計算。距離で小さくし、話者のいる向きで左右に振る。
 */
public final class SpatialGain {
    /** 真横から聞こえるとき、反対側の耳に残す大きさ */
    private static final float FAR_EAR = 0.3f;

    private SpatialGain() {
    }

    /** 距離による大きさ。1 ブロック以内はそのまま、範囲の端で 0 になるまで直線で下げる */
    public static float distanceGain(double distance, double range) {
        if (range <= 0 || distance >= range) {
            return 0f;
        }
        if (distance <= 1) {
            return 1f;
        }
        return (float) (1 - (distance - 1) / (range - 1));
    }

    /**
     * 話者のいる向き。-1 で真左、0 で正面か真後ろ、1 で真右。
     * Minecraft の yaw は 0 で南（+Z）向き、90 で西（-X）向き。
     */
    public static float pan(double listenerX, double listenerZ, float yawDegrees, double speakerX, double speakerZ) {
        double dx = speakerX - listenerX;
        double dz = speakerZ - listenerZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3) {
            return 0f;
        }
        double yaw = Math.toRadians(yawDegrees);
        // 向いている方向から見た右手側の向き
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        return (float) Math.max(-1, Math.min(1, (dx * rightX + dz * rightZ) / len));
    }

    /** 向きから左の耳の大きさ */
    public static float left(float pan) {
        return pan > 0 ? 1 - pan * (1 - FAR_EAR) : 1f;
    }

    /** 向きから右の耳の大きさ */
    public static float right(float pan) {
        return pan < 0 ? 1 + pan * (1 - FAR_EAR) : 1f;
    }
}
