-- V35 — vùng an toàn (ADR-046 D3): bước vào thì hồi đầy máu.
--
-- BẢNG MỚI + MỘT HÀNG DỮ LIỆU. Không đổi bảng nào đang chạy.
--
-- ============================================================================================
-- VÌ SAO CẦN — số đo, không phải cảm giác
-- ============================================================================================
-- Trước ADR-046, cả arc 1 KHÔNG có một nguồn hồi máu nào (`grep -rn "regen\|heal"` trên
-- server-world/src/main ra 0 kết quả; kỹ năng `thuy_hoi_phuc` đòi hệ Thuỷ, tức từ Lv10 — đúng cái
-- mốc arc 1 kết thúc). Playtest client thật 2026-09-06: nhân vật chết BA lần trong ~15 phút, cả ba
-- vì đứng cạnh quái mà không có cách hồi máu.
--
-- Chủ dự án chọn CẢ BA hướng của dòng 17 bảng "chờ chủ dự án"; đây là hướng (c).
--
-- ============================================================================================
-- VÌ SAO Ở DB, KHÔNG PHẢI HẰNG SỐ JAVA
-- ============================================================================================
-- Điều 27 (Java = cơ chế, DB = tham số) và cùng đường mà ADR-037 (quái) / ADR-040 (NPC) đã đi.
-- Chủ nhân của bảng là module MỚI `feature-world` — chủ dự án chọn "module mới" thay vì nhét vào
-- `feature-monster` (vùng an toàn không liên quan gì tới quái). Module đó là chỗ ở tương lai của
-- bệ nhảy (`DEMO_PLATFORMS`, đang hardcode Java) và dải nền đất (đang hardcode ở CLIENT — nguồn
-- của bug quái đứng lơ lửng 2026-09-06).
--
-- ============================================================================================
-- VÌ SAO TÊN "safe_zone" CHỨ KHÔNG PHẢI "heal_zone"
-- ============================================================================================
-- Cùng vùng đó sau này là chỗ CẤM PvP (STORY §7 — tông môn là đất an toàn). ADR-046 chỉ thi hành
-- phần hồi máu. CỐ Ý chưa có cột "vùng này làm gì": mới có ĐÚNG một luật, và đoán trước luật thứ
-- hai là đoán sai — thêm `heals BOOLEAN` khi và chỉ khi có vùng thứ hai với luật khác.
--
-- ============================================================================================
-- BIÊN CỦA HÀNG DUY NHẤT
-- ============================================================================================
-- Tông môn quanh trưởng lão Chu Hoành (x = -5). Biên [-15, 5] trên trục X:
--   * Người chơi vào world ở x = 0 ⇒ ĐANG Ở TRONG vùng ngay từ lúc đăng nhập. Đó là ý: máu không
--     được lưu nên vào lại vốn đã đầy máu, và ai quay về làng thì hồi đầy.
--   * Điểm spawn quái gần nhất là mộc nhân @ x = 8 ⇒ cách biên đông (5) 3m, ngoài tầm đánh 2m.
--     Không có chỗ nào vừa đứng trong vùng an toàn vừa bị quái đánh.
--   * Thạch vệ gần nhất @ x = -20 ⇒ cách biên tây (-15) 5m.
-- Trục Y [-10, 10] rộng rãi: di chuyển khoá vào y=0 (ADR-033), nhưng vùng khai theo hình chữ nhật
-- để không phải đổi hình dạng khi bản đồ có tầng.
-- ============================================================================================
CREATE TABLE safe_zone (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    world_code VARCHAR(32) NOT NULL,
    min_x REAL NOT NULL,
    min_y REAL NOT NULL,
    max_x REAL NOT NULL,
    max_y REAL NOT NULL,
    CONSTRAINT safe_zone_bounds_not_inverted CHECK (min_x <= max_x AND min_y <= max_y)
);

CREATE INDEX idx_safe_zone_world_code ON safe_zone (world_code);

INSERT INTO safe_zone (code, world_code, min_x, min_y, max_x, max_y)
VALUES ('sect_grounds', 'main', -15, -10, 5, 10);
