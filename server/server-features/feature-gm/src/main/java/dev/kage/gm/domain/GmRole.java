package dev.kage.gm.domain;

/** Hai cấp quyền GM (ADR-052 D2) — {@link #ADMIN} làm được mọi lệnh của {@link #GM}. */
public enum GmRole {
    GM,
    ADMIN;

    /** Cấp này có đủ quyền cho một lệnh đòi {@code required} không. */
    public boolean atLeast(GmRole required) {
        return compareTo(required) >= 0;
    }
}
