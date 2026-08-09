package dev.kage.client.core.world;

/**
 * Một thứ đứng ở đâu đó trong thế giới và click chuột vào được — quái, NPC.
 *
 * <p>Tồn tại để {@link WorldEntityPicker} không phải biết loại thực thể nào cả. Trước 2026-08-09 nó
 * nhận thẳng {@code CombatHudController.MonsterState}, và điều đó khoá phép bắt click vào đúng một
 * loại: thêm NPC là phải chép lại toàn bộ phép chiếu + hộp bao + phân xử chồng lấn lần thứ hai, rồi
 * hai bản sao đó sẽ lệch nhau ở lần chỉnh vùng bắt kế tiếp.
 *
 * <p>Chỉ có toạ độ mặt đất: chiều cao KHÔNG nằm ở đây vì nó không phải thuộc tính của thực thể mà là
 * của thứ đang được VẼ cho nó (model 1.0 unit hay hộp thay thế cao hơn), và người bắt click cần một
 * vùng bắt ổn định chứ không phải một vùng nhảy kích thước ngay khi asset về.
 */
interface PickablePoint {

    float x();

    float y();
}
