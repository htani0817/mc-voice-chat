package jp.mc.voicechat.server;

import jp.mc.voicechat.CallState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallBookTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void 発信して応答すると通話中になり_切ると両方とも通話なしに戻る() {
        CallBook book = new CallBook();
        assertEquals(CallBook.Result.OK, book.call(a, b, 0));
        assertEquals(CallState.OUTGOING, book.view(a).state());
        assertEquals(CallState.INCOMING, book.view(b).state());
        assertNull(book.activePeer(a), "応答前は声をつながない");

        assertEquals(a, book.accept(b));
        assertEquals(CallState.ACTIVE, book.view(a).state());
        assertEquals(b, book.activePeer(a));
        assertEquals(a, book.activePeer(b));
        assertEquals(Set.of(a, b), book.activePlayers());

        CallBook.Ended ended = book.hangup(b);
        assertTrue(ended.active());
        assertEquals(a, ended.other(b));
        assertEquals(CallState.NONE, book.view(a).state());
        assertEquals(CallState.NONE, book.view(b).state());
    }

    @Test
    void 発信した側は応答できない() {
        CallBook book = new CallBook();
        book.call(a, b, 0);
        assertNull(book.accept(a));
    }

    @Test
    void 通話中の人にはかけられない() {
        CallBook book = new CallBook();
        book.call(a, b, 0);
        assertEquals(CallBook.Result.BUSY_PEER, book.call(c, b, 0));
        assertEquals(CallBook.Result.BUSY_SELF, book.call(a, c, 0));
        assertEquals(CallBook.Result.SELF, book.call(c, c, 0));
    }

    @Test
    void 応答がないまま30秒たつと取り消される_通話中は切れない() {
        CallBook book = new CallBook();
        book.call(a, b, 0);
        book.call(c, UUID.randomUUID(), 0);
        book.accept(book.view(c).peer());
        assertTrue(book.expire(CallBook.RING_NANOS - 1).isEmpty());
        var expired = book.expire(CallBook.RING_NANOS);
        assertEquals(1, expired.size());
        assertFalse(expired.get(0).active());
        assertEquals(CallState.NONE, book.view(a).state());
        assertEquals(CallState.ACTIVE, book.view(c).state());
    }
}
