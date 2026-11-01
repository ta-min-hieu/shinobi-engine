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
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.guild.domain.GuildRank;
import dev.kage.monster.domain.WorldBossPort;
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
        COMBAT_POWER,
        /** ADR-068 — bang hội theo kinh nghiệm bang; mỗi THÀNH VIÊN tra được hạng bang mình. */
        GUILD,
        /** ADR-081 — tổng sát thương lên boss thế giới. */
        BOSS_DAMAGE,
        /** ADR-134 — cấp linh mạch động phủ. */
        CAVE,
        /** ADR-134 — số kiếp Thiên Kiếp đã qua. */
        TRIBULATION
    }

    /** Một dòng đã xếp hạng. {@code rank} bắt đầu từ 1. */
    record Row(int rank, long characterId, String name, Faction faction, int level, long value) {}

    /** Một bản bảng xếp hạng: toàn bộ dòng theo thứ hạng + tra hạng theo nhân vật. */
    record Board(List<Row> rows, Map<Long, Row> byCharacterId, Instant computedAt) {}

    private final CharacterFacade characterFacade;
    private final PvpFacade pvpFacade;
    private final ToLongFunction<Character> combatPowerOf;
    private final Supplier<List<GuildRank>> guildRanks;
    private final Supplier<List<WorldBossPort.DamageRank>> bossDamage;
    private final Supplier<Map<Long, Integer>> caveLevels;
    private final Supplier<Map<Long, Integer>> tribulations;
    private final Clock clock;
    private final Map<Kind, Board> boards = new EnumMap<>(Kind.class);
    private final Map<Kind, Object> locks = new EnumMap<>(Kind.class);

    /** Nguồn dữ liệu của các bảng — gom để constructor không vượt trần tham số. */
    record Sources(
            CharacterFacade characterFacade,
            PvpFacade pvpFacade,
            ToLongFunction<Character> combatPowerOf,
            Supplier<List<GuildRank>> guildRanks,
            Supplier<List<WorldBossPort.DamageRank>> bossDamage,
            Supplier<Map<Long, Integer>> caveLevels,
            Supplier<Map<Long, Integer>> tribulations) {}

    LeaderboardCache(Sources sources, Clock clock) {
        this.characterFacade = sources.characterFacade();
        this.pvpFacade = sources.pvpFacade();
        this.combatPowerOf = sources.combatPowerOf();
        this.guildRanks = sources.guildRanks();
        this.bossDamage = sources.bossDamage();
        this.caveLevels = sources.caveLevels();
        this.tribulations = sources.tribulations();
        this.clock = clock;
        for (Kind kind : Kind.values()) {
            locks.put(kind, new Object());
        }
    }

    /** Bản dùng chung của server sở hữu {@code characterFacade} — dựng ở lần hỏi đầu tiên. */
    static LeaderboardCache shared(Sources sources) {
        synchronized (BY_FACADES) {
            return BY_FACADES.computeIfAbsent(
                    sources.characterFacade(), facade -> new LeaderboardCache(sources, Clock.systemUTC()));
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
        List<Row> rows = rowsFor(kind, characters);
        Map<Long, Row> byId = new HashMap<>();
        for (Row row : rows) {
            byId.put(row.characterId(), row);
        }
        if (kind == Kind.GUILD) {
            rows = rankGuilds(characters, byId);
        }
        return new Board(List.copyOf(rows), Collections.unmodifiableMap(byId), now);
    }

    private List<Row> rowsFor(Kind kind, List<Character> characters) {
        return switch (kind) {
            case REALM -> rank(characters, Character::exp);
            case COMBAT_POWER -> rankByCombatPower(characters);
            case ARENA -> rankByRating(characters);
            case GUILD -> List.of();
            case BOSS_DAMAGE -> rankByBossDamage(characters);
            case CAVE -> rankByScore(characters, caveLevels.get());
            case TRIBULATION -> rankByScore(characters, tribulations.get());
        };
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

    /**
     * ADR-068 — dòng bang: tên bang, cấp bang, kinh nghiệm bang; id nhân vật + hệ trên dòng là của bang chủ. Mọi
     * thành viên được ghi vào {@code byId} để "hạng của tôi" là hạng bang mình.
     */
    private List<Row> rankGuilds(List<Character> characters, Map<Long, Row> byId) {
        Map<Long, Character> charactersById = new HashMap<>();
        for (Character c : characters) {
            charactersById.put(c.id(), c);
        }
        List<Row> rows = new ArrayList<>();
        for (GuildRank guild : guildRanks.get()) {
            Character leader = charactersById.get(guild.leaderCharacterId());
            Faction faction = leader == null ? Faction.HOA : leader.faction();
            Row row = new Row(
                    rows.size() + 1, guild.leaderCharacterId(), guild.name(), faction, guild.level(), guild.exp());
            rows.add(row);
            for (long member : guild.memberIds()) {
                byId.put(member, row);
            }
        }
        return rows;
    }

    /**
     * ADR-134 — xếp theo điểm giảm dần, chỉ người CÓ điểm; hoà điểm thì giữ thứ tự cảnh giới của {@code ordered}
     * (cấp, EXP, id — đúng thứ tự SQL), nên sắp xếp phải ổn định.
     */
    private static List<Row> rankByScore(List<Character> ordered, Map<Long, Integer> scores) {
        List<Character> scored = new ArrayList<>();
        for (Character c : ordered) {
            if (scores.containsKey(c.id())) {
                scored.add(c);
            }
        }
        scored.sort(Comparator.comparingInt((Character c) -> scores.get(c.id())).reversed());
        List<Row> rows = new ArrayList<>(scored.size());
        for (Character c : scored) {
            rows.add(new Row(rows.size() + 1, c.id(), c.name(), c.faction(), c.level(), scores.get(c.id())));
        }
        return rows;
    }

    /** ADR-081 — {@code world_boss_damage} đã theo thứ tự của SQL; nhân vật đã xoá thì bỏ qua. */
    private List<Row> rankByBossDamage(List<Character> characters) {
        Map<Long, Character> byId = new HashMap<>();
        for (Character c : characters) {
            byId.put(c.id(), c);
        }
        List<Row> rows = new ArrayList<>();
        for (WorldBossPort.DamageRank damage : bossDamage.get()) {
            Character c = byId.get(damage.characterId());
            if (c != null) {
                rows.add(new Row(rows.size() + 1, c.id(), c.name(), c.faction(), c.level(), damage.totalDamage()));
            }
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
