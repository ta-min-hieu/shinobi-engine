package dev.kage.gm.domain;

/** Kết quả chốt của một lệnh GM trong {@code gm_audit_log.outcome} (V61 CHECK). */
public enum GmOutcome {
    OK,
    /** Lệnh hợp lệ về quyền nhưng không thi hành được (mục tiêu không có, offline...). */
    REJECTED,
    /** Có quyền GM nhưng thiếu cấp, hoặc tác động lên người cấp ≥ mình. */
    DENIED,
    /** Lỗi lúc thi hành. */
    FAILED
}
