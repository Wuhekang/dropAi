package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SchoolService {
    public static final BigDecimal MIN_RECHARGE_PRICE = new BigDecimal("0.30");
    public static final BigDecimal MAX_RECHARGE_PRICE = new BigDecimal("1000.00");
    public static final BigDecimal DEFAULT_STUDENT_RECHARGE_PRICE = new BigDecimal("2.00");
    public static final BigDecimal DEFAULT_STUDENT_RECHARGE_MIN_PRICE = new BigDecimal("1.00");

    private final SchoolMapper schools;
    private final UserAccountMapper users;
    private final JdbcTemplate jdbc;
    private final PointTransactionMapper transactions;
    private final AccountSecurityService accountSecurity;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SchoolService(SchoolMapper schools, UserAccountMapper users, JdbcTemplate jdbc,
                         PointTransactionMapper transactions, AccountSecurityService accountSecurity) {
        this.schools = schools;
        this.users = users;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.accountSecurity = accountSecurity;
    }

    public UserAccount requireAdmin() {
        UserAccount user = users.selectById(AuthContext.requireUserId());
        if (user == null || user.getDeletedAt() != null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无平台总管理员权限");
        }
        return user;
    }

    public List<Map<String, Object>> list() {
        return list(null);
    }

    public List<Map<String, Object>> list(String keyword) {
        requireAdmin();
        String normalizedKeyword = normalizeKeyword(keyword);
        return schools.selectList(new LambdaQueryWrapper<School>()
                        .isNull(School::getDeletedAt)
                        .orderByDesc(School::getCreatedAt))
                .stream()
                .filter(school -> normalizedKeyword == null
                        ? !Boolean.TRUE.equals(school.getHidden())
                        : matchesSchool(school, normalizedKeyword))
                .map(this::summary).toList();
    }

    @Transactional
    public Map<String, Object> save(Long id, SchoolInput input) {
        requireAdmin();
        validate(input.schoolCode(), input.schoolName());
        School school = id == null ? new School() : requiredForUpdate(id);
        BigDecimal ownPrice = input.rechargePricePer10() == null
                ? (id == null ? MIN_RECHARGE_PRICE : effectivePrice(school))
                : validateRechargePrice(input.rechargePricePer10());
        BigDecimal studentMinPrice = input.studentRechargeMinPricePer10() == null
                ? (id == null ? DEFAULT_STUDENT_RECHARGE_MIN_PRICE : effectiveStudentMinPrice(school))
                : validateStudentRechargeMinPrice(input.studentRechargeMinPricePer10());
        BigDecimal requestedStudentPrice = input.studentRechargePricePer10() == null
                ? (id == null ? DEFAULT_STUDENT_RECHARGE_PRICE : configuredStudentPrice(school))
                : input.studentRechargePricePer10();
        BigDecimal studentPrice = validateStudentRechargePrice(requestedStudentPrice, ownPrice, studentMinPrice);
        School duplicate = schools.selectOne(new LambdaQueryWrapper<School>()
                .eq(School::getSchoolCode, input.schoolCode().trim())
                .isNull(School::getDeletedAt)
                .ne(id != null, School::getId, id));
        if (duplicate != null) throw new IllegalArgumentException("学校编号已存在");

        school.setSchoolCode(input.schoolCode().trim());
        school.setSchoolName(input.schoolName().trim());
        school.setRechargePricePer10(ownPrice);
        school.setStudentRechargePricePer10(studentPrice);
        school.setStudentRechargeMinPricePer10(studentMinPrice);
        if (id == null) {
            school.setEnabled(input.enabled() == null || input.enabled());
            school.setHidden(false);
            school.setCreatedAt(LocalDateTime.now());
        } else if (input.enabled() != null) {
            school.setEnabled(input.enabled());
        }
        school.setUpdatedAt(LocalDateTime.now());
        if (id == null) schools.insert(school); else schools.updateById(school);
        return summary(school);
    }

    @Transactional
    public void enabled(Long id, boolean enabled) {
        requireAdmin();
        School school = requiredForUpdate(id);
        school.setEnabled(enabled);
        school.setUpdatedAt(LocalDateTime.now());
        schools.updateById(school);
    }

    @Transactional
    public void hidden(Long id, Boolean hidden) {
        requireAdmin();
        if (hidden == null) throw new IllegalArgumentException("隐藏状态不能为空");
        School school = requiredForUpdate(id);
        LocalDateTime now = LocalDateTime.now();
        int changed = schools.update(null, new LambdaUpdateWrapper<School>()
                .eq(School::getId, id)
                .isNull(School::getDeletedAt)
                .set(School::getHidden, hidden)
                .set(School::getUpdatedAt, now));
        if (changed != 1) throw new IllegalStateException("学校隐藏状态更新失败");
        school.setHidden(hidden);
        school.setUpdatedAt(now);
    }

    @Transactional
    public Map<String, Object> createViewer(Long schoolId, ViewerInput input) {
        requireAdmin();
        School school = requiredForUpdate(schoolId);
        validatePhonePassword(input.phone(), input.password());
        if (users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, input.phone()).isNull(UserAccount::getDeletedAt)) != null) {
            throw new IllegalArgumentException("账号已存在");
        }
        UserAccount user = new UserAccount();
        user.setPhone(input.phone());
        user.setPasswordHash(encoder.encode(input.password()));
        user.setRole("SCHOOL_VIEWER");
        user.setSchoolId(school.getId());
        user.setAccountEnabled(input.enabled() == null || input.enabled());
        user.setPoints(0);
        user.setTotalPoints(0);
        user.setUsedPoints(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        return viewer(user);
    }

    @Transactional
    public void updateViewer(Long userId, ViewerUpdate input) {
        requireAdmin();
        UserAccount hint = requiredViewer(userId);
        List<Long> schoolIds = new ArrayList<>();
        schoolIds.add(hint.getSchoolId());
        if (input.schoolId() != null && !schoolIds.contains(input.schoolId())) schoolIds.add(input.schoolId());
        schoolIds.sort(Long::compareTo);
        for (Long schoolId : schoolIds) requiredForUpdate(schoolId);
        UserAccount user = users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getId, userId)
                .isNull(UserAccount::getDeletedAt)
                .last("FOR UPDATE"));
        if (user == null || !"SCHOOL_VIEWER".equalsIgnoreCase(user.getRole())
                || !Objects.equals(user.getSchoolId(), hint.getSchoolId())) {
            throw new IllegalStateException("学校统计账号归属已变化，请重试");
        }
        boolean schoolChanged = input.schoolId() != null && !Objects.equals(input.schoolId(), user.getSchoolId());
        LambdaUpdateWrapper<UserAccount> update = new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, userId)
                .isNull(UserAccount::getDeletedAt)
                .set(UserAccount::getUpdatedAt, LocalDateTime.now());
        if (input.schoolId() != null) {
            user.setSchoolId(input.schoolId());
            update.set(UserAccount::getSchoolId, input.schoolId());
        }
        if (input.enabled() != null) {
            user.setAccountEnabled(input.enabled());
            update.set(UserAccount::getAccountEnabled, input.enabled());
        }
        if (users.update(null, update) != 1) throw new IllegalStateException("学校账户更新失败");
        boolean passwordChanged = input.password() != null && !input.password().isBlank();
        if (passwordChanged) {
            accountSecurity.resetPassword(user, input.password());
        } else if (schoolChanged || Boolean.FALSE.equals(input.enabled())) {
            accountSecurity.invalidateSessions(userId);
        }
    }

    public Map<String, Object> viewerStats(String range) {
        UserAccount user = requiredViewer(AuthContext.requireUserId());
        School school = required(user.getSchoolId());
        if (!Boolean.TRUE.equals(user.getAccountEnabled()) || !Boolean.TRUE.equals(school.getEnabled())) {
            throw new IllegalStateException("学校或统计账号已停用");
        }
        int days = "7d".equals(range) ? 7 : "monthly".equals(range) ? 365 : 30;
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schoolName", school.getSchoolName());
        out.put("schoolCode", school.getSchoolCode());
        out.put("rechargePricePer10", effectivePrice(school));
        out.put("studentRechargePricePer10", effectiveStudentPrice(school));
        out.put("studentRechargeMinPricePer10", effectiveStudentMinPrice(school));
        out.put("balance", user.getPoints());
        out.put("totalRechargeAmount", scalarMoney(
                "SELECT COALESCE(SUM(COALESCE(o.pay_amount,o.amount)),0) FROM recharge_order o WHERE o.user_id=? AND o.status IN ('paid','approved')",
                user.getId()));
        out.put("studentGiftPoints", jdbc.queryForObject(
                "SELECT COALESCE(-SUM(pt.points_change),0) FROM point_transactions pt JOIN user_account a ON a.id=pt.user_id WHERE a.school_id=? AND a.role='SCHOOL_VIEWER' AND pt.feature_code='SCHOOL_GIFT_OUT'",
                Long.class, school.getId()));
        out.put("giftTrend", jdbc.queryForList(
                "SELECT CAST(pt.created_at AS DATE) day,COALESCE(-SUM(pt.points_change),0) value FROM point_transactions pt JOIN user_account a ON a.id=pt.user_id WHERE a.school_id=? AND a.role='SCHOOL_VIEWER' AND pt.feature_code='SCHOOL_GIFT_OUT' AND pt.created_at>=? GROUP BY CAST(pt.created_at AS DATE) ORDER BY day",
                school.getId(), from));
        out.put("registrationTrend", jdbc.queryForList(
                "SELECT CAST(created_at AS DATE) day,COUNT(*) value FROM user_account WHERE school_id=? AND role='USER' AND deleted_at IS NULL AND created_at>=? GROUP BY CAST(created_at AS DATE) ORDER BY day",
                school.getId(), from));
        return out;
    }

    public List<Map<String, Object>> students() {
        UserAccount schoolViewer = requiredViewer(AuthContext.requireUserId());
        return users.selectList(new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getSchoolId, schoolViewer.getSchoolId())
                        .eq(UserAccount::getRole, "USER")
                        .isNull(UserAccount::getDeletedAt)
                        .orderByDesc(UserAccount::getCreatedAt))
                .stream().map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("phone", user.getPhone());
                    row.put("points", user.getPoints());
                    row.put("createdAt", user.getCreatedAt());
                    return row;
                }).toList();
    }

    @Transactional
    public Map<String, Object> gift(Long studentId, GiftInput input) {
        LockedViewerSchool locked = lockViewerSchool(AuthContext.requireUserId());
        UserAccount schoolViewer = locked.viewer();
        if (input.points() == null || input.points() <= 0) throw new IllegalArgumentException("赠送积分必须大于0");
        UserAccount student = users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getId, studentId)
                .isNull(UserAccount::getDeletedAt)
                .last("FOR UPDATE"));
        if (student == null || student.getDeletedAt() != null || !"USER".equalsIgnoreCase(student.getRole())
                || !Objects.equals(student.getSchoolId(), schoolViewer.getSchoolId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能向本校用户赠送积分");
        }
        if (users.transferOutPoints(schoolViewer.getId(), input.points()) != 1)
            throw new IllegalStateException("学校账户积分不足");
        if (users.addPoints(studentId, input.points()) != 1) throw new IllegalStateException("赠送积分失败");
        UserAccount senderAfter = users.selectById(schoolViewer.getId());
        UserAccount receiverAfter = users.selectById(studentId);
        String giftId = "SCHOOL-GIFT-" + System.currentTimeMillis();
        writeTransaction(schoolViewer.getId(), giftId, "SCHOOL_GIFT_OUT", "赠送学生积分",
                -input.points(), senderAfter.getPoints(), "赠送至 " + student.getPhone());
        writeTransaction(studentId, giftId, "SCHOOL_GIFT_IN", "学校赠送积分",
                input.points(), receiverAfter.getPoints(), "来自学校账户");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schoolBalance", senderAfter.getPoints());
        out.put("studentBalance", receiverAfter.getPoints());
        out.put("points", input.points());
        return out;
    }

    @Transactional
    public Map<String, Object> updateRechargePrice(PriceInput input) {
        LockedViewerSchool locked = lockViewerSchool(AuthContext.requireUserId());
        School school = locked.school();
        BigDecimal price = validateStudentRechargePrice(input.studentRechargePricePer10(),
                effectivePrice(school), effectiveStudentMinPrice(school));
        LocalDateTime now = LocalDateTime.now();
        int changed = schools.update(null, new LambdaUpdateWrapper<School>()
                .eq(School::getId, school.getId())
                .isNull(School::getDeletedAt)
                .set(School::getStudentRechargePricePer10, price)
                .set(School::getUpdatedAt, now));
        if (changed != 1) throw new IllegalStateException("下级账号充值价格更新失败");
        school.setStudentRechargePricePer10(price);
        school.setUpdatedAt(now);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schoolId", school.getId());
        out.put("rechargePricePer10", effectivePrice(school));
        out.put("studentRechargePricePer10", price);
        out.put("studentRechargeMinPricePer10", effectiveStudentMinPrice(school));
        return out;
    }

    @Transactional
    public Map<String, Object> deleteStudent(Long studentId, DeleteInput input) {
        LockedViewerSchool locked = lockViewerSchool(AuthContext.requireUserId());
        UserAccount schoolViewer = locked.viewer();
        UserAccount student = users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getId, studentId)
                .isNull(UserAccount::getDeletedAt)
                .last("FOR UPDATE"));
        if (student == null || student.getDeletedAt() != null) throw new IllegalArgumentException("用户不存在");
        if (!"USER".equalsIgnoreCase(student.getRole())
                || !Objects.equals(student.getSchoolId(), schoolViewer.getSchoolId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能删除本校普通测试账号");
        }
        validateTargetPassword(student, input);
        Long orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM recharge_order WHERE user_id=?", Long.class, studentId);
        if (orderCount != null && orderCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该账号已有充值订单，不能删除");
        }
        String reason = deleteReason(input, "学校删除测试账号");
        LocalDateTime now = LocalDateTime.now();
        int forfeited = student.getPoints() == null ? 0 : Math.max(0, student.getPoints());
        if (forfeited > 0) {
            writeTransaction(studentId, "DELETE-" + studentId + "-" + System.currentTimeMillis(),
                    "ACCOUNT_DELETION_FORFEIT", "删除测试账号作废余额", -forfeited, 0, "学校删除测试账号");
        }
        String releasedPhone = releasedAccountKey(studentId);
        int changed = users.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, studentId)
                .eq(UserAccount::getSchoolId, schoolViewer.getSchoolId())
                .eq(UserAccount::getRole, "USER")
                .isNull(UserAccount::getDeletedAt)
                .set(UserAccount::getPhone, releasedPhone)
                .set(UserAccount::getAccountEnabled, false)
                .set(UserAccount::getPoints, 0)
                .set(UserAccount::getDeletedAt, now)
                .set(UserAccount::getDeletedBy, schoolViewer.getId())
                .set(UserAccount::getDeleteReason, reason)
                .set(UserAccount::getUpdatedAt, now));
        if (changed != 1) throw new IllegalStateException("测试账号删除失败");
        student.setPhone(releasedPhone);
        student.setAccountEnabled(false);
        student.setPoints(0);
        student.setDeletedAt(now);
        student.setDeletedBy(schoolViewer.getId());
        student.setDeleteReason(reason);
        student.setUpdatedAt(now);
        accountSecurity.invalidateSessions(studentId);
        return Map.of("id", studentId, "deletedAt", now, "forfeitedPoints", forfeited);
    }

    @Transactional
    public Map<String, Object> deleteSchool(Long schoolId, DeleteInput input) {
        UserAccount admin = requireAdmin();
        School school = requiredForUpdate(schoolId);
        Long activeUsers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE school_id=? AND role='USER' AND deleted_at IS NULL",
                Long.class, schoolId);
        if (activeUsers != null && activeUsers > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "学校仍有未删除用户，不能删除");
        }
        Long orders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recharge_order o WHERE o.school_id=? OR EXISTS (SELECT 1 FROM user_account u WHERE u.id=o.user_id AND u.school_id=?)",
                Long.class, schoolId, schoolId);
        if (orders != null && orders > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "学校已有充值订单，不能删除");
        }

        LocalDateTime now = LocalDateTime.now();
        List<UserAccount> viewers = users.selectList(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getSchoolId, schoolId)
                .eq(UserAccount::getRole, "SCHOOL_VIEWER")
                .isNull(UserAccount::getDeletedAt)
                .last("FOR UPDATE"));
        for (UserAccount viewer : viewers) {
            int forfeited = viewer.getPoints() == null ? 0 : Math.max(0, viewer.getPoints());
            if (forfeited > 0) {
                writeTransaction(viewer.getId(), "DELETE-SCHOOL-" + schoolId + "-" + viewer.getId(),
                        "ACCOUNT_DELETION_FORFEIT", "删除测试学校作废余额", -forfeited, 0, "管理员删除测试学校");
            }
            String releasedPhone = releasedAccountKey(viewer.getId());
            String reason = deleteReason(input, "随测试学校删除");
            int changed = users.update(null, new LambdaUpdateWrapper<UserAccount>()
                    .eq(UserAccount::getId, viewer.getId())
                    .eq(UserAccount::getSchoolId, schoolId)
                    .eq(UserAccount::getRole, "SCHOOL_VIEWER")
                    .isNull(UserAccount::getDeletedAt)
                    .set(UserAccount::getPhone, releasedPhone)
                    .set(UserAccount::getAccountEnabled, false)
                    .set(UserAccount::getPoints, 0)
                    .set(UserAccount::getDeletedAt, now)
                    .set(UserAccount::getDeletedBy, admin.getId())
                    .set(UserAccount::getDeleteReason, reason)
                    .set(UserAccount::getUpdatedAt, now));
            if (changed != 1) throw new IllegalStateException("学校账户删除失败");
            viewer.setPhone(releasedPhone);
            viewer.setAccountEnabled(false);
            viewer.setPoints(0);
            viewer.setDeletedAt(now);
            viewer.setDeletedBy(admin.getId());
            viewer.setDeleteReason(reason);
            viewer.setUpdatedAt(now);
            accountSecurity.invalidateSessions(viewer.getId());
        }

        String releasedCode = releasedSchoolCode(schoolId);
        String reason = deleteReason(input, "管理员删除测试学校");
        int changed = schools.update(null, new LambdaUpdateWrapper<School>()
                .eq(School::getId, schoolId)
                .isNull(School::getDeletedAt)
                .set(School::getSchoolCode, releasedCode)
                .set(School::getEnabled, false)
                .set(School::getDeletedAt, now)
                .set(School::getDeletedBy, admin.getId())
                .set(School::getDeleteReason, reason)
                .set(School::getUpdatedAt, now));
        if (changed != 1) throw new IllegalStateException("学校删除失败");
        school.setSchoolCode(releasedCode);
        school.setEnabled(false);
        school.setDeletedAt(now);
        school.setDeletedBy(admin.getId());
        school.setDeleteReason(reason);
        school.setUpdatedAt(now);
        return Map.of("id", schoolId, "deletedAt", now, "deletedViewerCount", viewers.size());
    }

    public BigDecimal validateRechargePrice(BigDecimal rawPrice) {
        if (rawPrice == null) throw new IllegalArgumentException("学校每10积分价格不能为空");
        BigDecimal normalized = rawPrice.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.compareTo(MIN_RECHARGE_PRICE) < 0
                || normalized.compareTo(MAX_RECHARGE_PRICE) > 0) {
            throw new IllegalArgumentException("学校账户每10积分价格必须为0.30-1000元，最多两位小数");
        }
        return normalized.setScale(2, RoundingMode.UNNECESSARY);
    }

    public BigDecimal validateStudentRechargeMinPrice(BigDecimal rawPrice) {
        if (rawPrice == null) throw new IllegalArgumentException("下级账号最低限价不能为空");
        BigDecimal normalized = rawPrice.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.compareTo(MIN_RECHARGE_PRICE) < 0
                || normalized.compareTo(MAX_RECHARGE_PRICE) > 0) {
            throw new IllegalArgumentException("下级账号最低限价必须为0.30-1000元，最多两位小数");
        }
        return normalized.setScale(2, RoundingMode.UNNECESSARY);
    }

    public BigDecimal validateStudentRechargePrice(BigDecimal rawPrice, BigDecimal schoolRechargePrice,
                                                   BigDecimal studentRechargeMinPrice) {
        if (rawPrice == null) throw new IllegalArgumentException("下级账号每10积分价格不能为空");
        BigDecimal minimum = minimumStudentRechargePrice(schoolRechargePrice, studentRechargeMinPrice);
        BigDecimal normalized = rawPrice.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.compareTo(minimum) < 0
                || normalized.compareTo(MAX_RECHARGE_PRICE) > 0) {
            throw new IllegalArgumentException("下级账号每10积分价格必须为"
                    + minimum.toPlainString() + "-1000元，最多两位小数");
        }
        return normalized.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal effectivePrice(School school) {
        BigDecimal configured = school.getRechargePricePer10();
        return configured == null || configured.compareTo(MIN_RECHARGE_PRICE) < 0
                ? MIN_RECHARGE_PRICE : configured.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal effectiveStudentPrice(School school) {
        BigDecimal minimum = minimumStudentRechargePrice(school);
        BigDecimal configured = configuredStudentPrice(school);
        return configured.compareTo(minimum) < 0 ? minimum : configured;
    }

    private BigDecimal minimumStudentRechargePrice(School school) {
        return minimumStudentRechargePrice(effectivePrice(school), effectiveStudentMinPrice(school));
    }

    private BigDecimal minimumStudentRechargePrice(BigDecimal schoolRechargePrice,
                                                   BigDecimal studentRechargeMinPrice) {
        BigDecimal ownPrice = schoolRechargePrice == null
                ? MIN_RECHARGE_PRICE : schoolRechargePrice.max(MIN_RECHARGE_PRICE);
        BigDecimal adminMinimum = studentRechargeMinPrice == null
                ? DEFAULT_STUDENT_RECHARGE_MIN_PRICE : studentRechargeMinPrice.max(MIN_RECHARGE_PRICE);
        return ownPrice.max(adminMinimum).setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal effectiveStudentMinPrice(School school) {
        BigDecimal configured = school.getStudentRechargeMinPricePer10();
        return configured == null ? DEFAULT_STUDENT_RECHARGE_MIN_PRICE
                : validateStudentRechargeMinPrice(configured);
    }

    private BigDecimal configuredStudentPrice(School school) {
        BigDecimal configured = school.getStudentRechargePricePer10();
        if (configured == null) return DEFAULT_STUDENT_RECHARGE_PRICE;
        BigDecimal normalized = configured.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.compareTo(MIN_RECHARGE_PRICE) < 0
                || normalized.compareTo(MAX_RECHARGE_PRICE) > 0) {
            throw new IllegalStateException("下级账号充值价格配置无效");
        }
        return normalized.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void writeTransaction(Long userId, String jobId, String code, String name,
                                  int change, int balance, String remark) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setJobId(jobId);
        transaction.setFeatureCode(code);
        transaction.setFeatureName(name);
        transaction.setPointsChange(change);
        transaction.setBalanceAfter(balance);
        transaction.setRemark(remark);
        transaction.setCreatedAt(LocalDateTime.now());
        transactions.insert(transaction);
    }

    private Map<String, Object> summary(School school) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", school.getId());
        out.put("schoolCode", school.getSchoolCode());
        out.put("schoolName", school.getSchoolName());
        out.put("rechargePricePer10", effectivePrice(school));
        out.put("studentRechargePricePer10", effectiveStudentPrice(school));
        out.put("studentRechargeMinPricePer10", effectiveStudentMinPrice(school));
        out.put("enabled", school.getEnabled());
        out.put("hidden", Boolean.TRUE.equals(school.getHidden()));
        out.put("createdAt", school.getCreatedAt());
        out.put("updatedAt", school.getUpdatedAt());
        out.put("registrationCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE school_id=? AND role='USER' AND deleted_at IS NULL",
                Long.class, school.getId()));
        out.put("totalRechargeAmount", scalarMoney(
                "SELECT COALESCE(SUM(COALESCE(o.pay_amount,o.amount)),0) FROM recharge_order o WHERE o.school_id=? AND o.status IN ('paid','approved')",
                school.getId()));
        out.put("totalRechargePoints", jdbc.queryForObject(
                "SELECT COALESCE(SUM(o.points),0) FROM recharge_order o WHERE o.school_id=? AND o.status IN ('paid','approved')",
                Long.class, school.getId()));
        out.put("viewers", users.selectList(new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getSchoolId, school.getId())
                        .eq(UserAccount::getRole, "SCHOOL_VIEWER")
                        .isNull(UserAccount::getDeletedAt))
                .stream().map(this::viewer).toList());
        return out;
    }

    private Map<String, Object> viewer(UserAccount user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.getId());
        out.put("phone", user.getPhone());
        out.put("schoolId", user.getSchoolId());
        out.put("enabled", !Boolean.FALSE.equals(user.getAccountEnabled()));
        out.put("createdAt", user.getCreatedAt());
        out.put("updatedAt", user.getUpdatedAt());
        return out;
    }

    private BigDecimal scalarMoney(String sql, Long id) {
        return jdbc.queryForObject(sql, BigDecimal.class, id);
    }

    private School required(Long id) {
        School school = schools.selectById(id);
        if (school == null || school.getDeletedAt() != null) throw new IllegalArgumentException("学校不存在");
        return school;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesSchool(School school, String normalizedKeyword) {
        return containsIgnoreCase(school.getSchoolName(), normalizedKeyword)
                || containsIgnoreCase(school.getSchoolCode(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private School requiredForUpdate(Long id) {
        School school = schools.selectOne(new LambdaQueryWrapper<School>()
                .eq(School::getId, id)
                .isNull(School::getDeletedAt)
                .last("FOR UPDATE"));
        if (school == null) throw new IllegalArgumentException("学校不存在");
        return school;
    }

    private UserAccount requiredViewer(Long id) {
        UserAccount user = users.selectById(id);
        if (user == null || user.getDeletedAt() != null || !"SCHOOL_VIEWER".equalsIgnoreCase(user.getRole())
                || user.getSchoolId() == null || user.getSchoolId() == 0) {
            throw new IllegalStateException("学校统计账号无有效学校归属");
        }
        return user;
    }

    private LockedViewerSchool lockViewerSchool(Long viewerId) {
        UserAccount hint = requiredViewer(viewerId);
        School school = requiredForUpdate(hint.getSchoolId());
        UserAccount viewer = users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getId, viewerId)
                .isNull(UserAccount::getDeletedAt)
                .last("FOR UPDATE"));
        if (viewer == null || !"SCHOOL_VIEWER".equalsIgnoreCase(viewer.getRole())
                || !Objects.equals(viewer.getSchoolId(), hint.getSchoolId())) {
            throw new IllegalStateException("学校统计账号归属已变化，请重试");
        }
        if (!Boolean.TRUE.equals(school.getEnabled()) || !Boolean.TRUE.equals(viewer.getAccountEnabled())) {
            throw new IllegalStateException("学校或统计账号已停用");
        }
        return new LockedViewerSchool(viewer, school);
    }

    private void validate(String code, String name) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("学校编号仅允许数字、字母、下划线和连接符");
        if (name == null || name.isBlank() || name.length() > 120)
            throw new IllegalArgumentException("学校名称不能为空且不能超过120字");
    }

    private void validatePhonePassword(String phone, String password) {
        if (phone == null || !phone.matches("\\d{11}")) throw new IllegalArgumentException("请输入11位账号");
        if (password == null || password.length() < 6 || password.length() > 72)
            throw new IllegalArgumentException("密码长度为6-72位");
    }

    private void validateTargetPassword(UserAccount student, DeleteInput input) {
        String currentPassword = input == null ? null : input.currentPassword();
        if (currentPassword == null || currentPassword.length() < 6 || currentPassword.length() > 72
                || student.getPasswordHash() == null
                || !encoder.matches(currentPassword, student.getPasswordHash())) {
            throw new IllegalArgumentException("测试账号密码验证失败");
        }
    }

    private String deleteReason(DeleteInput input, String fallback) {
        if (input == null || input.reason() == null || input.reason().isBlank()) return fallback;
        String reason = input.reason().trim();
        if (reason.length() > 255) throw new IllegalArgumentException("删除原因不能超过255字");
        return reason;
    }

    private String releasedAccountKey(Long id) {
        return "deleted_" + Long.toString(id, 36);
    }

    private String releasedSchoolCode(Long id) {
        return "~deleted:" + Long.toString(id, 36);
    }

    public record SchoolInput(String schoolCode, String schoolName, BigDecimal rechargePricePer10,
                              BigDecimal studentRechargePricePer10,
                              BigDecimal studentRechargeMinPricePer10, Boolean enabled) {}
    public record ViewerInput(String phone, String password, Boolean enabled) {}
    public record ViewerUpdate(Long schoolId, String password, Boolean enabled) {}
    public record GiftInput(Integer points) {}
    public record PriceInput(BigDecimal studentRechargePricePer10) {}
    public record HiddenInput(Boolean hidden) {}
    public record DeleteInput(String reason, String currentPassword) {
        public DeleteInput(String reason) { this(reason, null); }
    }
    private record LockedViewerSchool(UserAccount viewer, School school) {}
}
