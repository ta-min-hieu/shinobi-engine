package dev.kage.gm.domain;

/**
 * Một lệnh GM ĐÃ parse (ADR-052 D4) — whitelist cố định, mỗi lệnh một record (D8: không reflection,
 * không tra lệnh theo tên lớp). Lệnh có mục tiêu mang TÊN nhân vật; gateway tự tra ra id.
 */
public sealed interface GmCommand {

    /** Tên lệnh như người gõ (ghi audit). */
    String name();

    /** Cấp tối thiểu được gọi lệnh này. */
    GmRole requiredRole();

    /** Tên nhân vật mục tiêu, rỗng nếu lệnh không có mục tiêu. */
    default String targetName() {
        return "";
    }

    record Help() implements GmCommand {
        @Override
        public String name() {
            return "help";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    record Who(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "who";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    record Kick(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "kick";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    record Mute(String targetName, int minutes) implements GmCommand {
        @Override
        public String name() {
            return "mute";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    record Unmute(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "unmute";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    record Announce(String text) implements GmCommand {
        @Override
        public String name() {
            return "announce";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    record Heal(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "heal";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    /** Xem túi + đồ đang mặc của mục tiêu — CHỈ ĐỌC (lô 16, chủ dự án duyệt 2026-10-18). */
    record Inspect(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "inspect";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    /** Dời CHÍNH GM tới chỗ mục tiêu đang đứng — chỉ trong world chính (D4). */
    record Goto(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "goto";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    /** Dời MỤC TIÊU tới chỗ GM đang đứng — gỡ người kẹt địa hình, chỉ trong world chính (D4). */
    record Summon(String targetName) implements GmCommand {
        @Override
        public String name() {
            return "summon";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    /** Tạo tài sản từ không khí ⇒ ADMIN (D4). Đi thư hệ thống. */
    record Give(String targetName, String itemCode, int quantity) implements GmCommand {
        @Override
        public String name() {
            return "give";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.ADMIN;
        }
    }

    /** Tạo vàng = lạm phát ⇒ ADMIN (D4). Đi thư hệ thống. */
    record Gold(String targetName, long amount) implements GmCommand {
        @Override
        public String name() {
            return "gold";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.ADMIN;
        }
    }

    /**
     * Bật/tắt sự kiện nhân đôi (ADR-067) — đổi nhịp kinh tế cả server ⇒ ADMIN. {@code minutes == 0} = tắt.
     */
    record Event(GameEventKind kind, int multiplier, int minutes) implements GmCommand {
        @Override
        public String name() {
            return "event";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.ADMIN;
        }

        /** Lệnh tắt ({@code event <loại> off}). */
        public boolean stops() {
            return minutes == 0;
        }
    }

    /**
     * Lệnh "bàn trực" lô 22 — thông báo server (ADR-103), mã quà (ADR-104), báo cáo người chơi (ADR-105). Gom thành
     * một nhánh để bộ định tuyến chỉ thêm MỘT {@code case}.
     */
    sealed interface Desk extends GmCommand {}

    /** Đặt thông báo server; {@code text} rỗng = xoá ({@code /gm motd off}). ADMIN — chữ hiện cho mọi người. */
    record Motd(String text) implements Desk {
        @Override
        public String name() {
            return "motd";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.ADMIN;
        }

        /** Lệnh xoá. */
        public boolean clears() {
            return text.isEmpty();
        }
    }

    /** Tạo mã quà — tạo tài sản từ không khí ⇒ ADMIN, cùng lý do {@link Give}. {@code itemCode} rỗng = chỉ vàng. */
    record CreateGiftCode(String code, long gold, String itemCode, int quantity, int maxUses, int days)
            implements Desk {
        @Override
        public String name() {
            return "code";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.ADMIN;
        }
    }

    /**
     * ADR-165 — thư bồi thường cho MỌI nhân vật cấp ≥ {@code minLevel}; tạo tài sản ⇒ ADMIN, cùng lý do {@link Give}.
     * {@code itemCode} rỗng = chỉ vàng.
     */
    record Compensate(String code, long gold, String itemCode, int quantity, int minLevel) implements Desk {
        @Override
        public String name() {
            return "compensate";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.ADMIN;
        }
    }

    /** Liệt kê báo cáo chưa xử lý. */
    record Reports() implements Desk {
        @Override
        public String name() {
            return "reports";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    /** Xem một báo cáo (kèm trích chat). */
    record ShowReport(long reportId) implements Desk {
        @Override
        public String name() {
            return "report";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }

    /** Đánh dấu một báo cáo đã xử lý. */
    record ResolveReport(long reportId) implements Desk {
        @Override
        public String name() {
            return "resolve";
        }

        @Override
        public GmRole requiredRole() {
            return GmRole.GM;
        }
    }
}
