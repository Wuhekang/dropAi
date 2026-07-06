package com.dropai.rewrite.service;

import com.dropai.rewrite.config.EpayProperties;
import com.dropai.rewrite.entity.RechargeOrder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
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

    public EpayService(EpayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void logConfig() {
        log.info("========== EPAY CONFIG ==========");
        log.info("Gateway: {}", properties.getGateway());
        log.info("PID: {}", properties.getPid());
        log.info("Notify URL: {}", endpoint("/api/recharge/notify", properties.getNotifyUrl()));
        log.info("Return URL: {}", endpoint("/recharge/result", properties.getReturnUrl()));
        log.info("=================================");
    }

    public String createPayUrl(RechargeOrder order) {
        String notifyUrl = endpoint("/api/recharge/notify", properties.getNotifyUrl());
        String returnUrl = endpoint("/recharge/result", properties.getReturnUrl());
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
        log.info("EPAY order created. orderNo={}, amount={}, type={}, payUrl={}, notifyUrl={}, returnUrl={}",
                order.getOrderNo(), params.get("money"), params.get("type"), payUrl, notifyUrl, returnUrl);
        return payUrl;
    }

    public boolean verifyNotify(Map<String, String> params) {
        String received = params.get("sign");
        return received != null && received.equalsIgnoreCase(sign(params));
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
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .replacePath(path)
                    .replaceQuery(null)
                    .build()
                    .toUriString();
        } catch (IllegalStateException exception) {
            return path;
        }
    }

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
