// shared-protocol — Packet/DTO. KHÔNG phụ thuộc module nào khác (REPOSITORY-STRUCTURE §2).
// KHÔNG phụ thuộc Netty (Điều 23) — PacketCodec dùng java.nio.ByteBuffer thuần, để cả
// server-gateway (Netty) lẫn client-core (Socket thuần) đều dùng chung không phá ArchUnit.
plugins {
    id("kage.quality-conventions")
    id("com.google.protobuf") version "0.10.0"
}

dependencies {
    api("com.google.protobuf:protobuf-java:4.35.1")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.35.1"
    }
}

// ⚠️ PHÁT HIỆN THẬT: exclude("**/build/generated/**") KHÔNG hoạt động — protobuf plugin thêm
// build/generated/sources/proto/main/java vào sourceSets.main.java.srcDirs, nhưng lúc Checkstyle/
// PMD duyệt file thì đường dẫn đã bị "cắt gốc" (relative tới srcDir), không còn chứa
// "build/generated" trong path nữa nên pattern không khớp — 21 lỗi Checkstyle + 9 PMD từ code
// SINH RA (không phải code ta viết) suýt chặn build. Cách đúng: ĐẶT LẠI hẳn "source" của
// Checkstyle/PMD chỉ trỏ src/main/java, không dùng exclude theo pattern.
//
// CHỈ áp lên task *Main* — dùng withType<>().configureEach{} sẽ ĐÈ LUÔN checkstyleTest/pmdTest,
// khiến chúng quét nhầm src/main/java thay vì src/test/java (test code sẽ không được kiểm tra
// thật). Nhắm đích danh 2 task Main để không ảnh hưởng Test.
tasks.named<Checkstyle>("checkstyleMain") {
    source = fileTree("src/main/java")
}
tasks.named<Pmd>("pmdMain") {
    source = fileTree("src/main/java")
}
