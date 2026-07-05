package com.dropai.rewrite.controller;

import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/alipay")
public class AlipayNotifyController {
    @PostMapping("/notify")
    public Result<Map<String, String>> notify(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success(Map.of("status", "reserved"));
    }
}
