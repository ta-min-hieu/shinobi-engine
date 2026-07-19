package dev.kage.character.domain;

import java.util.Objects;

/**
 * Value Object gom tham số tạo nhân vật — tránh {@link CharacterPort#create} vượt quá 4 tham số
 * (CODING-STANDARDS §4, Checkstyle {@code ParameterNumber}).
 */
public record NewCharacter(long accountId, String name, Faction faction, String idempotencyKey) {

    public NewCharacter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
