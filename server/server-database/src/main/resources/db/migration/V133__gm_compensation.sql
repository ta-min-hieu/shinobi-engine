-- ADR-165 (2026-11-03) — GM gửi thư bồi thường hàng loạt. Một dòng mỗi đợt: phần quà + cấp tối thiểu, ai tạo, lúc xong.
-- Thư từng người mang khoá chống trùng TẤT ĐỊNH (UUID từ mã đợt + id nhân vật) nên chạy lại một đợt dang dở không gửi
-- trùng; đợt đã xong (completed_at) thì không chạy lại — khoá chống trùng cũ có thể đã bị dọn.
CREATE TABLE gm_compensation (
    code             VARCHAR(32) PRIMARY KEY,
    gold             BIGINT      NOT NULL CHECK (gold >= 0),
    item_template_id BIGINT      REFERENCES item_template (id),
    quantity         INT         NOT NULL CHECK (quantity >= 0),
    min_level        INT         NOT NULL CHECK (min_level >= 1),
    created_by       BIGINT      NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    completed_at     TIMESTAMPTZ,
    sent             INT         NOT NULL DEFAULT 0,
    CHECK (gold > 0 OR (item_template_id IS NOT NULL AND quantity > 0))
);
