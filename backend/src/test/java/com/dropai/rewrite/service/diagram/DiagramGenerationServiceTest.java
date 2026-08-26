package com.dropai.rewrite.service.diagram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagramGenerationServiceTest {
    @Test void localSummaryNeverRequiresCloudAndRespectsLimit(){
        String source="用户提交订单。系统检查库存。库存充足时创建订单并扣减库存；库存不足时提示用户。随后发送支付请求和订单通知。";
        String summary=DiagramGenerationService.localSummary(source,30);
        assertFalse(summary.isBlank());
        assertTrue(summary.length()<=30,summary);
        assertTrue(source.startsWith(summary));
    }
}
