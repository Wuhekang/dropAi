package com.dropai.rewrite.service;

import com.dropai.rewrite.config.EpayProperties;
import com.dropai.rewrite.entity.RechargeOrder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EpayService {
    private static final Logger log = LoggerFactory.getLogger(EpayService.class);

    private final EpayProperties properties;
    private final RestClient restClient;

    public EpayService(EpayProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @PostConstruct
    public void logConfig() {
        log.info("========== EPAY CONFIG ==========");
        log.info("Gateway: {}", properties.getGateway());
        log.info("Merchant configured: {}", !blank(properties.getPid()) && !blank(properties.getKey()));
        log.info("Notify URL: {}", endpoint("/api/recharge/notify", properties.getNotifyUrl()));
        log.info("Return URL: {}", endpoint("/recharge", properties.getReturnUrl()));
        log.info("=================================");
    }

    public String createPayUrl(RechargeOrder order) {
        validateConfiguration();
        String notifyUrl = endpoint("/api/recharge/notify", properties.getNotifyUrl());
        String returnUrl = UriComponentsBuilder.fromUriString(endpoint("/recharge", properties.getReturnUrl()))
                .queryParam("order_no", order.getOrderNo()).build().toUriString();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", properties.getPid());
        params.put("type", normalizeType(order.getPayMethod()));
        params.put("out_trade_no", order.getOrderNo());
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("name", "DropAI points recharge");
        params.put("money", order.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        params.put("sitename", properties.getSiteName());
        params.put("sign", sign(params));
        params.put("sign_type", "MD5");
        String payUrl = properties.getGateway() + "?" + toQuery(params);
        log.info("EPAY order created. orderNo={}, amount={}, type={}, notifyUrl={}, returnUrl={}",
                order.getOrderNo(), params.get("money"), params.get("type"), notifyUrl, returnUrl);
        return payUrl;
    }

    public boolean verifyNotify(Map<String, String> params) {
        if (blank(properties.getPid()) || blank(properties.getKey()) ||
                !properties.getPid().equals(params.get("pid"))) return false;
        String received = params.get("sign");
        if (received == null) return false;
        return MessageDigest.isEqual(received.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                sign(params).getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(Map<String, String> params) {
        String payload = params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&")) + properties.getKey();
        return md5(payload);
    }

    private String endpoint(String path, String configured) {
        if (configured != null && !configured.isBlank()) return configured.trim();
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            return UriComponentsBuilder.fromUriString(properties.getBaseUrl().trim())
                    .replacePath(path)
                    .replaceQuery(null)
                    .build()
                    .toUriString();
        }
        throw new IllegalStateException("支付回调地址未配置");
    }

    public boolean isMockEnabled() { return properties.isMockEnabled(); }

    @SuppressWarnings("unchecked")
    public PaymentQuery queryPaidOrder(String orderNo) {
        validateConfiguration();
        if (blank(properties.getQueryUrl())) throw new IllegalStateException("支付订单查询地址未配置");
        Map<String, Object> body = restClient.get().uri(builder -> builder
                .scheme("https").host(UriComponentsBuilder.fromUriString(properties.getQueryUrl()).build().getHost())
                .path(UriComponentsBuilder.fromUriString(properties.getQueryUrl()).build().getPath())
                .queryParam("act", "order").queryParam("pid", properties.getPid())
                .queryParam("key", properties.getKey()).queryParam("out_trade_no", orderNo).build())
                .retrieve().body(Map.class);
        if (body == null || !"1".equals(String.valueOf(body.get("code")))) {
            throw new IllegalStateException("支付平台未确认该订单");
        }
        String status = String.valueOf(body.get("status"));
        if (!("1".equals(status) || "TRADE_SUCCESS".equalsIgnoreCase(status))) {
            throw new IllegalStateException("支付平台订单尚未支付成功");
        }
        return new PaymentQuery(orderNo, String.valueOf(body.get("trade_no")),
                new java.math.BigDecimal(String.valueOf(body.get("money"))), status);
    }

    public record PaymentQuery(String orderNo, String tradeNo, java.math.BigDecimal money, String status) {}

    private void validateConfiguration() {
        if (blank(properties.getGateway()) || blank(properties.getPid()) || blank(properties.getKey())) {
            throw new IllegalStateException("支付服务配置不完整，请联系管理员");
        }
        if (!properties.getGateway().startsWith("https://")) {
            throw new IllegalStateException("支付网关必须使用 HTTPS");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String normalizeType(String payMethod) {
        if (payMethod == null || payMethod.isBlank() || "epay".equalsIgnoreCase(payMethod)) {
            return properties.getDefaultType();
        }
        return payMethod.trim().toLowerCase();
    }

    private String toQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String md5(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign EasyPay request", exception);
        }
    }
}
