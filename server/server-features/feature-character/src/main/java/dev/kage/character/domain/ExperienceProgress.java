package dev.kage.character.domain;

/**
 * Trạng thái kinh nghiệm của một nhân vật tại một thời điểm — đủ để vẽ vạch EXP mà client KHÔNG
 * phải biết đường cong ({@link ExperienceCurve} là luật chơi, thuộc về server — Điều 15).
 *
 * @param level cấp hiện tại, luôn ≥ 1.
 * @param totalExp tổng EXP tích luỹ từ đầu — khớp cột {@code character.exp}.
 * @param expIntoLevel đã đi được bao nhiêu trong cấp hiện tại.
 * @param expForNextLevel cần bao nhiêu để lên cấp kế; {@code 0} nghĩa là đã ở cấp tối đa.
 */
public record ExperienceProgress(int level, long totalExp, long expIntoLevel, long expForNextLevel) {}
