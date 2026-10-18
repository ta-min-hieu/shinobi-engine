package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToLongFunction;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.domain.PvpRating;

/**
 * Bia Anh Danh (ADR-057) — ba bảng xếp hạng, mỗi bảng tính lại TỐI ĐA một lần mỗi 60 giây, lười (chỉ
 * khi có người hỏi mà bản cũ đã quá hạn). Giữ TRỌN bảng (không chỉ top 50) để trả được hạng của chính
 * người hỏi trên CÙNG bản đã tính.
 *
 * <p>Một bản cache cho mỗi bộ facade (một server = một bộ): handler sống theo KẾT NỐI nên không giữ
 * được cache; khoá theo danh tính {@link CharacterFacade} (yếu) thay vì một biến tĩnh trơn để hai server
 * trong cùng JVM (test) không bao giờ thấy bảng của nhau.
 *
 * <p>Chạm DB — chỉ gọi trên Virtual Thread ({@code authExecutor}). Hai người hỏi cùng lúc lúc bảng quá
 * hạn: khoá theo LOẠI bảng, người sau chờ rồi dùng bản người trước vừa tính.
 */
final class LeaderboardCache {

    /** Thời gian sống của một bản (ADR-057 D3). */
    static final Duration TIME_TO_LIVE = Duration.ofSeconds(60);

    /** Số dòng gửi cho client. */
    static final int TOP_SIZE = 50;

    private static final Map<CharacterFacade, LeaderboardCache> BY_FACADES = new WeakHashMap<>();

    enum Kind {
        REALM,
        ARENA,
        COMBAT_POWER
    }

    /** Một dòng đã xếp hạng. {@code rank} bắt đầu từ 1. */
    record Row(int rank, long characterId, String name, Faction faction, int level, long value) {}

    /** Một bản bảng xếp hạng: toàn bộ dòng theo thứ hạng + tra hạng theo nhân vật. */
    record Board(List<Row> rows, Map<Long, Row> byCharacterId, Instant computedAt) {}

    private final CharacterFacade characterFacade;
    private final PvpFacade pvpFacade;
    private final ToLongFunction<Character> combatPowerOf;
    private final Clock clock;
    private final Map<Kind, Board> boards = new EnumMap<>(Kind.class);
    private final Map<Kind, Object> locks = new EnumMap<>(Kind.class);

    LeaderboardCache(
            CharacterFacade characterFacade,
            PvpFacade pvpFacade,
            ToLongFunction<Character> combatPowerOf,
            Clock clock) {
        this.characterFacade = characterFacade;
        this.pvpFacade = pvpFacade;
        this.combatPowerOf = combatPowerOf;
        this.clock = clock;
        for (Kind kind : Kind.values()) {
            locks.put(kind, new Object());
        }
    }

    /** Bản dùng chung của server sở hữu {@code characterFacade} — dựng ở lần hỏi đầu tiên. */
    static LeaderboardCache shared(
            CharacterFacade characterFacade, PvpFacade pvpFacade, ToLongFunction<Character> combatPowerOf) {
        synchronized (BY_FACADES) {
            return BY_FACADES.computeIfAbsent(
                    characterFacade,
                    facade -> new LeaderboardCache(facade, pvpFacade, combatPowerOf, Clock.systemUTC()));
        }
    }

    Board board(Kind kind) {
        synchronized (locks.get(kind)) {
            Board current = boards.get(kind);
            Instant now = clock.instant();
            if (current != null && current.computedAt().plus(TIME_TO_LIVE).isAfter(now)) {
                return current;
            }
            Board fresh = compute(kind, now);
            boards.put(kind, fresh);
            return fresh;
        }
    }

    private Board compute(Kind kind, Instant now) {
        List<Character> characters = characterFacade.listAllByRealm();
        List<Row> rows =
                switch (kind) {
                    case REALM -> rank(characters, Character::exp);
                    case COMBAT_POWER -> rankByCombatPower(characters);
                    case ARENA -> rankByRating(characters);
                };
        Map<Long, Row> byId = new HashMap<>();
        for (Row row : rows) {
            byId.put(row.characterId(), row);
        }
        return new Board(List.copyOf(rows), Collections.unmodifiableMap(byId), now);
    }

    /** {@code characters} đã theo đúng thứ tự (cấp, EXP, id) của SQL — chỉ đánh số. */
    private static List<Row> rank(List<Character> ordered, ToLongFunction<Character> value) {
        List<Row> rows = new ArrayList<>(ordered.size());
        for (Character c : ordered) {
            rows.add(new Row(rows.size() + 1, c.id(), c.name(), c.faction(), c.level(), value.applyAsLong(c)));
        }
        return rows;
    }

    private List<Row> rankByCombatPower(List<Character> characters) {
        record Scored(Character character, long power) {}
        List<Scored> scored = new ArrayList<>(characters.size());
        for (Character c : characters) {
            scored.add(new Scored(c, combatPowerOf.applyAsLong(c)));
        }
        scored.sort(Comparator.comparingLong(Scored::power).reversed().thenComparingLong(s -> s.character()
                .id()));
        List<Row> rows = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            Character c = s.character();
            rows.add(new Row(rows.size() + 1, c.id(), c.name(), c.faction(), c.level(), s.power()));
        }
        return rows;
    }

    private List<Row> rankByRating(List<Character> characters) {
        Map<Long, Character> byId = new HashMap<>();
        for (Character c : characters) {
            byId.put(c.id(), c);
        }
        List<Row> rows = new ArrayList<>();
        for (PvpRating rating : pvpFacade.listAllByRating()) {
            Character c = byId.get(rating.characterId());
            if (c == null) {
                continue; // nhân vật đã xoá mà dòng rating còn — không có tên để hiện
            }
            rows.add(new Row(rows.size() + 1, c.id(), c.name(), c.faction(), c.level(), rating.rating()));
        }
        return rows;
    }
}
