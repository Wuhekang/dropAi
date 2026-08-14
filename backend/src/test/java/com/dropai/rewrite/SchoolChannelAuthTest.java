package com.dropai.rewrite;

import com.dropai.rewrite.dto.PhoneAuthDTO;
import com.dropai.rewrite.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:school-auth;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
class SchoolChannelAuthTest {
 @Autowired AuthService auth; @Autowired JdbcTemplate jdbc;
 @BeforeEach void seed(){jdbc.update("DELETE FROM user_session");jdbc.update("DELETE FROM user_account");jdbc.update("DELETE FROM school");jdbc.update("INSERT INTO school(school_code,school_name,enabled,created_at,updated_at) VALUES('GXDX2026','广西大学',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");jdbc.update("INSERT INTO school(school_code,school_name,enabled,created_at,updated_at) VALUES('OFF','停用学校',FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");}
 @Test void ordinaryRegistrationIsUnbound(){var result=auth.register(dto("13800000001",null));assertEquals(0L,result.schoolId());assertNull(result.schoolName());}
 @Test void validCollegeBindsByInternalId(){var result=auth.register(dto("13800000002","GXDX2026"));assertEquals("广西大学",result.schoolName());assertTrue(result.schoolId()>0);}
 @Test void invalidOrDisabledCollegeCannotBind(){assertThrows(IllegalArgumentException.class,()->auth.register(dto("13800000003","UNKNOWN")));assertThrows(IllegalArgumentException.class,()->auth.register(dto("13800000004","OFF")));}
 @Test void loginLinkCannotRebindExistingUser(){auth.register(dto("13800000005",null));var login=dto("13800000005","GXDX2026");var result=auth.login(login);assertEquals(0L,result.schoolId());}
 private PhoneAuthDTO dto(String phone,String college){PhoneAuthDTO d=new PhoneAuthDTO();d.setPhone(phone);d.setPassword("secret12");d.setCollege(college);return d;}
}
