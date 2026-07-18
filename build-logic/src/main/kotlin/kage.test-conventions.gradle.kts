// kage.test-conventions — mở rộng cho module cần Mockito / integration test.
// Áp THÊM khi module thật sự cần (không áp mặc định — Điều 9).
plugins {
    id("kage.java-conventions")
}

dependencies {
    "testImplementation"("org.mockito:mockito-core:5.20.0")            // ⚠️ verify
    "testImplementation"("org.mockito:mockito-junit-jupiter:5.20.0")   // ⚠️ verify
    // Integration test (Testcontainers) chỉ thêm ở module infrastructure khi tới Phase 3.
}
