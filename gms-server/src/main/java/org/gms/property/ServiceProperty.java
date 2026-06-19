package org.gms.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "gms.service")
@Component
@Data
public class ServiceProperty {
    private static final String FIXED_LANGUAGE = "zh-CN";

    private String language = FIXED_LANGUAGE;
    private RateLimitProperty rateLimit;
    private String wanHost;
    private String lanHost;
    private String localhost;
    private int loginPort;

    public String getLanguage() {
        return FIXED_LANGUAGE;
    }

    public void setLanguage(String language) {
        this.language = FIXED_LANGUAGE;
    }

    @Data
    public static class RateLimitProperty {
        private boolean enabled;
        private int limit;
        private long duration;
        private boolean autoBan;
    }
}
