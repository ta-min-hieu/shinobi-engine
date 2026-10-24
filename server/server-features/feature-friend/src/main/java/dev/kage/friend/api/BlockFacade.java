package dev.kage.friend.api;

import java.util.List;

import dev.kage.friend.domain.BlockOutcome;
import dev.kage.friend.domain.BlockPolicy;
import dev.kage.friend.domain.BlockPort;

/**
 * API chặn người chơi (ADR-091). Chạm DB — chỉ gọi trên Virtual Thread. Luật "bị chặn thì không tương tác được" do
 * gateway thi hành ở từng lệnh (chat riêng, mời bạn/nhóm/giao dịch, thách đấu, tặng quà, sư đồ, đạo lữ) qua
 * {@link #eitherBlocks}.
 */
public final class BlockFacade {

    /** Composition không có danh sách chặn (test) — không ai chặn ai. */
    public static final BlockFacade DISABLED = new BlockFacade(new BlockPort() {
        @Override
        public BlockOutcome block(long blockerId, long blockedId, int maxBlocked) {
            return BlockOutcome.LIST_FULL;
        }

        @Override
        public BlockOutcome unblock(long blockerId, long blockedId) {
            return BlockOutcome.NOT_BLOCKED;
        }

        @Override
        public List<Long> blockedBy(long blockerId) {
            return List.of();
        }

        @Override
        public boolean eitherBlocks(long firstId, long secondId) {
            return false;
        }
    });

    private final BlockPort port;

    /** Dựng ở composition root. */
    public BlockFacade(BlockPort port) {
        this.port = port;
    }

    /** Chặn — {@code blockerId} từ session. */
    public BlockOutcome block(long blockerId, long blockedId) {
        if (blockerId == blockedId) {
            return BlockOutcome.CANNOT_BLOCK_SELF;
        }
        return port.block(blockerId, blockedId, BlockPolicy.MAX_BLOCKED);
    }

    /** Bỏ chặn. */
    public BlockOutcome unblock(long blockerId, long blockedId) {
        return port.unblock(blockerId, blockedId);
    }

    /** Những người {@code blockerId} đang chặn. */
    public List<Long> blockedBy(long blockerId) {
        return port.blockedBy(blockerId);
    }

    /** Một trong hai đã chặn người kia — khi đó mọi lời mời/tin riêng giữa họ bị từ chối. */
    public boolean eitherBlocks(long firstId, long secondId) {
        return firstId != secondId && port.eitherBlocks(firstId, secondId);
    }
}
