package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XuejiePlatformTest {

    @Test
    void exposesOnlyTheDayaOptInPlatform() {
        assertThat(XuejiePlatform.values()).containsExactly(XuejiePlatform.DAYA);
        assertThat(XuejiePlatform.require("daya").remoteName()).isEqualTo("大雅");
    }

    @Test
    void generalAndTestCanNeverEnterExternalRoute() {
        for (String rejected : new String[]{"GENERAL", "TEST", "CNKI", "WEIPU", "GEZIDA",
                "PAPERYY", "BIGAN", "WANFANG", "PAPERPASS", "ZHUQUE", "HUACHEN"}) {
            assertThatThrownBy(() -> XuejiePlatform.require(rejected))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> XuejieRewriteMode.require("rewrite"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
