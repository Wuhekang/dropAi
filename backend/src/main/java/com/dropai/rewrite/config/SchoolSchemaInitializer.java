package com.dropai.rewrite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
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
            boolean schoolExisted=tableExists(c,"school");
            boolean hadStudentMinimum=schoolExisted&&exists(c,"school","student_recharge_min_price_per10");
            jdbc.execute(h2 ? "CREATE TABLE IF NOT EXISTS school(id BIGINT AUTO_INCREMENT PRIMARY KEY,school_code VARCHAR(64) NOT NULL UNIQUE,school_name VARCHAR(120) NOT NULL,recharge_price_per10 DECIMAL(10,2) DEFAULT 0.30 NOT NULL,student_recharge_price_per10 DECIMAL(10,2) DEFAULT 2.00 NOT NULL,student_recharge_min_price_per10 DECIMAL(10,2) DEFAULT 1.00 NOT NULL,enabled BOOLEAN DEFAULT TRUE NOT NULL,deleted_at TIMESTAMP,deleted_by BIGINT,delete_reason VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)" : "CREATE TABLE IF NOT EXISTS school(id BIGINT PRIMARY KEY AUTO_INCREMENT,school_code VARCHAR(64) NOT NULL,school_name VARCHAR(120) NOT NULL,recharge_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 0.30,student_recharge_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 2.00,student_recharge_min_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 1.00,enabled TINYINT(1) NOT NULL DEFAULT 1,deleted_at DATETIME NULL,deleted_by BIGINT NULL,delete_reason VARCHAR(255) NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_school_code(school_code)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            ensure(c,"school","recharge_price_per10","DECIMAL(10,2) DEFAULT 0.30 NOT NULL");
            ensure(c,"school","student_recharge_price_per10","DECIMAL(10,2) DEFAULT 2.00 NOT NULL");
            ensure(c,"school","student_recharge_min_price_per10","DECIMAL(10,2) DEFAULT 1.00 NOT NULL");
            ensureDefault(c,"school","student_recharge_price_per10","2.00");
            ensureDefault(c,"school","student_recharge_min_price_per10","1.00");
            ensure(c,"school","deleted_at",h2?"TIMESTAMP":"DATETIME NULL");
            ensure(c,"school","deleted_by","BIGINT");
            ensure(c,"school","delete_reason","VARCHAR(255)");
            ensure(c,"user_account","school_id",h2?"BIGINT DEFAULT 0 NOT NULL":"BIGINT NOT NULL DEFAULT 0");
            ensure(c,"user_account","account_enabled",h2?"BOOLEAN DEFAULT TRUE NOT NULL":"TINYINT(1) NOT NULL DEFAULT 1");
            ensure(c,"user_account","deleted_at",h2?"TIMESTAMP":"DATETIME NULL");
            ensure(c,"user_account","deleted_by","BIGINT");
            ensure(c,"user_account","delete_reason","VARCHAR(255)");
            if(tableExists(c,"recharge_order")){ensure(c,"recharge_order","refund_amount","DECIMAL(10,2) DEFAULT 0 NOT NULL");ensure(c,"recharge_order","recharge_price_per10","DECIMAL(10,2)");}
            jdbc.update("UPDATE school SET recharge_price_per10=0.30 WHERE recharge_price_per10 IS NULL OR recharge_price_per10<0.30");
            jdbc.update("UPDATE school SET student_recharge_min_price_per10=CASE WHEN student_recharge_min_price_per10 IS NULL THEN 1.00 WHEN student_recharge_min_price_per10<0.30 THEN 0.30 ELSE student_recharge_min_price_per10 END");
            if(!hadStudentMinimum){
                jdbc.update("UPDATE school SET student_recharge_price_per10=GREATEST(COALESCE(student_recharge_price_per10,2.00),2.00,recharge_price_per10,student_recharge_min_price_per10)");
            }else{
                jdbc.update("UPDATE school SET student_recharge_price_per10=CASE WHEN student_recharge_price_per10 IS NULL OR student_recharge_price_per10<GREATEST(recharge_price_per10,student_recharge_min_price_per10,0.30) THEN GREATEST(2.00,recharge_price_per10,student_recharge_min_price_per10,0.30) ELSE student_recharge_price_per10 END");
            }
            jdbc.update("UPDATE user_account SET school_id=0 WHERE school_id IS NULL");
        }
    }
    private void ensure(Connection c,String table,String column,String def)throws Exception{
        if(exists(c,table,column))return;
        try {
            jdbc.execute("ALTER TABLE "+table+" ADD COLUMN "+column+" "+def);
        } catch (DataAccessException exception) {
            if (!isDuplicateColumn(exception)) throw exception;
        }
    }
    private boolean isDuplicateColumn(Throwable failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
            if(cause instanceof SQLException sqlException){
                String state=sqlException.getSQLState();
                int code=sqlException.getErrorCode();
                if("42S21".equalsIgnoreCase(state)||code==1060||code==42121)return true;
            }
        }
        return false;
    }
    private boolean exists(Connection c,String table,String column)throws Exception{DatabaseMetaData m=c.getMetaData();for(String t:List.of(table,table.toUpperCase()))for(String col:List.of(column,column.toUpperCase()))try(ResultSet r=m.getColumns(null,null,t,col)){if(r.next())return true;}return false;}
    private void ensureDefault(Connection c,String table,String column,String expected)throws Exception{
        DatabaseMetaData metadata=c.getMetaData();
        for(String t:List.of(table,table.toUpperCase()))for(String col:List.of(column,column.toUpperCase()))try(ResultSet r=metadata.getColumns(null,null,t,col)){
            if(r.next()){
                String current=r.getString("COLUMN_DEF");
                try{if(current!=null&&new java.math.BigDecimal(current.replace("'","")).compareTo(new java.math.BigDecimal(expected))==0)return;}catch(NumberFormatException ignored){}
                jdbc.execute("ALTER TABLE "+table+" ALTER COLUMN "+column+" SET DEFAULT "+expected);
                return;
            }
        }
    }
    private boolean tableExists(Connection c,String table)throws Exception{DatabaseMetaData m=c.getMetaData();for(String t:List.of(table,table.toUpperCase()))try(ResultSet r=m.getTables(null,null,t,null)){if(r.next())return true;}return false;}
}
