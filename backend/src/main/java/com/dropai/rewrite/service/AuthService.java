package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.config.WechatProperties;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.UserSession;
import com.dropai.rewrite.entity.WechatLoginState;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.UserSessionMapper;
import com.dropai.rewrite.mapper.WechatLoginStateMapper;
import com.dropai.rewrite.vo.AuthVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountMapper accountMapper;
    private final UserSessionMapper sessionMapper;
    private final WechatLoginStateMapper stateMapper;
    private final WechatProperties properties;
    private final RestClient restClient;

    public AuthService(UserAccountMapper accountMapper, UserSessionMapper sessionMapper,
                       WechatLoginStateMapper stateMapper, WechatProperties properties,
                       RestClient.Builder restClientBuilder) {
        this.accountMapper = accountMapper;
        this.sessionMapper = sessionMapper;
        this.stateMapper = stateMapper;
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public boolean wechatConfigured() {
        return properties.configured();
    }

    public String wechatAuthorizeUrl() {
        if (!properties.configured()) throw new IllegalStateException("微信扫码登录尚未配置");
        WechatLoginState loginState = new WechatLoginState();
        loginState.setState(UUID.randomUUID().toString().replace("-", ""));
        loginState.setCreatedAt(LocalDateTime.now());
        loginState.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        stateMapper.insert(loginState);
        return "https://open.weixin.qq.com/connect/qrconnect?appid="
                + encode(properties.getAppId()) + "&redirect_uri=" + encode(properties.getRedirectUri())
                + "&response_type=code&scope=snsapi_login&state=" + encode(loginState.getState()) + "#wechat_redirect";
    }

    public AuthVO wechatCallback(String code, String state) {
        WechatLoginState loginState = stateMapper.selectById(state);
        stateMapper.deleteById(state);
        if (loginState == null || loginState.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("微信登录状态已失效，请重新扫码");
        }
        JsonNode token = restClient.get().uri(UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", properties.getAppId()).queryParam("secret", properties.getAppSecret())
                .queryParam("code", code).queryParam("grant_type", "authorization_code").build().toUri())
                .retrieve().body(JsonNode.class);
        requireWechatSuccess(token);
        String openid = token.path("openid").asText();
        String unionid = token.path("unionid").asText("");
        String accessToken = token.path("access_token").asText();
        JsonNode profile = restClient.get().uri(UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/userinfo")
                .queryParam("access_token", accessToken).queryParam("openid", openid)
                .queryParam("lang", "zh_CN").build().toUri()).retrieve().body(JsonNode.class);
        requireWechatSuccess(profile);

        UserAccount account = findWechatAccount(openid, unionid);
        if (account == null) {
            account = new UserAccount();
            account.setWechatOpenid(openid);
            account.setWechatUnionid(blankToNull(unionid));
            account.setCreatedAt(LocalDateTime.now());
        }
        account.setNickname(profile.path("nickname").asText("微信用户"));
        account.setAvatarUrl(profile.path("headimgurl").asText(""));
        account.setUpdatedAt(LocalDateTime.now());
        if (account.getId() == null) accountMapper.insert(account); else accountMapper.updateById(account);
        return createSession(account);
    }

    public String frontendSuccessUri(AuthVO auth) {
        return properties.getFrontendSuccessUri() + "?token=" + encode(auth.token()) + "&username=" + encode(auth.username());
    }

    public Long authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession session = sessionMapper.selectById(token);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) return null;
        UserAccount account = accountMapper.selectById(session.getUserId());
        if (account == null || account.getWechatOpenid() == null || account.getWechatOpenid().isBlank()) return null;
        return session.getUserId();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) sessionMapper.deleteById(token);
    }

    private UserAccount findWechatAccount(String openid, String unionid) {
        LambdaQueryWrapper<UserAccount> query = new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getWechatOpenid, openid);
        if (unionid != null && !unionid.isBlank()) query.or().eq(UserAccount::getWechatUnionid, unionid);
        return accountMapper.selectOne(query);
    }

    private AuthVO createSession(UserAccount account) {
        UserSession session = new UserSession();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(account.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        sessionMapper.insert(session);
        return new AuthVO(account.getId(), account.getNickname(), session.getToken());
    }

    private void requireWechatSuccess(JsonNode response) {
        if (response == null || response.has("errcode")) {
            throw new IllegalStateException("微信登录失败：" + (response == null ? "空响应" : response.path("errmsg").asText()));
        }
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
