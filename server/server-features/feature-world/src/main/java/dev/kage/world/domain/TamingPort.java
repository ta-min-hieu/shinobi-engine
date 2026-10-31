package dev.kage.world.domain;

import java.util.List;

/** Thu phục yêu thú (ADR-124, V104) — hiện thực ở server-database. */
public interface TamingPort {

    /**
     * Một loài thu phục được.
     *
     * @param monsterLevel cấp của loài ({@code monster_template.level}) — để tính chênh cấp
     */
    record Tameable(
            String monsterCode, int monsterLevel, long petItemTemplateId, String petItemCode, int baseChancePercent) {}

    /** Mọi loài thu phục được — đọc một lần lúc khởi động. */
    List<Tameable> tameables();

    /** Cấp linh thú vừa thu phục vào túi + audit, MỘT giao dịch. {@code false} = túi đầy, không đổi gì. */
    boolean grantPet(long characterId, long petItemTemplateId, String monsterCode);
}
