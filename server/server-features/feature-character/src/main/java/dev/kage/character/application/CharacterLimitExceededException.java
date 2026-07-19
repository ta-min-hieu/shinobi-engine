package dev.kage.character.application;

/** Account đã đạt giới hạn số nhân vật ({@link CreateCharacterUseCase#MAX_CHARACTERS_PER_ACCOUNT}). */
public final class CharacterLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CharacterLimitExceededException(long accountId, int limit) {
        super("account " + accountId + " đã đạt giới hạn " + limit + " nhân vật");
    }
}
