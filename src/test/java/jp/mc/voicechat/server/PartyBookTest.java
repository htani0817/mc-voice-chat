package jp.mc.voicechat.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyBookTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();

    private static int onlyId(PartyBook book) {
        return book.list().get(0).id();
    }

    @Test
    void 作った人はそのまま入り_パスワードなしなら誰でも入れる() {
        PartyBook book = new PartyBook();
        assertEquals(PartyBook.Result.OK, book.create(owner, "  探検隊  ", ""));
        PartyBook.Info info = book.list().get(0);
        assertEquals("探検隊", info.name(), "前後の空白は落とす");
        assertFalse(info.locked());
        assertEquals(List.of(owner), info.members());

        assertEquals(PartyBook.Result.OK, book.join(guest, info.id(), ""));
        assertEquals(List.of(guest), book.matesOf(owner));
        assertEquals("探検隊", book.partyNameOf(guest));
    }

    @Test
    void パスワード付きは合っているときだけ入れる() {
        PartyBook book = new PartyBook();
        book.create(owner, "秘密基地", "pass");
        int id = onlyId(book);
        assertTrue(book.list().get(0).locked());
        assertEquals(PartyBook.Result.WRONG_PASSWORD, book.join(guest, id, "nope"));
        assertEquals(PartyBook.Result.OK, book.join(guest, id, "pass"));
    }

    @Test
    void 名前とパスワードを変えられるのと削除できるのは作った人だけ() {
        PartyBook book = new PartyBook();
        book.create(owner, "A", "");
        int id = onlyId(book);
        book.join(guest, id, "");
        assertEquals(PartyBook.Result.NOT_OWNER, book.rename(guest, "B"));
        assertEquals(PartyBook.Result.NOT_OWNER, book.setPassword(guest, "x"));
        assertEquals(PartyBook.Result.NOT_OWNER, book.delete(guest, id));

        assertEquals(PartyBook.Result.OK, book.rename(owner, "B"));
        assertEquals(PartyBook.Result.OK, book.setPassword(owner, "x"));
        assertEquals("B", book.list().get(0).name());
        assertTrue(book.list().get(0).locked());

        assertEquals(PartyBook.Result.OK, book.delete(owner, id));
        assertTrue(book.list().isEmpty());
        assertNull(book.partyNameOf(guest), "削除したパーティーの人は外れる");
    }

    @Test
    void 別のパーティーに入ると前のパーティーから抜ける_空になってもパーティーは残る() {
        PartyBook book = new PartyBook();
        book.create(owner, "A", "");
        book.create(guest, "B", "");
        int a = book.list().get(0).id();
        int b = book.list().get(1).id();
        assertEquals(PartyBook.Result.OK, book.join(owner, b, ""));
        assertEquals(List.of(), book.membersOf(a));
        assertEquals(List.of(guest, owner), book.membersOf(b));
        assertEquals(2, book.list().size());
    }

    @Test
    void 名前の重複と使えない名前は断る() {
        PartyBook book = new PartyBook();
        book.create(owner, "Team", "");
        assertEquals(PartyBook.Result.NAME_TAKEN, book.create(guest, "team", ""));
        assertEquals(PartyBook.Result.BAD_NAME, book.create(guest, "   ", ""));
        assertEquals(PartyBook.Result.BAD_NAME, book.create(guest, "§cRed", ""));
        assertEquals(PartyBook.Result.BAD_NAME, book.create(guest, "x".repeat(PartyBook.MAX_NAME + 1), ""));
        assertEquals(PartyBook.Result.BAD_PASSWORD, book.create(guest, "Ok", "p".repeat(PartyBook.MAX_PASSWORD + 1)));
    }

    @Test
    void 作った人は自分のパーティーにパスワードなしで戻れる() {
        PartyBook book = new PartyBook();
        book.create(owner, "A", "secret");
        int id = onlyId(book);
        book.leave(owner);
        assertEquals(PartyBook.Result.OK, book.join(owner, id, ""));
    }
}
