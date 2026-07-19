package dev.kage.account.domain;

/**
 * Port — DB-agnostic, không SQL/PostgreSQL ở đây (Điều 23). Implementation thật ({@code
 * PostgresAccountAdapter}) nằm ở server-database.
 */
public interface AccountPort {

    /**
     * Tìm account theo username, tạo mới nếu chưa tồn tại — một thao tác nguyên tử (tránh
     * TOCTOU giữa "tìm" và "tạo" khi đăng nhập đồng thời cùng username lần đầu).
     */
    Account findOrCreate(Transaction tx, String username);
}
