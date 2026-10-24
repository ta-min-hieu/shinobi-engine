package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.BlockFacade;
import dev.kage.friend.api.DaoLuFacade;
import dev.kage.friend.domain.BlockOutcome;
import dev.kage.protocol.BlockCharacterOutcome;
import dev.kage.protocol.BlockCharacterResponse;
import dev.kage.protocol.UnblockCharacterOutcome;
import dev.kage.protocol.UnblockCharacterResponse;

/**
 * Chặn người chơi (ADR-091) — lệnh chặn/bỏ chặn, và phép kiểm {@link #requireNotBlocked} mà mọi lệnh "chạm tới
 * người khác" gọi trước khi làm gì. Chạy trên Virtual Thread (chạm DB).
 */
final class BlockCommands {

    /** Mã HTTP-giống của từ chối "đã chặn nhau" — cùng họ với 409/429 các từ chối nghiệp vụ khác. */
    static final int BLOCKED_CODE = 403;

    /** Khoá i18n client hiện khi lệnh bị từ chối vì một trong hai đã chặn người kia. */
    static final String BLOCKED_KEY = "error.blocked";

    private static final Map<BlockOutcome, BlockCharacterOutcome> BLOCK_OUTCOMES = new EnumMap<>(BlockOutcome.class);

    static {
        BLOCK_OUTCOMES.put(BlockOutcome.OK, BlockCharacterOutcome.BLOCK_CHARACTER_OUTCOME_OK);
        BLOCK_OUTCOMES.put(
                BlockOutcome.CANNOT_BLOCK_SELF, BlockCharacterOutcome.BLOCK_CHARACTER_OUTCOME_CANNOT_BLOCK_SELF);
        BLOCK_OUTCOMES.put(BlockOutcome.ALREADY_BLOCKED, BlockCharacterOutcome.BLOCK_CHARACTER_OUTCOME_ALREADY_BLOCKED);
        BLOCK_OUTCOMES.put(BlockOutcome.LIST_FULL, BlockCharacterOutcome.BLOCK_CHARACTER_OUTCOME_LIST_FULL);
        // Không bao giờ ra từ lệnh chặn — giữ bảng đủ mọi giá trị.
        BLOCK_OUTCOMES.put(BlockOutcome.NOT_BLOCKED, BlockCharacterOutcome.BLOCK_CHARACTER_OUTCOME_UNSPECIFIED);
    }

    private final BlockFacade blocks;
    private final DaoLuFacade daoLu;
    private final CharacterFacade characters;

    BlockCommands(BlockFacade blocks, DaoLuFacade daoLu, CharacterFacade characters) {
        this.blocks = blocks;
        this.daoLu = daoLu;
        this.characters = characters;
    }

    BlockCharacterResponse block(long blockerId, String targetName) {
        Optional<Character> target = characters.findByName(targetName.strip());
        if (target.isEmpty()) {
            return BlockCharacterResponse.newBuilder()
                    .setOutcome(BlockCharacterOutcome.BLOCK_CHARACTER_OUTCOME_TARGET_NOT_FOUND)
                    .build();
        }
        BlockOutcome outcome = blocks.block(blockerId, target.get().id());
        if (outcome == BlockOutcome.OK) {
            // Lần chặn có thể đã xoá dòng đạo lữ — bộ nhớ đệm đạo lữ của hai người không còn đúng.
            daoLu.forget(blockerId, target.get().id());
        }
        return BlockCharacterResponse.newBuilder()
                .setOutcome(BLOCK_OUTCOMES.get(outcome))
                .build();
    }

    UnblockCharacterResponse unblock(long blockerId, String targetName) {
        Optional<Character> target = characters.findByName(targetName.strip());
        boolean unblocked =
                target.isPresent() && blocks.unblock(blockerId, target.get().id()) == BlockOutcome.OK;
        return UnblockCharacterResponse.newBuilder()
                .setOutcome(
                        unblocked
                                ? UnblockCharacterOutcome.UNBLOCK_CHARACTER_OUTCOME_OK
                                : UnblockCharacterOutcome.UNBLOCK_CHARACTER_OUTCOME_NOT_BLOCKED)
                .build();
    }

    /** Một trong hai đã chặn người kia. */
    boolean eitherBlocks(long firstId, long secondId) {
        return blocks.eitherBlocks(firstId, secondId);
    }

    /** Ném {@link CommandRefusedException} ({@value #BLOCKED_KEY}) nếu một trong hai đã chặn người kia. */
    void requireNotBlocked(long firstId, long secondId) {
        if (eitherBlocks(firstId, secondId)) {
            throw new CommandRefusedException(BLOCKED_CODE, BLOCKED_KEY);
        }
    }
}
