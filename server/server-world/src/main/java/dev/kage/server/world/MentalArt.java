package dev.kage.server.world;

/**
 * Một tâm pháp bị động học được bằng điểm công pháp (ADR-077) — mỗi bậc cộng PHẲNG chừng này chỉ số.
 *
 * @param code mã tâm pháp (cũng là khoá i18n {@code skill.<code>.name}/{@code .desc} ở client)
 * @param faction linh căn sở hữu
 * @param requiredLevel cấp mở tâm pháp
 * @param perRank chỉ số cộng thêm cho MỖI bậc (bậc 0 = chưa học, không cộng gì)
 */
public record MentalArt(String code, Element faction, int requiredLevel, EquipmentBonusSnapshot perRank) {}
