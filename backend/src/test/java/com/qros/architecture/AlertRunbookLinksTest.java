package com.qros.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * {@code NFR-OBS-05}: "Mỗi cảnh báo phải kèm liên kết tới sổ tay xử lý (runbook)" — và liên kết
 * đó phải trỏ tới một file thật, không phải trỏ chết.
 *
 * <p>Chưa có Prometheus/Alertmanager thật đọc {@code infra/alerts/qros-alerts.yaml} ở M0 (xem
 * chú thích đầu file đó) — test này kiểm phần có thể đúng/sai ngay bây giờ mà không cần hạ tầng
 * đó: mọi {@code runbook_url} trong file cảnh báo phải khớp một file có thật trong
 * {@code docs/runbooks/}.
 */
class AlertRunbookLinksTest {

    @Test
    void moiCanhBaoDeuCoRunbookThat() throws IOException {
        Path repoRoot = repoRoot();
        Path alertsFile = repoRoot.resolve("infra/alerts/qros-alerts.yaml");
        assertThat(alertsFile).exists();

        List<String> runbookUrls = runbookUrlsTrongFile(alertsFile);
        assertThat(runbookUrls).isNotEmpty();

        List<String> khongTonTai = new ArrayList<>();
        for (String url : runbookUrls) {
            if (!Files.isRegularFile(repoRoot.resolve(url))) {
                khongTonTai.add(url);
            }
        }
        assertThat(khongTonTai).as("runbook_url trỏ chết").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<String> runbookUrlsTrongFile(Path alertsFile) throws IOException {
        Map<String, Object> root;
        try (var in = Files.newInputStream(alertsFile)) {
            root = new Yaml().load(in);
        }

        List<String> urls = new ArrayList<>();
        for (Object groupObj : (List<Object>) root.get("groups")) {
            Map<String, Object> group = (Map<String, Object>) groupObj;
            for (Object ruleObj : (List<Object>) group.get("rules")) {
                Map<String, Object> rule = (Map<String, Object>) ruleObj;
                Map<String, Object> annotations = (Map<String, Object>) rule.get("annotations");
                Object runbookUrl = annotations != null ? annotations.get("runbook_url") : null;
                assertThat(runbookUrl)
                        .as("alert %s thiếu annotations.runbook_url", rule.get("alert"))
                        .isNotNull();
                urls.add((String) runbookUrl);
            }
        }
        return urls;
    }

    /** {@code backend/} là thư mục làm việc của task test Gradle; gốc repo là thư mục cha. */
    private static Path repoRoot() {
        return Path.of("").toAbsolutePath().getParent();
    }
}
