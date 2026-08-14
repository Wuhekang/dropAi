package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SchoolService {
    private final SchoolMapper schools; private final UserAccountMapper users; private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    public SchoolService(SchoolMapper schools,UserAccountMapper users,JdbcTemplate jdbc){this.schools=schools;this.users=users;this.jdbc=jdbc;}
    public UserAccount requireAdmin(){UserAccount u=users.selectById(AuthContext.requireUserId());if(u==null||!"ADMIN".equalsIgnoreCase(u.getRole()))throw new IllegalStateException("无平台总管理员权限");return u;}
    public List<Map<String,Object>> list(){requireAdmin();return schools.selectList(new LambdaQueryWrapper<School>().orderByDesc(School::getCreatedAt)).stream().map(this::summary).toList();}
    @Transactional public Map<String,Object> save(Long id,SchoolInput in){requireAdmin();validate(in.schoolCode(),in.schoolName());School duplicate=schools.selectOne(new LambdaQueryWrapper<School>().eq(School::getSchoolCode,in.schoolCode().trim()).ne(id!=null,School::getId,id));if(duplicate!=null)throw new IllegalArgumentException("学校编号已存在");School s=id==null?new School():required(id);s.setSchoolCode(in.schoolCode().trim());s.setSchoolName(in.schoolName().trim());s.setEnabled(in.enabled()==null||in.enabled());s.setUpdatedAt(LocalDateTime.now());if(id==null){s.setCreatedAt(LocalDateTime.now());schools.insert(s);}else schools.updateById(s);return summary(s);}
    @Transactional public void enabled(Long id,boolean enabled){requireAdmin();School s=required(id);s.setEnabled(enabled);s.setUpdatedAt(LocalDateTime.now());schools.updateById(s);}
    @Transactional public Map<String,Object> createViewer(Long schoolId,ViewerInput in){requireAdmin();School s=required(schoolId);validatePhonePassword(in.phone(),in.password());if(users.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone,in.phone()))!=null)throw new IllegalArgumentException("账号已存在");UserAccount u=new UserAccount();u.setPhone(in.phone());u.setPasswordHash(encoder.encode(in.password()));u.setRole("SCHOOL_VIEWER");u.setSchoolId(s.getId());u.setAccountEnabled(in.enabled()==null||in.enabled());u.setPoints(0);u.setTotalPoints(0);u.setUsedPoints(0);u.setCreatedAt(LocalDateTime.now());u.setUpdatedAt(LocalDateTime.now());users.insert(u);return viewer(u);}
    @Transactional public void updateViewer(Long userId,ViewerUpdate in){requireAdmin();UserAccount u=requiredViewer(userId);if(in.schoolId()!=null)required(in.schoolId());u.setSchoolId(in.schoolId()==null?u.getSchoolId():in.schoolId());u.setAccountEnabled(in.enabled()==null?u.getAccountEnabled():in.enabled());if(in.password()!=null&&!in.password().isBlank()){if(in.password().length()<6||in.password().length()>72)throw new IllegalArgumentException("密码长度为6-72位");u.setPasswordHash(encoder.encode(in.password()));}u.setUpdatedAt(LocalDateTime.now());users.updateById(u);}
    public Map<String,Object> viewerStats(String range){UserAccount u=requiredViewer(AuthContext.requireUserId());School s=required(u.getSchoolId());if(!Boolean.TRUE.equals(u.getAccountEnabled())||!Boolean.TRUE.equals(s.getEnabled()))throw new IllegalStateException("学校或统计账号已停用");int days="7d".equals(range)?7:"monthly".equals(range)?365:30;LocalDate from=LocalDate.now().minusDays(days-1L);Map<String,Object> out=new LinkedHashMap<>();out.put("schoolName",s.getSchoolName());out.put("schoolCode",s.getSchoolCode());out.put("totalRechargeAmount",scalarMoney("SELECT COALESCE(SUM(COALESCE(o.pay_amount,o.amount)-COALESCE(o.refund_amount,0)),0) FROM recharge_order o WHERE o.school_id=? AND o.status IN ('paid','refunded')",s.getId()));out.put("rechargeTrend",jdbc.queryForList("SELECT CAST(o.paid_at AS DATE) day,COALESCE(SUM(COALESCE(o.pay_amount,o.amount)-COALESCE(o.refund_amount,0)),0) value FROM recharge_order o WHERE o.school_id=? AND o.status IN ('paid','refunded') AND o.paid_at>=? GROUP BY CAST(o.paid_at AS DATE) ORDER BY day",s.getId(),from));out.put("registrationTrend",jdbc.queryForList("SELECT CAST(created_at AS DATE) day,COUNT(*) value FROM user_account WHERE school_id=? AND role='USER' AND created_at>=? GROUP BY CAST(created_at AS DATE) ORDER BY day",s.getId(),from));return out;}
    private Map<String,Object> summary(School s){Map<String,Object> m=new LinkedHashMap<>();m.put("id",s.getId());m.put("schoolCode",s.getSchoolCode());m.put("schoolName",s.getSchoolName());m.put("enabled",s.getEnabled());m.put("createdAt",s.getCreatedAt());m.put("updatedAt",s.getUpdatedAt());m.put("registrationCount",jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE school_id=? AND role='USER'",Long.class,s.getId()));m.put("totalRechargeAmount",scalarMoney("SELECT COALESCE(SUM(COALESCE(o.pay_amount,o.amount)-COALESCE(o.refund_amount,0)),0) FROM recharge_order o WHERE o.school_id=? AND o.status IN ('paid','refunded')",s.getId()));m.put("totalRechargePoints",jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN o.status='paid' THEN o.points WHEN o.status='refunded' AND COALESCE(o.pay_amount,o.amount)>0 THEN ROUND(o.points*(1-COALESCE(o.refund_amount,0)/COALESCE(o.pay_amount,o.amount))) ELSE 0 END),0) FROM recharge_order o WHERE o.school_id=? AND o.status IN ('paid','refunded')",Long.class,s.getId()));m.put("viewers",users.selectList(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getSchoolId,s.getId()).eq(UserAccount::getRole,"SCHOOL_VIEWER")).stream().map(this::viewer).toList());return m;}
    private Map<String,Object> viewer(UserAccount u){return Map.of("id",u.getId(),"phone",u.getPhone(),"schoolId",u.getSchoolId(),"enabled",!Boolean.FALSE.equals(u.getAccountEnabled()));}
    private BigDecimal scalarMoney(String sql,Long id){return jdbc.queryForObject(sql,BigDecimal.class,id);}
    private School required(Long id){School s=schools.selectById(id);if(s==null)throw new IllegalArgumentException("学校不存在");return s;}
    private UserAccount requiredViewer(Long id){UserAccount u=users.selectById(id);if(u==null||!"SCHOOL_VIEWER".equalsIgnoreCase(u.getRole())||u.getSchoolId()==null||u.getSchoolId()==0)throw new IllegalStateException("学校统计账号无有效学校归属");return u;}
    private void validate(String c,String n){if(c==null||!c.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("学校编号仅允许数字、字母、下划线和连接符");if(n==null||n.isBlank()||n.length()>120)throw new IllegalArgumentException("学校名称不能为空且不能超过120字");}
    private void validatePhonePassword(String p,String pw){if(p==null||!p.matches("\\d{11}"))throw new IllegalArgumentException("请输入11位账号");if(pw==null||pw.length()<6||pw.length()>72)throw new IllegalArgumentException("密码长度为6-72位");}
    public record SchoolInput(String schoolCode,String schoolName,Boolean enabled){} public record ViewerInput(String phone,String password,Boolean enabled){} public record ViewerUpdate(Long schoolId,String password,Boolean enabled){}
}
