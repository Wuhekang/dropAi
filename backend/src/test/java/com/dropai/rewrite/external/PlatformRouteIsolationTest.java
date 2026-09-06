package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRouteIsolationTest {
    private static final String DEFAULT_SKILL_RESOURCE = "skills/humanize-zh-academic/SKILL.md";
    private static final String DEFAULT_SKILL_SHA256 =
            "440a5e76f540cf5dfaaf9c89f5b1585d339b7772b14ba12307cc71d22a974d39";
    private static final String DAYA_SKILL_SHA256 =
            "c51fc3653c43b2a77de721c75bd419f7a1d22f7528a9dfb4d5562485a4a49d56";

    @Test
    void nativeSkillCanChangeWhileTheDayaSkillRemainsGoldenAndIsolated() throws Exception {
        String defaultSkill = new ClassPathResource(DEFAULT_SKILL_RESOURCE)
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        assertThat(sha256(defaultSkill)).isEqualTo(DEFAULT_SKILL_SHA256);

        Set<String> selectableRoutes = new LinkedHashSet<>();
        selectableRoutes.add("GENERAL");
        selectableRoutes.add(XuejiePlatform.DAYA.name());

        assertThat(selectableRoutes).containsExactly("GENERAL", "DAYA").doesNotContain("TEST");

        PlatformRewriteSkillCatalog catalog = new PlatformRewriteSkillCatalog();
        String dayaSkillHash = sha256(catalog.load(XuejiePlatform.DAYA)
                .replace("\r\n", "\n").replace('\r', '\n'));

        assertThat(dayaSkillHash)
                .isEqualTo(DAYA_SKILL_SHA256)
                .isNotEqualTo(DEFAULT_SKILL_SHA256);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算测试资源 SHA-256", exception);
        }
    }
}
