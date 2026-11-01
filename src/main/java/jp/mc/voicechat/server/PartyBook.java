package jp.mc.voicechat.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * パーティーVCの台帳。メモリーにだけ持つので、サーバーを止めるとすべて消える。
 * <p>
 * 1 人が入れるパーティーは 1 つだけ（別のパーティーに入ると前のパーティーから抜ける）。
 * 名前とパスワードを変えられるのと削除できるのは作った人だけ。作った人がパーティーから抜けても、
 * サーバーにいなくても、パーティーは残る（誰もいないパーティーも残る）。
 * <p>
 * 声の中継（ネットワークスレッド）からも読むので、すべて synchronized にしている。
 */
public final class PartyBook {
    public static final int MAX_NAME = 24;
    public static final int MAX_PASSWORD = 32;
    public static final int MAX_PARTIES = 64;

    public enum Result { OK, BAD_NAME, NAME_TAKEN, BAD_PASSWORD, TOO_MANY, NOT_FOUND, WRONG_PASSWORD, NOT_OWNER, NOT_IN_PARTY }

    /** 外に見せる形（パスワードそのものは出さない） */
    public record Info(int id, String name, boolean locked, UUID owner, List<UUID> members) {
    }

    private static final class Party {
        final int id;
        final UUID owner;
        String name;
        String password;
        /** 入った順 */
        final Set<UUID> members = new LinkedHashSet<>();

        Party(int id, UUID owner, String name, String password) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.password = password;
        }
    }

    private final Map<Integer, Party> parties = new HashMap<>();
    private final Map<UUID, Party> byMember = new HashMap<>();
    private int nextId = 1;

    /** パーティーを作り、作った人をそのまま入れる */
    public synchronized Result create(UUID owner, String rawName, String password) {
        String name = cleanName(rawName);
        if (name == null) {
            return Result.BAD_NAME;
        }
        if (!validPassword(password)) {
            return Result.BAD_PASSWORD;
        }
        if (nameTaken(name, -1)) {
            return Result.NAME_TAKEN;
        }
        if (parties.size() >= MAX_PARTIES) {
            return Result.TOO_MANY;
        }
        Party p = new Party(nextId++, owner, name, password);
        parties.put(p.id, p);
        moveTo(owner, p);
        return Result.OK;
    }

    /** パーティーに入る。作った人はパスワードなしで入れる */
    public synchronized Result join(UUID player, int id, String password) {
        Party p = parties.get(id);
        if (p == null) {
            return Result.NOT_FOUND;
        }
        if (!p.password.isEmpty() && !p.owner.equals(player) && !p.password.equals(password)) {
            return Result.WRONG_PASSWORD;
        }
        moveTo(player, p);
        return Result.OK;
    }

    public synchronized Result leave(UUID player) {
        Party p = byMember.remove(player);
        if (p == null) {
            return Result.NOT_IN_PARTY;
        }
        p.members.remove(player);
        return Result.OK;
    }

    /** いま入っているパーティーの名前を変える（作った人だけ） */
    public synchronized Result rename(UUID player, String rawName) {
        Party p = byMember.get(player);
        if (p == null) {
            return Result.NOT_IN_PARTY;
        }
        if (!p.owner.equals(player)) {
            return Result.NOT_OWNER;
        }
        String name = cleanName(rawName);
        if (name == null) {
            return Result.BAD_NAME;
        }
        if (nameTaken(name, p.id)) {
            return Result.NAME_TAKEN;
        }
        p.name = name;
        return Result.OK;
    }

    /** いま入っているパーティーのパスワードを変える（作った人だけ。空ならパスワードなし） */
    public synchronized Result setPassword(UUID player, String password) {
        Party p = byMember.get(player);
        if (p == null) {
            return Result.NOT_IN_PARTY;
        }
        if (!p.owner.equals(player)) {
            return Result.NOT_OWNER;
        }
        if (!validPassword(password)) {
            return Result.BAD_PASSWORD;
        }
        p.password = password;
        return Result.OK;
    }

    /** パーティーを消す（作った人だけ）。入っていた人はパーティーから外れる */
    public synchronized Result delete(UUID player, int id) {
        Party p = parties.get(id);
        if (p == null) {
            return Result.NOT_FOUND;
        }
        if (!p.owner.equals(player)) {
            return Result.NOT_OWNER;
        }
        parties.remove(id);
        for (UUID m : p.members) {
            byMember.remove(m);
        }
        return Result.OK;
    }

    /** 同じパーティーにいる自分以外の人 */
    public synchronized List<UUID> matesOf(UUID player) {
        Party p = byMember.get(player);
        if (p == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>(p.members.size());
        for (UUID m : p.members) {
            if (!m.equals(player)) {
                out.add(m);
            }
        }
        return out;
    }

    /** そのパーティーに入っている人（無ければ空） */
    public synchronized List<UUID> membersOf(int id) {
        Party p = parties.get(id);
        return p == null ? List.of() : List.copyOf(p.members);
    }

    /** 入っているパーティーの名前（入っていなければ null） */
    public synchronized String partyNameOf(UUID player) {
        Party p = byMember.get(player);
        return p == null ? null : p.name;
    }

    /** 作った順に並べた一覧 */
    public synchronized List<Info> list() {
        return parties.values().stream()
                .sorted(Comparator.comparingInt(p -> p.id))
                .map(p -> new Info(p.id, p.name, !p.password.isEmpty(), p.owner, List.copyOf(p.members)))
                .toList();
    }

    public synchronized void clear() {
        parties.clear();
        byMember.clear();
        nextId = 1;
    }

    private void moveTo(UUID player, Party p) {
        Party old = byMember.put(player, p);
        if (old != null) {
            old.members.remove(player);
        }
        p.members.add(player);
    }

    private boolean nameTaken(String name, int exceptId) {
        String key = name.toLowerCase(Locale.ROOT);
        return parties.values().stream().anyMatch(p -> p.id != exceptId && p.name.toLowerCase(Locale.ROOT).equals(key));
    }

    /**
     * 名前を整える。前後の空白を落とし、制御文字と書式コード（§）は認めない。
     * 使えない名前なら null。
     */
    static String cleanName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.strip();
        if (name.isEmpty() || name.length() > MAX_NAME || hasBadChar(name)) {
            return null;
        }
        return name;
    }

    static boolean validPassword(String password) {
        return password != null && password.length() <= MAX_PASSWORD && !hasBadChar(password);
    }

    private static boolean hasBadChar(String s) {
        return s.chars().anyMatch(c -> c == '§' || Character.isISOControl(c));
    }
}
