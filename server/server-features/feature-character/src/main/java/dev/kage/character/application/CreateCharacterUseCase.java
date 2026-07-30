package dev.kage.character.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.character.domain.Character;
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

    public Character execute(long accountId, String name, String idempotencyKey) {
        Character.validateName(name);

        if (characterPort.countByAccount(accountId) >= MAX_CHARACTERS_PER_ACCOUNT) {
            throw new CharacterLimitExceededException(accountId, MAX_CHARACTERS_PER_ACCOUNT);
        }
        if (characterPort.existsByName(name)) {
            throw new DuplicateCharacterNameException(name);
        }

        NewCharacter newCharacter = new NewCharacter(accountId, name, Faction.HOA, idempotencyKey);
        AtomicReference<Character> holder = new AtomicReference<Character>();
        transactionRunner.inTransaction(tx -> holder.set(characterPort.create(tx, newCharacter)));
        return holder.get();
    }
}
