package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.epay")
public class EpayProperties {
    private String gateway = "https://pay.dropai-demo.com/submit.php";
    private String pid = "";
    private String key = "";
    private String notifyUrl = "";
    private String returnUrl = "";
    private String baseUrl = "https://dropai-demo.com";
    private String siteName = "DropAI";
    private String defaultType = "alipay";
    private String queryUrl = "https://pay.dropai-demo.com/api.php";
    private boolean mockEnabled = false;

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public String getPid() { return pid; }
    public void setPid(String pid) { this.pid = pid; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getDefaultType() { return defaultType; }
    public void setDefaultType(String defaultType) { this.defaultType = defaultType; }
    public String getQueryUrl() { return queryUrl; }
    public void setQueryUrl(String queryUrl) { this.queryUrl = queryUrl; }
    public boolean isMockEnabled() { return mockEnabled; }
    public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
}
