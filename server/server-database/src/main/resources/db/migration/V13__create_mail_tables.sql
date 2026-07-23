-- V13 — thư (GAMEPLAY.md §11, Phase 6 lát cắt 5). sender_id CHO PHÉP NULL = thư hệ thống (quest
-- reward tồn đọng, GM đền bù) — không dùng sentinel số (0/-1, dễ nhầm ID thật, xem feature-mail/
-- CLAUDE.md).
--
-- mail_item TÁCH riêng (không lưu mảng/JSON trong cột mail) — cùng lý do inventory_item tách khỏi
-- character (V4): mỗi dòng đính kèm là 1 loại item + số lượng, dễ SELECT/INSERT bằng SQL chuẩn.
CREATE TABLE mail (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id BIGINT REFERENCES character (id),
    recipient_id BIGINT NOT NULL REFERENCES character (id),
    subject VARCHAR(64) NOT NULL,
    body VARCHAR(1024) NOT NULL,
    gold_amount BIGINT NOT NULL DEFAULT 0 CHECK (gold_amount >= 0),
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mail_recipient_id ON mail (recipient_id);

CREATE TABLE mail_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mail_id BIGINT NOT NULL REFERENCES mail (id),
    item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    quantity INT NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_mail_item_mail_id ON mail_item (mail_id);
