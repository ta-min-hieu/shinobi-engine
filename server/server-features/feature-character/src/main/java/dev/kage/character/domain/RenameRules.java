package dev.kage.character.domain;

import java.time.Duration;

/** Luật Dịch Danh Phù (ADR-148) — hằng số, không trạng thái. */
public final class RenameRules {

    /** Mã vật phẩm Dịch Danh Phù (V120). */
    public static final String TALISMAN_CODE = "rename_talisman";

    /** Khoảng cách tối thiểu giữa hai lần đổi tên của một nhân vật — chống né truy nã, đổi tên lừa đảo liên tục. */
    public static final Duration COOLDOWN = Duration.ofDays(7);

    private RenameRules() {}
}
