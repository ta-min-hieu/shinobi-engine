-- ADR-193 — tên riêng của linh thú (NULL = dùng tên loài). Theo dòng túi đồ như cấp/bậc, nên đi theo linh thú khi
-- tháo ra / mặc lại; dòng túi bị xoá thì tên đi theo (ON DELETE CASCADE của V71).
ALTER TABLE pet_state ADD COLUMN custom_name VARCHAR(12);
