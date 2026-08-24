package com.dropai.rewrite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

@Component
@Order(100)
public class SchoolSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc; private final DataSource dataSource;
    public SchoolSchemaInitializer(JdbcTemplate jdbc, DataSource dataSource){this.jdbc=jdbc;this.dataSource=dataSource;}
    public void run(ApplicationArguments args) throws Exception {
        try(Connection c=dataSource.getConnection()){
            boolean h2=c.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
            jdbc.execute(h2 ? "CREATE TABLE IF NOT EXISTS school(id BIGINT AUTO_INCREMENT PRIMARY KEY,school_code VARCHAR(64) NOT NULL UNIQUE,school_name VARCHAR(120) NOT NULL,recharge_price_per10 DECIMAL(10,2) DEFAULT 0.30 NOT NULL,enabled BOOLEAN DEFAULT TRUE NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)" : "CREATE TABLE IF NOT EXISTS school(id BIGINT PRIMARY KEY AUTO_INCREMENT,school_code VARCHAR(64) NOT NULL,school_name VARCHAR(120) NOT NULL,recharge_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 0.30,enabled TINYINT(1) NOT NULL DEFAULT 1,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_school_code(school_code)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            ensure(c,"school","recharge_price_per10","DECIMAL(10,2) DEFAULT 0.30 NOT NULL");
            ensure(c,"user_account","school_id",h2?"BIGINT DEFAULT 0 NOT NULL":"BIGINT NOT NULL DEFAULT 0");
            ensure(c,"user_account","account_enabled",h2?"BOOLEAN DEFAULT TRUE NOT NULL":"TINYINT(1) NOT NULL DEFAULT 1");
            if(tableExists(c,"recharge_order")){ensure(c,"recharge_order","refund_amount","DECIMAL(10,2) DEFAULT 0 NOT NULL");ensure(c,"recharge_order","recharge_price_per10","DECIMAL(10,2)");}
            jdbc.update("UPDATE user_account SET school_id=0 WHERE school_id IS NULL");
        }
    }
    private void ensure(Connection c,String table,String column,String def)throws Exception{if(exists(c,table,column))return;jdbc.execute("ALTER TABLE "+table+" ADD COLUMN "+column+" "+def);}
    private boolean exists(Connection c,String table,String column)throws Exception{DatabaseMetaData m=c.getMetaData();for(String t:List.of(table,table.toUpperCase()))for(String col:List.of(column,column.toUpperCase()))try(ResultSet r=m.getColumns(null,null,t,col)){if(r.next())return true;}return false;}
    private boolean tableExists(Connection c,String table)throws Exception{DatabaseMetaData m=c.getMetaData();for(String t:List.of(table,table.toUpperCase()))try(ResultSet r=m.getTables(null,null,t,null)){if(r.next())return true;}return false;}
}
