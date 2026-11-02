-- V120 — Dịch Danh Phù (ADR-148, lô 27). Một phù đổi tên nhân vật một lần; hai lần đổi cách nhau ít nhất 7 ngày
-- (RenameRules.COOLDOWN). Tên mới qua đúng luật tên lúc tạo (NFC, không ký tự vô hình, không trộn Latin/Cyrillic) và
-- UNIQUE lower(name) của V54. Giá là PLACEHOLDER (Điều 27).

INSERT INTO item_template (code, name_key, max_stack)
SELECT 'rename_talisman', 'item.rename_talisman.name', 5
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'rename_talisman');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 20000, 2000 FROM item_template WHERE code = 'rename_talisman'
   AND NOT EXISTS (SELECT 1 FROM shop_item si JOIN item_template it ON it.id = si.item_template_id
                    WHERE it.code = 'rename_talisman');

-- Lịch sử tên: dòng mới nhất của một nhân vật quyết hồi chiêu; GM tra "người này trước tên gì" bằng bảng này.
CREATE TABLE character_name_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT      NOT NULL REFERENCES character (id),
    old_name     VARCHAR(16) NOT NULL,
    new_name     VARCHAR(16) NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX character_name_history_by_character ON character_name_history (character_id, changed_at);
