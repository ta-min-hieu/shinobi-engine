package dev.kage.server.world;

/**
 * Máu của một entity vừa đổi mà KHÔNG do đòn đánh nào (ADR-046 D1) — kết quả tick, {@code
 * WorldFacade} drain rồi phát cho mọi người đang thấy entity đó trong AoI.
 *
 * <p>Dùng chung cho cả BỐN nguồn của ADR-046: hồi máu ngoài chiến đấu ({@link HealthRegenSystem}),
 * vùng an toàn (D3), quái về tới điểm spawn ({@code AiSystem}, D4), và đan dược (D5). Một record
 * chứ không phải bốn: phía nhận chỉ cần "entity nào, còn bao nhiêu máu" — lý do là thứ vạch máu
 * không quan tâm.
 *
 * @param entityId ID nghiệp vụ: DƯƠNG = {@code characterId}, ÂM = {@code monsterId} (combat.proto)
 */
record HealthChange(long entityId, int currentHp, int maxHp) {}
