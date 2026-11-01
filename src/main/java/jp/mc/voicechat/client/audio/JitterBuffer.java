package jp.mc.voicechat.client.audio;

import java.util.TreeMap;

/**
 * 話者 1 人ぶんの受信フレームの待ち行列。
 * 届く間隔のばらつきを吸収するために少し溜めてから鳴らし、連番の順に取り出す。欠けたフレームは「欠け」として返す。
 */
public final class JitterBuffer {
    /** 欠けたフレームを表す印（Opus の補間で埋める） */
    public static final byte[] LOST = new byte[0];

    /** 鳴らし始める前に溜めるフレーム数（60ms） */
    static final int START_FRAMES = 3;
    /** これより溜まったら古いものから捨てる（遅れが積もるのを防ぐ。0.4 秒） */
    static final int MAX_FRAMES = 20;
    /** 続けて欠けたらその先の届いているフレームまで飛ばす数 */
    static final int MAX_MISSES = 5;
    /** 鳴らしている位置よりこれだけ古い番号が来たら、相手が入り直したとみなして最初からにする */
    static final int RESTART_GAP = 50;

    private final TreeMap<Integer, byte[]> frames = new TreeMap<>();
    private boolean playing;
    private int nextSeq;
    private int misses;

    public synchronized void push(int seq, byte[] opus) {
        if (playing && seq < nextSeq) {
            if (nextSeq - seq < RESTART_GAP) {
                return; // 間に合わなかったフレーム
            }
            frames.clear();
            playing = false;
        }
        frames.put(seq, opus);
        while (frames.size() > MAX_FRAMES) {
            frames.pollFirstEntry();
            if (playing) {
                nextSeq = frames.firstKey();
            }
        }
    }

    /**
     * 次の 20ms ぶんを取り出す。
     *
     * @return Opus のフレーム、欠けなら {@link #LOST}、鳴らすものが無ければ null
     */
    public synchronized byte[] poll() {
        if (!playing) {
            if (frames.size() < START_FRAMES) {
                return null;
            }
            playing = true;
            nextSeq = frames.firstKey();
            misses = 0;
        }
        byte[] frame = frames.remove(nextSeq);
        if (frame != null) {
            nextSeq++;
            misses = 0;
            return frame;
        }
        if (frames.isEmpty()) {
            // 話し終わった（か、届くのが遅れている）。また溜まるまで待つ
            playing = false;
            return null;
        }
        misses++;
        nextSeq = misses > MAX_MISSES ? frames.firstKey() : nextSeq + 1;
        return LOST;
    }

    public synchronized boolean isIdle() {
        return !playing && frames.isEmpty();
    }
}
