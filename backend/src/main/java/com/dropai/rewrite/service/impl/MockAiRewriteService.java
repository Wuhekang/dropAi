package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.AiRewriteService;
import org.springframework.stereotype.Service;

@Service
public class MockAiRewriteService implements AiRewriteService {

    @Override
    public String rewrite(String originalText, String rewriteType) {
        return "【" + rewriteType + "】优化结果：" + originalText;
    }

    @Override
    public String providerName() {
        return "Mock 模拟服务";
    }

    @Override
    public String modelName() {
        return "mock";
    }
}
