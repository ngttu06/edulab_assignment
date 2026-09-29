package vn.edu.khtn8.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edulab")
public class AppProperties {
    private final Cookie cookie = new Cookie();
    private final Upload upload = new Upload();
    private final Ai ai = new Ai();

    public Cookie getCookie() { return cookie; }
    public Upload getUpload() { return upload; }
    public Ai getAi() { return ai; }

    public static class Cookie {
        private boolean secure = true;
        private String sameSite = "Lax";
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    }

    public static class Upload {
        private String root = "./storage";
        private long maxImageBytes = 5 * 1024 * 1024;
        private long maxPdfBytes = 10 * 1024 * 1024;
        private boolean devScanBypass;
        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
        public long getMaxImageBytes() { return maxImageBytes; }
        public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
        public long getMaxPdfBytes() { return maxPdfBytes; }
        public void setMaxPdfBytes(long maxPdfBytes) { this.maxPdfBytes = maxPdfBytes; }
        public boolean isDevScanBypass() { return devScanBypass; }
        public void setDevScanBypass(boolean devScanBypass) { this.devScanBypass = devScanBypass; }
    }

    public static class Ai {
        private boolean enabled;
        private int dailyRequestLimit = 20;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getDailyRequestLimit() { return dailyRequestLimit; }
        public void setDailyRequestLimit(int dailyRequestLimit) { this.dailyRequestLimit = dailyRequestLimit; }
    }
}
