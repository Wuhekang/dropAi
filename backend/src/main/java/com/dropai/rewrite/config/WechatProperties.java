package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.wechat")
public class WechatProperties {
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    private String frontendSuccessUri = "/login";
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getFrontendSuccessUri() { return frontendSuccessUri; }
    public void setFrontendSuccessUri(String frontendSuccessUri) { this.frontendSuccessUri = frontendSuccessUri; }
    public boolean configured() { return !appId.isBlank() && !appSecret.isBlank() && !redirectUri.isBlank(); }
}
