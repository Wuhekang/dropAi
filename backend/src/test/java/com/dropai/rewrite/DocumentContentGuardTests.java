package com.dropai.rewrite;

import com.dropai.rewrite.service.impl.DocumentRewriteServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContentGuardTests {

    private final DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(null, null, null, null, null);

    @Test
    void detectsCodeLikeFragmentsAsProtectedContent() throws Exception {
        assertThat(isTechnicalFragment("@GetMapping(\"/page\")")).isTrue();
        assertThat(isTechnicalFragment("public class UserController {")).isTrue();
        assertThat(isTechnicalFragment("SELECT * FROM user WHERE id = #{id}")).isTrue();
        assertThat(isTechnicalFragment("<template><div>{{ title }}</div></template>")).isTrue();
        assertThat(isTechnicalFragment("private BillService billService;")).isTrue();
        assertThat(isTechnicalFragment("本系统采用Vue完成前端页面开发。")).isFalse();
    }

    @Test
    void detectsCaptionsAndFormulaLinesAsProtectedContent() throws Exception {
        assertThat(isCaptionOrFormulaLine("图 5.1 登录页面")).isTrue();
        assertThat(isCaptionOrFormulaLine("表 4.2 用户信息表")).isTrue();
        assertThat(isCaptionOrFormulaLine("公式 3.1 计费模型")).isTrue();
        assertThat(isCaptionOrFormulaLine("(3.1) y = ax + b")).isTrue();
        assertThat(isCaptionOrFormulaLine("该模块主要完成数据统计与展示功能。")).isFalse();
    }

    private boolean isTechnicalFragment(String text) throws Exception {
        Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod("isTechnicalFragment", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, text);
    }

    private boolean isCaptionOrFormulaLine(String text) throws Exception {
        Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod("isCaptionOrFormulaLine", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, text);
    }
}
