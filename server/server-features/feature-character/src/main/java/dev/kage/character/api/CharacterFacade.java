package dev.kage.character.api;

import java.util.List;
import java.util.Optional;

import dev.kage.character.application.CharacterUseCases;
import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.application.SelectFactionUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.PositionSnapshot;

/**
 * API công khai của {@code feature-character} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code character} trực tiếp (Điều 24).
 */
public final class CharacterFacade {

    private final CreateCharacterUseCase createCharacterUseCase;
    private final ListCharactersUseCase listCharactersUseCase;
    private final SelectCharacterUseCase selectCharacterUseCase;
    private final SelectFactionUseCase selectFactionUseCase;
    private final GrantExperienceUseCase grantExperienceUseCase;
    // loadPosition/savePosition không có nghiệp vụ nào ngoài delegation thuần (characterId đã
    // được validate từ session ở gateway, không cần UseCase riêng chỉ để gọi thẳng Port — 3
    // UseCase còn lại có validate/orchestrate thật, đây thì không).
    private final CharacterPort characterPort;

    /** Dựng ở composition root; {@code characterPort} cho các lời gọi đọc/ghi thuần không cần UseCase. */
    public CharacterFacade(CharacterUseCases useCases, CharacterPort characterPort) {
        this.createCharacterUseCase = useCases.createCharacterUseCase();
        this.listCharactersUseCase = useCases.listCharactersUseCase();
        this.selectCharacterUseCase = useCases.selectCharacterUseCase();
        this.selectFactionUseCase = useCases.selectFactionUseCase();
        this.grantExperienceUseCase = useCases.grantExperienceUseCase();
        this.characterPort = characterPort;
    }

    /**
     * Tạo nhân vật mới cho {@code accountId} — validate tên, trần số nhân vật mỗi tài khoản, idempotency
     * (ADR-025: gửi lại cùng {@code idempotencyKey} không tạo thêm).
     *
     * @throws dev.kage.character.application.CharacterLimitExceededException tài khoản đã đủ số nhân vật
     * @throws dev.kage.character.application.DuplicateCharacterNameException tên đã có người dùng
     */
    public Character createCharacter(long accountId, String name, String idempotencyKey) {
        return createCharacterUseCase.execute(accountId, name, idempotencyKey);
    }

    /** Mọi nhân vật của {@code accountId} — rỗng nếu chưa tạo nhân vật nào. */
    public List<Character> listCharacters(long accountId) {
        return listCharactersUseCase.execute(accountId);
    }

    /**
     * Nhân vật {@code characterId} — CHỈ khi nó thuộc {@code accountId} (chống IDOR, SECURITY.md §4).
     *
     * @throws dev.kage.character.application.CharacterNotFoundException không tồn tại HOẶC thuộc tài khoản khác
     *     (cùng một lỗi — fail secure)
     */
    public Character selectCharacter(long accountId, long characterId) {
        return selectCharacterUseCase.execute(accountId, characterId);
    }

    /** GDD §4 "chọn hệ ở Lv10" (Phase 8) — {@code characterId} gateway đã verify sở hữu qua session. */
    public Character selectFaction(long characterId, Faction faction) {
        return selectFactionUseCase.execute(characterId, faction);
    }

    /**
     * Cộng EXP sau khi hạ gục quái/nhận thưởng (2026-08-09) — trả về trạng thái vạch EXP mới để
     * gateway phát {@code ExperienceChangedEvent}.
     *
     * @param amount {@code <= 0} = chỉ ĐỌC trạng thái hiện tại (dùng lúc vào world, chưa có EXP nào).
     */
    public GrantExperienceUseCase.Result grantExperience(long characterId, long amount) {
        return grantExperienceUseCase.execute(characterId, amount);
    }

    /**
     * Vạch EXP sau khi thưởng quest đã cộng EXP bằng đường tắt — {@code leveledUp} báo đúng khi lần
     * lĩnh thưởng đó vượt mốc cấp. Xem {@link GrantExperienceUseCase#syncAfterExternalGrant}.
     */
    public GrantExperienceUseCase.Result syncExperienceAfterQuestReward(long characterId) {
        return grantExperienceUseCase.syncAfterExternalGrant(characterId);
    }

    /** Vị trí đã lưu lần cuối (SERVER.md §8) — điểm xuất hiện khi vào world. */
    public PositionSnapshot loadPosition(long characterId) {
        return characterPort.loadPosition(characterId);
    }

    /** Ghi đè vị trí đã lưu — gọi định kỳ và lúc thoát (SERVER.md §8), không mỗi tick. Blocking, chạm DB. */
    public void savePosition(long characterId, float x, float y) {
        characterPort.savePosition(characterId, x, y);
    }

    /** Chỉ số nền — server-world dẫn xuất ATK/DEF/... lúc gắn nhân vật vào world (GDD §5). */
    public CharacterStats loadStats(long characterId) {
        return characterPort.loadStats(characterId);
    }

    /**
     * Số dư vàng hiện tại — gateway đọc đúng một lần lúc nhân vật vào world để phát
     * {@code GoldChangedEvent} đầu tiên (ADR-035 Decision-3: "cửa hàng phải có số ngay, không phải
     * chờ giao dịch đầu tiên"). Mọi lần vàng ĐỔI sau đó tự phát từ hạ tầng, không qua đây.
     */
    public long loadGold(long characterId) {
        return characterPort.loadGold(characterId);
    }

    /**
     * Tra nhân vật theo tên — dùng cho kết bạn/gửi thư theo tên (feature-friend, feature-mail).
     *
     * <p>Tên không hợp lệ ({@link Character#isValidName}) trả rỗng NGAY, không chạm DB (sửa
     * 2026-10-02): tên đến thẳng từ packet của client ở chín đường (thư, chat riêng, kết bạn, mời
     * nhóm/bang, giao dịch, đấu trường...), và một tên chứa {@code U+0000} từng làm Postgres từ chối
     * câu SELECT — người chơi nhận 500 {@code error.server_internal} thay vì "không tìm thấy". Không
     * mất kết quả nào: mọi nhân vật đã lưu đều qua đúng luật này.
     *
     * <p>Không thấy nguyên văn thì thử dạng NFC (2026-10-14) — xem {@link Character#normalizeNewName}.
     * Vẫn PHÂN BIỆT hoa-thường: chỉ việc TẠO tên trùng hoa-thường bị chặn (V54).
     */
    public Optional<Character> findByName(String name) {
        // Nguyên văn trước, dạng NFC sau (2026-10-14, README dòng 42): tên mới lưu NFC, nên người gõ dấu
        // tách rời vẫn tìm thấy — mà tên cũ lưu dạng nào cũng không bị mất.
        if (Character.isValidName(name)) {
            Optional<Character> exact = characterPort.findByName(name);
            if (exact.isPresent()) {
                return exact;
            }
        }
        String normalized = Character.normalizeNewName(name);
        if (normalized == null || normalized.equals(name) || !Character.isValidName(normalized)) {
            return Optional.empty();
        }
        return characterPort.findByName(normalized);
    }

    /** Mọi nhân vật theo thứ hạng cảnh giới — cho Bia Anh Danh (ADR-057); gateway cache 60 s. */
    public List<Character> listAllByRealm() {
        return characterPort.listAllByRealm();
    }

    /** Tra nhân vật theo ID — dùng để hiển thị tên (danh sách bạn bè, người gửi thư/chat riêng). */
    public Optional<Character> findById(long characterId) {
        return characterPort.findById(characterId);
    }
}
