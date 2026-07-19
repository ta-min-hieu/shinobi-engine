package dev.kage.character.domain;

import java.util.List;

/**
 * Port — DB-agnostic, không SQL/PostgreSQL ở đây (Điều 23). Implementation thật ({@code
 * PostgresCharacterAdapter}) nằm ở server-database.
 */
public interface CharacterPort {

    List<Character> listByAccount(long accountId);

    int countByAccount(long accountId);

    /** Tên đã trùng (bất kỳ account nào — UNIQUE toàn server, DATABASE.md §2.1) → Optional rỗng. */
    boolean existsByName(String name);

    /**
     * Tạo nhân vật mới + ghi {@code idempotencyKey} trong CÙNG transaction (ADR-025) — key đã dùng
     * trước đó phải khiến adapter ném lỗi (unique constraint), KHÔNG tạo nhân vật lần 2.
     */
    Character create(Transaction tx, NewCharacter newCharacter);
}
