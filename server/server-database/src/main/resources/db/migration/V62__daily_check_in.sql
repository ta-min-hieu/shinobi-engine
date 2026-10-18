-- ADR-060 — điểm danh hằng ngày. Mỗi ngày (mốc 0h giờ Việt Nam, cùng mốc nhiệm vụ ngày ADR-055) lần vào
-- world đầu tiên nhận quà của ngày kế tiếp trong chu kỳ 7 ngày, gửi bằng THƯ HỆ THỐNG.
--
-- Bỏ một ngày KHÔNG phạt: chu kỳ đi theo số lần đã điểm danh, không theo lịch (Q2 theo khuyến nghị).
-- Quà là THAM SỐ (Điều 27): chỉnh bằng SQL, không sửa code. Số dưới đây là số TẠM — tổng 750 linh thạch
-- mỗi chu kỳ, ngang 7 ngày nhiệm vụ ngày (770).

CREATE TABLE daily_check_in_reward (
    day          SMALLINT    PRIMARY KEY CHECK (day BETWEEN 1 AND 7),
    gold_amount  BIGINT      NOT NULL CHECK (gold_amount >= 0),
    item_code    VARCHAR(32) REFERENCES item_template (code),
    quantity     INT         NOT NULL DEFAULT 0 CHECK (quantity BETWEEN 0 AND 99),
    CHECK ((item_code IS NULL) = (quantity = 0)),
    CHECK (gold_amount > 0 OR item_code IS NOT NULL)
);

INSERT INTO daily_check_in_reward (day, gold_amount, item_code, quantity) VALUES
    (1, 100, NULL, 0),
    (2, 0, 'minor_healing_pill', 2),
    (3, 150, NULL, 0),
    (4, 0, 'enhance_protection_charm', 1),
    (5, 200, NULL, 0),
    (6, 0, 'minor_healing_pill', 3),
    (7, 300, 'enhance_protection_charm', 1);

-- Một dòng mỗi nhân vật đã từng điểm danh. Chỉ đổi bằng compare-and-set (cả hai cột cũ trong WHERE) —
-- hai kết nối cùng vào world một lúc không thể cùng tiến chu kỳ.
CREATE TABLE daily_check_in (
    character_id     BIGINT      PRIMARY KEY REFERENCES character (id),
    last_day         SMALLINT    NOT NULL CHECK (last_day BETWEEN 1 AND 7),
    last_claim_date  DATE        NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
