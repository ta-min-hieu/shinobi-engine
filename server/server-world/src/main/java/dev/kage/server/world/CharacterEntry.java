package dev.kage.server.world;

/**
 * Một nhân vật ĐANG VÀO world: danh tính, chỗ đứng, chỉ số nền và chỉ số trang bị — đi cùng nhau
 * vì chúng phải tới world trong CÙNG một lệnh.
 *
 * <p><b>Vì sao là record chứ không phải 5 tham số rời.</b> Hai lý do, và lý do thứ hai mới là lý do
 * thật: (1) Checkstyle {@code ParameterNumber} chặn ở 4 tham số — đếm lùi bằng cách bỏ bớt thông tin
 * là chữa triệu chứng; (2) trước 2026-09-18 chỉ số trang bị đi bằng một lệnh RIÊNG gửi sau
 * {@code attachCharacter}, và khoảng hở giữa hai lệnh là một tick mà entity đã ở trong world với chỉ
 * số NỀN — đo thật: đăng nhập cạnh quái ăn 37 sát thương (giáp chưa mặc) trên thanh máu 100 thay vì
 * 27 trên thanh 206. Gói chúng vào một record là cách làm cho "quên gửi bonus" trở thành chuyện
 * KHÔNG diễn đạt được, thay vì một lỗi phải nhớ mà tránh.
 *
 * @param bonus {@link EquipmentBonusSnapshot#NONE} nếu chỗ gọi không có trang bị (test, và các đường
 *     arena/dungeon chưa nối — xem ghi chú tại chỗ gọi ở hai registry đó)
 */
public record CharacterEntry(
        long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats, EquipmentBonusSnapshot bonus) {

    /** Vào world KHÔNG kèm chỉ số trang bị — test, và các đường chưa nối được nguồn bonus. */
    public static CharacterEntry withoutEquipment(
            long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats) {
        return new CharacterEntry(characterId, spawnX, spawnY, baseStats, EquipmentBonusSnapshot.NONE);
    }
}
