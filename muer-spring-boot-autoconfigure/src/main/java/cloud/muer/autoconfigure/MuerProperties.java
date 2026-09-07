package cloud.muer.autoconfigure;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Muer 对外暴露的 Spring Boot 配置属性。
 *
 * <p>该类只负责绑定框架配置，不改变会话、令牌或数据表的既有默认行为。</p>
 */
@ConfigurationProperties("muer")
public class MuerProperties {
    private boolean enabled = true;
    private final Token token = new Token();
    private final Session session = new Session();
    private final Schema schema = new Schema();
    private final Feature audit = new Feature();
    private final Feature diagnostics = new Feature();
    private List<String> clientTypes = new ArrayList<>(List.of("WEB"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Token getToken() {
        return token;
    }

    public Session getSession() {
        return session;
    }

    public Schema getSchema() {
        return schema;
    }

    public Feature getAudit() {
        return audit;
    }

    public Feature getDiagnostics() {
        return diagnostics;
    }

    public List<String> getClientTypes() {
        return List.copyOf(clientTypes);
    }

    public void setClientTypes(List<String> clientTypes) {
        this.clientTypes = new ArrayList<>(clientTypes == null ? List.of() : clientTypes);
    }

    /**
     * 校验外部配置，避免不安全或不可用的令牌与客户端类型设置进入运行时。
     */
    @PostConstruct
    void validate() {
        if (token.ttl == null || token.ttl.isZero() || token.ttl.isNegative()) {
            throw new IllegalStateException("muer.token.ttl must be positive");
        }
        if (clientTypes.isEmpty() || clientTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalStateException("muer.client-types must contain at least one non-blank value");
        }
        if (token.redisPrefix == null || token.redisPrefix.isBlank()) {
            throw new IllegalStateException("muer.token.redis-prefix must not be blank");
        }
        if (schema.historyTable == null || schema.historyTable.isBlank()) {
            throw new IllegalStateException("muer.schema.history-table must not be blank");
        }
    }

    public static class Token {
        private Duration ttl = Duration.ofHours(8);
        private String redisPrefix = "iam";

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public String getRedisPrefix() {
            return redisPrefix;
        }

        public void setRedisPrefix(String redisPrefix) {
            this.redisPrefix = redisPrefix;
        }
    }

    public static class Session {
        private boolean enabled = true;
        private Duration touchInterval = Duration.ofMinutes(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTouchInterval() {
            return touchInterval;
        }

        public void setTouchInterval(Duration touchInterval) {
            this.touchInterval = touchInterval;
        }
    }

    public static class Schema {
        private boolean enabled = true;
        private String historyTable = "iam_flyway_schema_history";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHistoryTable() {
            return historyTable;
        }

        public void setHistoryTable(String historyTable) {
            this.historyTable = historyTable;
        }
    }

    public static class Feature {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
