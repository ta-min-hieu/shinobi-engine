package dev.kage.character.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterNameTakenException;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.TransactionRunner;

/** Tạo nhân vật mới cho account (GAMEPLAY.md §2) — validate tên, giới hạn số nhân vật, idempotency. */
public final class CreateCharacterUseCase {

    // Tham số triển khai, không phải quyết định kiến trúc — chỉnh được sau, không cần ADR.
    static final int MAX_CHARACTERS_PER_ACCOUNT = 4;

    private final CharacterPort characterPort;
    private final TransactionRunner transactionRunner;

    public CreateCharacterUseCase(CharacterPort characterPort, TransactionRunner transactionRunner) {
        this.characterPort = characterPort;
        this.transactionRunner = transactionRunner;
    }

    public Character execute(long accountId, String requestedName, String idempotencyKey) {
        String name = Character.normalizeNewName(requestedName);
        Character.validateNewName(name);

        // Kiểm sớm, KHÔNG khoá — chỉ để account đã đầy khỏi mở transaction. Phép kiểm quyết định là
        // phép đếm có khoá bên trong transaction phía dưới.
        if (characterPort.countByAccount(accountId) >= MAX_CHARACTERS_PER_ACCOUNT) {
            throw new CharacterLimitExceededException(accountId, MAX_CHARACTERS_PER_ACCOUNT);
        }
        if (characterPort.existsByName(name)) {
            throw new DuplicateCharacterNameException(name);
        }

        NewCharacter newCharacter = new NewCharacter(accountId, name, Faction.HOA, idempotencyKey);
        AtomicReference<Character> holder = new AtomicReference<Character>();
        try {
            transactionRunner.inTransaction(tx -> {
                // Đếm lại SAU khi khoá account, trong CÙNG transaction với INSERT: các lượt tạo đồng
                // thời trên cùng account xếp hàng ở đây (race vượt giới hạn, vá 2026-09-25). Ném ra
                // ⇒ transaction rollback, chưa ghi gì (khoá là thao tác đầu tiên).
                if (characterPort.lockAccountAndCountCharacters(tx, accountId) >= MAX_CHARACTERS_PER_ACCOUNT) {
                    throw new CharacterLimitExceededException(accountId, MAX_CHARACTERS_PER_ACCOUNT);
                }
                holder.set(characterPort.create(tx, newCharacter));
            });
        } catch (CharacterNameTakenException e) {
            // existsByName ở trên chỉ là kiểm sớm, không nguyên tử với INSERT: người khác có thể lấy
            // đúng tên này ngay giữa hai bước. Báo cùng một lỗi như đường thường.
            throw new DuplicateCharacterNameException(name, e);
        }
        return holder.get();
    }
}
