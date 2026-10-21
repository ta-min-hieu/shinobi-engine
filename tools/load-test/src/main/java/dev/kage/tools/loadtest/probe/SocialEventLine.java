package dev.kage.tools.loadtest.probe;

import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeResponse;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.TradeClosedEvent;
import dev.kage.protocol.TradeRequestedEvent;

/**
 * Giải mã gói chat (801/802) và giao dịch (1401/1403/1412/1414) thành cột mô tả của dòng TSV — tách
 * khỏi {@link ProbeEventLine} vì lớp đó đã sát trần độ phức tạp của PMD (tính cho cả lớp).
 */
final class SocialEventLine {

    private SocialEventLine() {}

    /**
     * @return mô tả nếu {@code opcode} thuộc nhóm chat/giao dịch, rỗng nếu không
     * @throws InvalidProtocolBufferException nếu payload không khớp message của opcode đó
     */
    static Optional<String> describe(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + SendChatMessageResponse.parseFrom(payload)
                                .getOutcome()
                                .name());
            case Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE -> Optional.of(chat(ChatMessageEvent.parseFrom(payload)));
            case Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE -> {
                RequestTradeResponse response = RequestTradeResponse.parseFrom(payload);
                yield Optional.of("outcome=" + response.getOutcome().name() + " session=" + response.getSessionId());
            }
            case Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE ->
                Optional.of("outcome="
                        + AcceptTradeRequestResponse.parseFrom(payload)
                                .getOutcome()
                                .name());
            case Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE -> {
                TradeRequestedEvent event = TradeRequestedEvent.parseFrom(payload);
                yield Optional.of("session=" + event.getSessionId() + " from=" + event.getFromCharacterId() + " name="
                        + event.getFromName());
            }
            case Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE -> {
                TradeClosedEvent event = TradeClosedEvent.parseFrom(payload);
                yield Optional.of("session=" + event.getSessionId() + " reason="
                        + event.getReason().name());
            }
            default -> Optional.empty();
        };
    }

    /**
     * Thân tin nhắn in kèm SỐ ký tự xuống dòng — thứ cần đếm để kiểm luật ép một dòng; bản thân ký tự
     * xuống dòng được thay bằng {@code ⏎} để một dòng TSV vẫn là một dòng.
     */
    private static String chat(ChatMessageEvent event) {
        String body = event.getBody();
        long lineBreaks = body.chars().filter(ch -> ch == '\n' || ch == '\r').count();
        return "channel=" + event.getChannel().name() + " sender=" + event.getSenderId() + " name="
                + event.getSenderName() + " title='" + event.getSenderTitleKey() + "' lineBreaks=" + lineBreaks
                + " body='"
                + body.replace('\n', '⏎').replace('\r', '⏎') + "'";
    }
}
