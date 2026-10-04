package com.dropai.rewrite;

import com.dropai.rewrite.config.PointSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactPricingTest {
    @Test void upgradesThreeArtifactPricesOnceWithoutChangingOtherFeaturesOrEnabledFlags() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:artifact-prices;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE feature_pricing(id BIGINT AUTO_INCREMENT PRIMARY KEY,feature_code VARCHAR(80) UNIQUE,feature_name VARCHAR(120),cost_points INT,enabled BOOLEAN)");
        jdbc.update("INSERT INTO feature_pricing(feature_code,feature_name,cost_points,enabled) VALUES('WRITING_DOCX','legacy-writing',60,FALSE),('PPT_GENERATE','legacy-ppt',100,TRUE),('DOCX_GENERATE','other-docx',30,TRUE)");
        var initializer=new PointSchemaInitializer(jdbc,source);ReflectionTestUtils.invokeMethod(initializer,"seedPricing");
        for(String code:new String[]{"WRITING_DOCX","PPT_GENERATE","MECHANICAL_PAPER"}) assertEquals(300,jdbc.queryForObject("SELECT cost_points FROM feature_pricing WHERE feature_code=?",Integer.class,code));
        assertFalse(jdbc.queryForObject("SELECT enabled FROM feature_pricing WHERE feature_code='WRITING_DOCX'",Boolean.class));
        assertEquals(30,jdbc.queryForObject("SELECT cost_points FROM feature_pricing WHERE feature_code='DOCX_GENERATE'",Integer.class));
        jdbc.update("UPDATE feature_pricing SET cost_points=350 WHERE feature_code='PPT_GENERATE'");
        ReflectionTestUtils.invokeMethod(initializer,"seedPricing");
        assertEquals(350,jdbc.queryForObject("SELECT cost_points FROM feature_pricing WHERE feature_code='PPT_GENERATE'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM feature_pricing WHERE feature_code='MECHANICAL_PAPER'",Integer.class));
    }
}
