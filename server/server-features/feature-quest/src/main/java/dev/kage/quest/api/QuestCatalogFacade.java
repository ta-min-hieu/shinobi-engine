package dev.kage.quest.api;

import java.util.List;

import dev.kage.quest.domain.QuestCatalogPort;

/** API danh mục nhiệm vụ (ADR-228). Bảng tham số — đọc MỘT lần ở lần hỏi đầu (đổi SQL thì khởi động lại). */
public final class QuestCatalogFacade {

    /** Danh mục rỗng (composition test). */
    public static final QuestCatalogFacade DISABLED = new QuestCatalogFacade(List::of);

    private final QuestCatalogPort port;
    private volatile List<QuestCatalogPort.Entry> cached;

    /** Dựng ở composition root. */
    public QuestCatalogFacade(QuestCatalogPort port) {
        this.port = port;
    }

    /** Mọi nhiệm vụ theo thứ tự cốt truyện. */
    public List<QuestCatalogPort.Entry> all() {
        List<QuestCatalogPort.Entry> loaded = cached;
        if (loaded == null) {
            loaded = List.copyOf(port.all());
            cached = loaded;
        }
        return loaded;
    }
}
