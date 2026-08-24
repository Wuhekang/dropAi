package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchoolViewerStatisticsTest {
    @AfterEach void clearAuth() { AuthContext.clear(); }

    @Test void exposesGiftPointsInsteadOfStudentRechargeAmount() {
        SchoolMapper schools=mock(SchoolMapper.class);
        UserAccountMapper users=mock(UserAccountMapper.class);
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        UserAccount viewer=new UserAccount();
        viewer.setId(7L); viewer.setRole("SCHOOL_VIEWER"); viewer.setSchoolId(1L);
        viewer.setAccountEnabled(true); viewer.setPoints(80);
        School school=new School();
        school.setId(1L); school.setSchoolCode("00001"); school.setSchoolName("测试学校"); school.setEnabled(true);
        when(users.selectById(7L)).thenReturn(viewer);
        when(schools.selectById(1L)).thenReturn(school);
        when(jdbc.queryForObject(contains("recharge_order"),eq(BigDecimal.class),eq(7L))).thenReturn(new BigDecimal("3.00"));
        when(jdbc.queryForObject(contains("SCHOOL_GIFT_OUT"),eq(Long.class),eq(1L))).thenReturn(120L);
        AuthContext.setUserId(7L);
        var stats=new SchoolService(schools,users,jdbc,mock(PointTransactionMapper.class)).viewerStats("30d");

        assertEquals(120L,stats.get("studentGiftPoints"));
        assertTrue(stats.containsKey("giftTrend"));
        assertFalse(stats.containsKey("studentRechargeAmount"));
        assertFalse(stats.containsKey("rechargeTrend"));
    }
}
