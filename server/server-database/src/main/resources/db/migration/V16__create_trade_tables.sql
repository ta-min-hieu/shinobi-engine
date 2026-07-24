-- V16 — trade (GAMEPLAY.md §8, Phase 7 lát cắt 3 — "hệ thống nguy hiểm nhất"). Quyết định
-- 2026-07-24 (cùng chủ dự án qua AskUserQuestion): KHÔNG khoá item trước (không có cột locked
-- trên inventory_item) — trade_session/trade_offer_item chỉ lưu Ý ĐỊNH trao đổi; toàn bộ xác
-- thực thật (còn sở hữu đủ item/gold không) chạy lại NGAY LÚC THI HÀNH (ConfirmTradeUseCase khi
-- cả 2 bên đã xác nhận), khoá dòng FOR UPDATE trong 1 transaction — nếu ai đã bán/gửi/trang bị
-- mất item đã chào giữa chừng, giao dịch thất bại SẠCH (không mất gì), không có duplicate item
-- nào có thể xảy ra dù không khoá trước. Xem feature-trade/CLAUDE.md.
--
-- Không có trạng thái terminal lưu lại (EXECUTED/CANCELLED) — xoá dòng khi xong, cùng quyết định
-- "reject/cancel = xoá dòng" đã dùng ở friendship/guild_join_request/party_invite.
CREATE TABLE trade_session (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_character_id BIGINT NOT NULL REFERENCES character (id),
    target_character_id BIGINT NOT NULL REFERENCES character (id),
    status VARCHAR(16) NOT NULL,
    requester_gold BIGINT NOT NULL DEFAULT 0,
    target_gold BIGINT NOT NULL DEFAULT 0,
    requester_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    target_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (requester_character_id <> target_character_id)
);

CREATE INDEX idx_trade_session_requester ON trade_session (requester_character_id);
CREATE INDEX idx_trade_session_target ON trade_session (target_character_id);

-- 1 dòng = 1 item trong lời chào của 1 bên (offering_character_id xác định bên nào, so với
-- trade_session.requester_character_id — không cần cột role riêng).
--
-- ⚠️ item_id KHÔNG có FK REFERENCES inventory_item(id) CÓ CHỦ ĐÍCH — item_id là tham chiếu MỀM,
-- xác thực lại lúc thi hành (không phải lúc chào). Nếu có FK, bán/gửi thư làm XOÁ dòng
-- inventory_item trong lúc còn đang được chào trong 1 trade sẽ bị Postgres CHẶN (vi phạm FK) —
-- chặn nhầm 1 thao tác hợp lệ (bán đồ) chỉ vì 1 trade session không liên quan đứng chờ. Để tham
-- chiếu mềm: item biến mất/đổi chủ giữa chừng chỉ khiến CHÍNH giao dịch trade đó thất bại sạch lúc
-- thi hành (ConfirmTradeUseCase phát hiện qua transferItem), không ảnh hưởng thao tác khác.
CREATE TABLE trade_offer_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES trade_session (id) ON DELETE CASCADE,
    offering_character_id BIGINT NOT NULL REFERENCES character (id),
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_trade_offer_item_session ON trade_offer_item (session_id);
