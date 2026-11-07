package dev.kage.mail.api;

import java.util.function.LongPredicate;

import dev.kage.mail.domain.StrangerMailPort;

/**
 * API chặn thư người lạ (ADR-212). Chỉ chặn thư NGƯỜI CHƠI gửi qua lệnh gửi thư; thư hệ thống (thưởng, GM, quà bạn bè)
 * không đi qua đây nên luôn tới.
 */
public final class StrangerMailFacade {

    /** Không ai chặn (composition test). */
    public static final StrangerMailFacade DISABLED = new StrangerMailFacade(new StrangerMailPort() {
        @Override
        public boolean blocksStrangers(long characterId) {
            return false;
        }

        @Override
        public void setBlocksStrangers(long characterId, boolean enabled) {
            // không lưu gì
        }
    });

    private final StrangerMailPort port;

    /** Dựng ở composition root. */
    public StrangerMailFacade(StrangerMailPort port) {
        this.port = port;
    }

    /** {@code characterId} có đang bật chặn thư người lạ không. */
    public boolean blocksStrangers(long characterId) {
        return port.blocksStrangers(characterId);
    }

    /** Bật / tắt chặn thư người lạ. */
    public void setBlocksStrangers(long characterId, boolean enabled) {
        port.setBlocksStrangers(characterId, enabled);
    }

    /**
     * {@code recipientId} từ chối thư của {@code senderId}: người nhận đang chặn người lạ VÀ người gửi không phải bạn
     * bè. {@code isFriendOfRecipient} chỉ được hỏi khi người nhận đang bật chặn.
     */
    public boolean refuses(long recipientId, long senderId, LongPredicate isFriendOfRecipient) {
        return senderId != recipientId && port.blocksStrangers(recipientId) && !isFriendOfRecipient.test(senderId);
    }
}
