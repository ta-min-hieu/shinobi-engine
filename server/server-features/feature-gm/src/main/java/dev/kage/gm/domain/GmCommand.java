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
}
