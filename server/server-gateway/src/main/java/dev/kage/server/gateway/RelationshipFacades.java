package dev.kage.server.gateway;

import dev.kage.friend.api.BlockFacade;
import dev.kage.friend.api.DaoLuFacade;
import dev.kage.friend.api.MentorshipFacade;

/** Quan hệ giữa người chơi của lô 21 (ADR-091/093/094) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record RelationshipFacades(BlockFacade blocks, MentorshipFacade mentorship, DaoLuFacade daoLu) {

    /** Composition không có các quan hệ này (test). */
    static final RelationshipFacades DISABLED =
            new RelationshipFacades(BlockFacade.DISABLED, MentorshipFacade.DISABLED, DaoLuFacade.DISABLED);
}
