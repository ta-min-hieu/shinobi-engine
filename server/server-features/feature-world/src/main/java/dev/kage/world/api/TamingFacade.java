package dev.kage.world.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.kage.world.domain.TamingPort;
import dev.kage.world.domain.TamingRules;

/** Thu phục yêu thú (ADR-124). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class TamingFacade {

    /** Composition không có DB (test) — không loài nào thu phục được. */
    public static final TamingFacade DISABLED = new TamingFacade(new TamingPort() {
        @Override
        public List<Tameable> tameables() {
            return List.of();
        }

        @Override
        public boolean grantPet(long characterId, long petItemTemplateId, String monsterCode) {
            return false;
        }
    });

    private final TamingPort port;
    private volatile Map<String, TamingPort.Tameable> byMonsterCode;

    /** Dựng ở composition root. */
    public TamingFacade(TamingPort port) {
        this.port = port;
    }

    /** Loài thu phục được theo mã quái. */
    public Optional<TamingPort.Tameable> tameable(String monsterCode) {
        Map<String, TamingPort.Tameable> loaded = byMonsterCode;
        if (loaded == null) {
            loaded = port.tameables().stream()
                    .collect(Collectors.toUnmodifiableMap(TamingPort.Tameable::monsterCode, Function.identity()));
            byMonsterCode = loaded;
        }
        return Optional.ofNullable(loaded.get(monsterCode));
    }

    /**
     * Tỉ lệ (%) theo mã loài cho một nhân vật cấp {@code characterLevel} — tính sẵn ở đây (có thể chạm DB lần đầu) để
     * phía tick thread chỉ còn tra bảng, không I/O.
     */
    public Map<String, Integer> chancesFor(int characterLevel) {
        tameable("");
        return byMonsterCode.values().stream()
                .collect(Collectors.toUnmodifiableMap(
                        TamingPort.Tameable::monsterCode,
                        t -> TamingRules.chancePercent(t.baseChancePercent(), t.monsterLevel(), characterLevel)));
    }

    /** Tra một bảng {@link #chancesFor} — hàm thuần, an toàn trên tick thread. */
    public static OptionalInt lookup(Map<String, Integer> chances, String monsterCode) {
        Integer chance = chances.get(monsterCode);
        return chance == null ? OptionalInt.empty() : OptionalInt.of(chance);
    }

    /** Cấp linh thú vào túi; {@code false} = túi đầy. */
    public boolean grantPet(long characterId, long petItemTemplateId, String monsterCode) {
        return port.grantPet(characterId, petItemTemplateId, monsterCode);
    }
}
