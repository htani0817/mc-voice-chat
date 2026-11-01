package jp.mc.voicechat.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JitterBufferTest {
    private static byte[] f(int n) {
        return new byte[]{(byte) n};
    }

    @Test
    void 溜まるまでは鳴らさず_溜まったら番号順に出す() {
        JitterBuffer jb = new JitterBuffer();
        jb.push(11, f(11));
        jb.push(10, f(10));
        assertNull(jb.poll());
        jb.push(12, f(12));
        assertArrayEquals(f(10), jb.poll());
        assertArrayEquals(f(11), jb.poll());
        assertArrayEquals(f(12), jb.poll());
        assertNull(jb.poll());
        assertTrue(jb.isIdle());
    }

    @Test
    void 欠けた番号は欠けとして返し_遅れて来たものは捨てる() {
        JitterBuffer jb = new JitterBuffer();
        jb.push(0, f(0));
        jb.push(2, f(2));
        jb.push(3, f(3));
        assertArrayEquals(f(0), jb.poll());
        assertSame(JitterBuffer.LOST, jb.poll());
        jb.push(1, f(1)); // もう間に合わない
        assertArrayEquals(f(2), jb.poll());
        assertArrayEquals(f(3), jb.poll());
        assertNull(jb.poll());
    }

    @Test
    void 相手が入り直して番号が戻ったら最初から() {
        JitterBuffer jb = new JitterBuffer();
        for (int i = 100; i < 103; i++) {
            jb.push(i, f(i));
        }
        jb.poll();
        for (int i = 0; i < 3; i++) {
            jb.push(i, f(i));
        }
        assertArrayEquals(f(0), jb.poll());
    }

    @Test
    void 溜まりすぎたら古いものを捨てる() {
        JitterBuffer jb = new JitterBuffer();
        for (int i = 0; i < JitterBuffer.MAX_FRAMES + 5; i++) {
            jb.push(i, f(i));
        }
        assertArrayEquals(f(5), jb.poll());
    }
}
