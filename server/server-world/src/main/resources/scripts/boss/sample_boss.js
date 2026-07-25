// sample_boss.js — Boss AI, nội dung pha/chiêu THẬT nằm ở ĐÂY (Điều 27/ADR-016), KHÔNG hardcode
// Java. Java (BossAiSystem/CombatConstants.resolveSkillAttack) chỉ lo cơ chế dùng chung: cooldown,
// miss/crit/kháng phép (RES) — script chỉ quyết định HÀNH ĐỘNG và damage thô (GDD §6, cùng pipeline
// fireball.js dùng cho skill người chơi).
//
// ctx là facade dữ liệu thuần (SCRIPTING.md §4-5, ProxyObject.fromMap) — KHÔNG có object Java nào
// lộ ra đây:
//   ctx.hp_percent          % máu hiện tại của CHÍNH boss (0.0 - 1.0)
//   ctx.already_enraged     đã kích hoạt enrage lần nào chưa (chỉ kích hoạt 1 lần/trận)
//   ctx.heavy_strike_ready  chiêu heavy_strike đã hết cooldown chưa
//   ctx.base_atk            ATK nền của boss — dùng làm cơ sở tính damage chiêu đặc biệt
//
// Trả về OBJECT (không phải scalar) — { action, raw_damage }. action là 1 trong 3:
//   "enrage"        — pha 3 (HP < 30%), chỉ kích hoạt 1 LẦN, damage lớn nhất trận
//   "heavy_strike"  — pha 2 (còn cooldown xong), damage lớn hơn đánh thường
//   "basic_attack"  — không làm gì thêm, để CombatSystem tự đánh thường như quái bình thường
(function () {
    if (ctx.hp_percent < 0.3 && !ctx.already_enraged) {
        return { action: "enrage", raw_damage: ctx.base_atk * 2.0 };
    }
    if (ctx.hp_percent < 0.7 && ctx.heavy_strike_ready) {
        var multiplier = ctx.already_enraged ? 2.5 : 1.8;
        return { action: "heavy_strike", raw_damage: ctx.base_atk * multiplier };
    }
    return { action: "basic_attack", raw_damage: 0 };
})()
