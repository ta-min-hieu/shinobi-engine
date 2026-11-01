-- ADR-145 (2026-11-01, lô 26) — Cổ Thạch Khổng Lồ (trận đóng arc 4, @408) thành BOSS world chính có kỹ năng: dậm đất
-- diện rộng có báo trước. Không đổi máu/ATK/giáp (biên của GoldenCoreBalanceTest giữ nguyên); chỉ gắn script + nhịp.
-- Hồi chiêu 10 giây, hỏi script mỗi 0,5 giây. Đề xuất cân bằng.
UPDATE monster_template
   SET is_boss = TRUE,
       ai_script_ref = '/scripts/boss/ancient_stone_colossus.js',
       ai_decision_interval_seconds = 0.5,
       ai_skill_cooldown_seconds = 10
 WHERE code = 'ancient_stone_colossus' AND NOT is_boss;
