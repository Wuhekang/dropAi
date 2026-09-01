package com.dropai.rewrite;

import com.dropai.rewrite.config.CommercialFeatureSchemaInitializer;
import com.dropai.rewrite.config.SchoolSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchoolAccountSchemaInitializerTest {
    @Test
    void freshDatabaseContainsRechargeSnapshotAndDeletionAuditColumns() throws Exception {
        DriverManagerDataSource dataSource = dataSource("school-account-schema");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createLegacyUserTable(jdbc);

        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        new SchoolSchemaInitializer(jdbc, dataSource).run(arguments);
        new CommercialFeatureSchemaInitializer(jdbc, dataSource).run(arguments);

        assertColumn(jdbc, "RECHARGE_ORDER", "RECHARGE_PRICE_PER10");
        assertColumn(jdbc, "USER_ACCOUNT", "DELETED_AT");
        assertColumn(jdbc, "USER_ACCOUNT", "DELETED_BY");
        assertColumn(jdbc, "USER_ACCOUNT", "DELETE_REASON");
        assertColumn(jdbc, "SCHOOL", "DELETED_AT");
        assertColumn(jdbc, "SCHOOL", "DELETED_BY");
        assertColumn(jdbc, "SCHOOL", "DELETE_REASON");
        assertColumn(jdbc, "SCHOOL", "STUDENT_RECHARGE_PRICE_PER10");
        assertColumn(jdbc, "SCHOOL", "STUDENT_RECHARGE_MIN_PRICE_PER10");
    }

    @Test
    void legacySchoolGetsTwoYuanStudentDefaultAndOneYuanAdminFloor() throws Exception {
        DriverManagerDataSource dataSource = dataSource("school-account-upgrade");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createLegacyUserTable(jdbc);
        jdbc.execute("""
                CREATE TABLE school (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  school_code VARCHAR(64) NOT NULL UNIQUE,
                  school_name VARCHAR(120) NOT NULL,
                  recharge_price_per10 DECIMAL(10,2) DEFAULT 0.30 NOT NULL,
                  enabled BOOLEAN DEFAULT TRUE NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("INSERT INTO school(school_code,school_name,recharge_price_per10) VALUES(?,?,?)",
                "LEGACY", "旧学校", new java.math.BigDecimal("0.50"));

        SchoolSchemaInitializer initializer = new SchoolSchemaInitializer(jdbc, dataSource);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        initializer.run(arguments);
        initializer.run(arguments);

        java.math.BigDecimal studentPrice = jdbc.queryForObject(
                "SELECT student_recharge_price_per10 FROM school WHERE school_code='LEGACY'",
                java.math.BigDecimal.class);
        java.math.BigDecimal studentMinPrice = jdbc.queryForObject(
                "SELECT student_recharge_min_price_per10 FROM school WHERE school_code='LEGACY'",
                java.math.BigDecimal.class);
        assertEquals(0, new java.math.BigDecimal("2.00").compareTo(studentPrice));
        assertEquals(0, new java.math.BigDecimal("1.00").compareTo(studentMinPrice));

        jdbc.update("INSERT INTO school(school_code,school_name) VALUES(?,?)", "DEFAULTS", "默认价格学校");
        assertEquals(0, new java.math.BigDecimal("2.00").compareTo(jdbc.queryForObject(
                "SELECT student_recharge_price_per10 FROM school WHERE school_code='DEFAULTS'",
                java.math.BigDecimal.class)));
        assertEquals(0, new java.math.BigDecimal("1.00").compareTo(jdbc.queryForObject(
                "SELECT student_recharge_min_price_per10 FROM school WHERE school_code='DEFAULTS'",
                java.math.BigDecimal.class)));

        jdbc.update("UPDATE school SET recharge_price_per10=0.30,student_recharge_price_per10=0.40,student_recharge_min_price_per10=0.30 WHERE school_code='LEGACY'");
        initializer.run(arguments);
        assertEquals(0, new java.math.BigDecimal("0.40").compareTo(jdbc.queryForObject(
                "SELECT student_recharge_price_per10 FROM school WHERE school_code='LEGACY'",
                java.math.BigDecimal.class)));
    }

    @Test
    void concurrentDuplicateColumnFromAnotherInitializerIsSafelyIgnored() throws Exception {
        DriverManagerDataSource dataSource = dataSource("school-account-concurrent-upgrade");
        AtomicBoolean raced = new AtomicBoolean();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource) {
            @Override
            public void execute(String sql) {
                if (sql.startsWith("ALTER TABLE user_account ADD COLUMN school_id")
                        && raced.compareAndSet(false, true)) {
                    super.execute(sql);
                    super.execute(sql);
                    return;
                }
                super.execute(sql);
            }
        };
        createLegacyUserTable(jdbc);

        new SchoolSchemaInitializer(jdbc, dataSource)
                .run(new DefaultApplicationArguments(new String[0]));

        assertTrue(raced.get());
        assertColumn(jdbc, "USER_ACCOUNT", "SCHOOL_ID");
        assertColumn(jdbc, "USER_ACCOUNT", "DELETED_AT");
    }

    @Test
    void interruptedUpgradeRestoresInvalidPartialDefaultButPreservesValidAdminDiscount() throws Exception {
        DriverManagerDataSource dataSource = dataSource("school-account-partial-upgrade");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createLegacyUserTable(jdbc);
        jdbc.execute("""
                CREATE TABLE school (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  school_code VARCHAR(64) NOT NULL UNIQUE,
                  school_name VARCHAR(120) NOT NULL,
                  recharge_price_per10 DECIMAL(10,2) DEFAULT 0.30 NOT NULL,
                  student_recharge_price_per10 DECIMAL(10,2) DEFAULT 0.30 NOT NULL,
                  student_recharge_min_price_per10 DECIMAL(10,2) DEFAULT 1.00 NOT NULL,
                  enabled BOOLEAN DEFAULT TRUE NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("INSERT INTO school(school_code,school_name,student_recharge_price_per10,student_recharge_min_price_per10) VALUES(?,?,?,?)",
                "PARTIAL", "中断升级学校", new java.math.BigDecimal("0.30"), new java.math.BigDecimal("1.00"));
        jdbc.update("INSERT INTO school(school_code,school_name,student_recharge_price_per10,student_recharge_min_price_per10) VALUES(?,?,?,?)",
                "DISCOUNT", "合法放宽学校", new java.math.BigDecimal("0.40"), new java.math.BigDecimal("0.30"));

        new SchoolSchemaInitializer(jdbc, dataSource)
                .run(new DefaultApplicationArguments(new String[0]));

        assertEquals(0, new java.math.BigDecimal("2.00").compareTo(jdbc.queryForObject(
                "SELECT student_recharge_price_per10 FROM school WHERE school_code='PARTIAL'",
                java.math.BigDecimal.class)));
        assertEquals(0, new java.math.BigDecimal("0.40").compareTo(jdbc.queryForObject(
                "SELECT student_recharge_price_per10 FROM school WHERE school_code='DISCOUNT'",
                java.math.BigDecimal.class)));
    }

    private DriverManagerDataSource dataSource(String name) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        return dataSource;
    }

    private void createLegacyUserTable(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE user_account (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  phone VARCHAR(64) NOT NULL,
                  password_hash VARCHAR(255) NOT NULL,
                  role VARCHAR(32) NOT NULL,
                  points INT DEFAULT 0 NOT NULL,
                  total_points INT DEFAULT 0 NOT NULL,
                  used_points INT DEFAULT 0 NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """);
    }

    private void assertColumn(JdbcTemplate jdbc, String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? AND COLUMN_NAME=?",
                Integer.class, table, column);
        assertEquals(1, count, table + "." + column + " should exist after first startup");
    }
}
