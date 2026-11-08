package dev.kage.monster.api;

import java.util.Locale;
import java.util.Optional;

import dev.kage.monster.domain.MonsterInfoPort;

/** API xem thông tin quái (ADR-255). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class MonsterInfoFacade {

    /** Mã quái dài nhất nhận vào (đúng cột {@code monster_template.code}). */
    public static final int MAX_CODE = 32;

    /** Không có quái nào (composition test). */
    public static final MonsterInfoFacade DISABLED = new MonsterInfoFacade(code -> Optional.empty());

    private final MonsterInfoPort port;

    /** Dựng ở composition root. */
    public MonsterInfoFacade(MonsterInfoPort port) {
        this.port = port;
    }

    /** Thông tin theo mã (cắt khoảng trắng, chữ thường); mã rỗng / quá dài / có ký tự NUL ⇒ rỗng. */
    public Optional<MonsterInfoPort.Profile> info(String code) {
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_CODE || normalized.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        return port.info(normalized);
    }
}
