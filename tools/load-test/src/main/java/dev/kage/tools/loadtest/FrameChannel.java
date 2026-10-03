package dev.kage.tools.loadtest;

import java.io.IOException;

import dev.kage.protocol.wire.PacketCodec;

/**
 * Tầng dây tối thiểu mà một lượt hỏi-đáp ({@link RoundTrip}) cần: gửi 1 frame và đọc frame kế tiếp.
 * Tách ra (2026-10-03) để phần GHÉP PHẢN HỒI — chỗ đã từng sai và làm hỏng số đo độ trễ — kiểm được
 * bằng test đơn vị với một dòng frame dựng sẵn, không cần socket TLS thật.
 */
interface FrameChannel {

    /**
     * Gửi 1 frame.
     *
     * @return {@code sequenceId} đã gắn vào frame — server lặp lại đúng số này ở phản hồi
     * @throws IOException nếu socket đã chết
     */
    int send(int opcode, byte[] payload) throws IOException;

    /**
     * Đọc đúng 1 frame kế tiếp, bất kể opcode.
     *
     * @throws java.net.SocketTimeoutException nếu hết thời gian chờ
     * @throws IOException nếu socket chết hoặc frame hỏng
     */
    PacketCodec.Frame readFrame() throws IOException;
}
