// blood_sand_enforcer.js — AI của boss THỨ HAI: Hình Vân Sơn, chấp sự Hình Đường (thật ra là hộ
// pháp Huyết Sa cải trang). Nội dung pha/chiêu nằm ở ĐÂY (Điều 27/ADR-016), Java chỉ lo cơ chế:
// throttle, cooldown, miss/crit/kháng phép (cùng pipeline `CombatConstants.resolveSkillAttack`).
//
// STORY: arc-2 §6 — CAO TRÀO. Khai theo STORY.md §8 quy tắc 1.
//
// ctx giống hệt sample_boss.js (facade dữ liệu thuần, SCRIPTING.md §4-5):
//   ctx.hp_percent          % máu hiện tại của chính hắn (0.0 - 1.0)
//   ctx.already_enraged     đã lộ nguyên hình chưa (chỉ một lần/trận)
//   ctx.heavy_strike_ready  chiêu đã hết cooldown chưa
//   ctx.base_atk            ATK nền — cơ sở tính damage chiêu
//
// Trả OBJECT { action, raw_damage }; action là "enrage" | "heavy_strike" | "basic_attack".
//
// =============================================================================================
// HAI PHA, VÀ CHÚNG KỂ ĐÚNG THỨ CỐT TRUYỆN NÓI
// =============================================================================================
// §6 arc 2: *"hộ pháp Huyết Sa cải trang làm chấp sự tông môn — kẻ đứng sau các vụ mất tích. Đánh
// bại, nhưng hắn kịp tự tan tu vi, manh mối đứt."* Hai câu đó dịch thẳng thành hai pha:
//
//   · **Còn cải trang** (máu > 55%): hắn đánh như một chấp sự biết võ — chiêu là "trượng pháp Hình
//     Đường", mạnh vừa (×1.6). Hắn KHÔNG dùng công phu Huyết Sa vì làm thế là tự khai giữa tông môn.
//   · **Lộ nguyên hình** (máu < 55%, một lần duy nhất): không còn gì để giấu ⇒ ×2.2, và từ đó chiêu
//     cũng mạnh hơn (×2.0).
//
// ⚠️ Ngưỡng 55% chứ không phải 30% như `sample_boss`: con kia là máy móc đánh tới chết nên pha cuối
// để sát đáy; con này phải LỘ MẶT đủ sớm để người chơi kịp thấy trận đấu đổi tính chất — nếu đặt
// 30% thì với 150 máu, cú lộ mặt rơi vào đúng hai nhát cuối và người chơi chỉ thấy một con số to
// hơn, không thấy một khúc ngoặt.
//
// ⚠️ **MỌI CON SỐ Ở ĐÂY LÀ TRÊN GIẤY CHO TỚI KHI PLAYTEST.** Quest 8 đã bác giấy hai lần trong cùng
// một ngày (quãng đường tốn máu; và quái ra đòn trước hai nhát vì aggro 6m > tầm đánh 2m). Trận này
// khác ở chỗ hắn đứng TRONG vùng an toàn nên người chơi tới với máu đầy và không bị phủ đầu — nhưng
// đó cũng là một giả định, và giả định thì phải đo.
(function () {
    if (ctx.hp_percent < 0.55 && !ctx.already_enraged) {
        return { action: "enrage", raw_damage: ctx.base_atk * 2.2 };
    }
    if (ctx.heavy_strike_ready) {
        var multiplier = ctx.already_enraged ? 2.0 : 1.6;
        return { action: "heavy_strike", raw_damage: ctx.base_atk * multiplier };
    }
    return { action: "basic_attack", raw_damage: 0 };
})()
