package dev.kage.trade.application;

/**
 * Ném để buộc rollback TOÀN BỘ transaction thi hành khi xác thực lại phát hiện lời chào không còn
 * hợp lệ (item đã mất/đổi chủ, không đủ gold) — rollback bao gồm CẢ cờ {@code confirmed} vừa set
 * trong CÙNG transaction, buộc 2 bên phải confirm lại sau khi biết lý do (package-private, rollback
 * nội bộ, cùng tinh thần {@code ShopTransactionAbortedException}/{@code MailTransactionAbortedException}).
 */
final class TradeExecutionAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
}
