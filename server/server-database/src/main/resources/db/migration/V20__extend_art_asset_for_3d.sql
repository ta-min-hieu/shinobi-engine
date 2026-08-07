-- V20 — ART-PIPELINE.md §8.6: mở bảng art_asset cho nhánh 3D (ADR-031 Accepted 2026-08-03).
--
-- Vì sao cần: bộ cột của V19 giả định sản phẩm giao là sprite nằm trong atlas (atlas_name,
-- master_px). Nhân vật · quái · NPC sau ADR-031 giao file glTF ở assets/exported/models/{low,med,
-- high}/ nên KHÔNG có atlas nào để trỏ tới — thiếu 3 cột dưới đây thì luật §8.5 "không có asset mồ
-- côi" hở với MỌI file .glb: file nằm trên đĩa mà không truy ra được dòng DB tương ứng.
--
-- Vì sao thêm cột vào art_asset chứ không tách bảng art_model (lý lẽ đầy đủ ở §8.6): nhánh 3D có
-- đúng cùng hình dạng bài toán với atlas — một cái tên + một quy ước sinh ra 3 đường dẫn tier — và
-- trang quản trị liệt kê CẢ KHO trong một lưới, nên tách bảng chỉ biến mọi truy vấn danh sách thành
-- LEFT JOIN để lấy thêm 1-2 cột.

-- asset_kind quyết định nhóm cột nào có nghĩa, và pipeline soi thư mục nào khi kiểm asset mồ côi.
-- DEFAULT 'SPRITE' để không phải backfill: mọi dòng đang có trong bảng đều là asset 2D, và chúng
-- vẫn thoả ràng buộc bên dưới vì hai cột mới sinh ra là NULL.
--
-- model_name đóng đúng vai của atlas_name ở nhánh 2D: chỉ lưu CÁI TÊN, đường dẫn phát hành từng
-- tier SUY RA theo quy ước models/{low|med|high}/{model_name}.glb (§8.5) — đổi tier hay đổi origin
-- không phải cập nhật hàng loạt dòng DB.
--
-- tri_counts là {"low":5107,"med":10107,"high":20107}: số tam giác THẬT của từng tier. Ba tier của
-- một model khác nhau ở con số chứ không khác ở pixel (§8.8), nên đây là dữ liệu duy nhất cho phép
-- trang quản trị so 3 tier — đặt cạnh nhau 3 ảnh là dùng sai công cụ.
ALTER TABLE art_asset
    ADD COLUMN asset_kind VARCHAR(8) NOT NULL DEFAULT 'SPRITE',
    ADD COLUMN model_name VARCHAR(64),
    ADD COLUMN tri_counts JSONB;

-- Chặn lẫn nhánh ngay lúc INSERT thay vì trông vào kỷ luật của người ghi. Đây chính là thứ mà
-- phương án "tách bảng art_model" mua được (không thể điền nhầm cột chéo nhánh), với giá 4 dòng SQL
-- và không thêm bảng nào.
--
-- Không cần liệt kê riêng danh sách giá trị hợp lệ của asset_kind: giá trị thứ ba bất kỳ làm CẢ HAI
-- vế sai nên bị chính ràng buộc này bác.
ALTER TABLE art_asset
    ADD CONSTRAINT art_asset_kind_branch CHECK (
        (asset_kind = 'SPRITE' AND model_name IS NULL AND tri_counts IS NULL)
     OR (asset_kind = 'MODEL' AND atlas_name IS NULL AND master_px IS NULL));
