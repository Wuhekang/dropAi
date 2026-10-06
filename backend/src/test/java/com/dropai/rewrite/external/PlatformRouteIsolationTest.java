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
            "1807c1174e55bcda0cea7d9ff94aaa6be91c7789246c1f7356941d764b91aada";
    private static final String DAYA_SKILL_SHA256 =
            // Normalized LF hash of the unchanged 01c09450 resource.
            "56a4f111b7d7aa70f3b1226175b4b96c9c6b33ad995e06ffa6dce1a91f350d7d";

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
        selectableRoutes.add("GEZIDA");

        assertThat(selectableRoutes).containsExactly("GENERAL", "DAYA", "GEZIDA").doesNotContain("TEST");
        String gezida = new ClassPathResource("skills/gezida-academic/SKILL.md")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(gezida).contains("格子达学术表达专项", "必要的编号列举", "可以保留");
        assertThat(gezida).isNotEqualTo(defaultSkill);

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
