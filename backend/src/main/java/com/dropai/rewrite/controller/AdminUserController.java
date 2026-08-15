package com.dropai.rewrite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.PointTransaction;
import com.dropai.rewrite.entity.RechargeOrder;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.entity.School;
import com.dropai.rewrite.mapper.PointTransactionMapper;
import com.dropai.rewrite.mapper.RechargeOrderMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.mapper.SchoolMapper;
import com.dropai.rewrite.vo.Result;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserAccountMapper userMapper;
    private final PointTransactionMapper transactionMapper;
    private final RechargeOrderMapper orderMapper;
    private final SchoolMapper schoolMapper;

    public AdminUserController(UserAccountMapper userMapper, PointTransactionMapper transactionMapper,
                               RechargeOrderMapper orderMapper, SchoolMapper schoolMapper) {
        this.userMapper = userMapper;
        this.transactionMapper = transactionMapper;
        this.orderMapper = orderMapper;
        this.schoolMapper = schoolMapper;
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> orders() {
        requireAdmin();
        return Result.success(orderMapper.selectList(new LambdaQueryWrapper<RechargeOrder>()
                        .orderByDesc(RechargeOrder::getCreatedAt).last("LIMIT 200"))
                .stream().map(order -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    UserAccount user = userMapper.selectById(order.getUserId());
                    view.put("orderNo", order.getOrderNo());
                    view.put("userId", order.getUserId());
                    view.put("phone", user == null ? "--" : user.getPhone());
                    view.put("amount", order.getAmount());
                    view.put("points", order.getPoints());
                    view.put("status", order.getStatus());
                    view.put("createdAt", order.getCreatedAt());
                    view.put("paidAt", order.getPaidAt());
                    return view;
                }).toList());
    }

    @GetMapping
    public Result<List<Map<String, Object>>> users(@RequestParam(required = false) String school,
                                                   @RequestParam(required = false) String keyword) {
        requireAdmin();
        return Result.success(userMapper.selectList(new LambdaQueryWrapper<UserAccount>().orderByDesc(UserAccount::getCreatedAt))
                .stream().map(this::userView)
                .filter(v -> school == null || school.isBlank() || "all".equalsIgnoreCase(school)
                        || ("unbound".equalsIgnoreCase(school) && ((Number)v.get("schoolId")).longValue() == 0)
                        || String.valueOf(v.get("schoolId")).equals(school))
                .filter(v -> keyword == null || keyword.isBlank() || (String.valueOf(v.get("phone"))+" "+v.get("schoolName")+" "+v.get("schoolCode")).toLowerCase().contains(keyword.toLowerCase()))
                .toList());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        requireAdmin();
        UserAccount user = requireUser(id);
        List<PointTransaction> transactions = transactionMapper.selectList(new LambdaQueryWrapper<PointTransaction>()
                .eq(PointTransaction::getUserId, id).orderByDesc(PointTransaction::getCreatedAt).last("LIMIT 100"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", userView(user));
        result.put("transactions", transactions);
        return Result.success(result);
    }

    @PostMapping("/{id}/points-adjust")
    @Transactional
    public Result<Map<String, Object>> adjust(@PathVariable Long id, @RequestBody PointAdjustment request) {
        requireAdmin();
        if (request.quantity() == null || request.quantity() <= 0) throw new IllegalArgumentException("积分数量必须大于 0");
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("必须填写调整原因");
        if (request.remark() == null || request.remark().isBlank()) throw new IllegalArgumentException("必须填写备注");
        UserAccount before = requireUser(id);
        boolean deduct = "DEDUCT".equalsIgnoreCase(request.type());
        int signed = deduct ? -request.quantity() : request.quantity();
        int changed = deduct ? userMapper.deductPoints(id, request.quantity()) : userMapper.addPoints(id, request.quantity());
        if (changed != 1) throw new IllegalStateException(deduct ? "用户积分不足，无法扣除" : "积分调整失败");
        UserAccount after = requireUser(id);
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(id);
        transaction.setJobId("ADMIN-" + System.currentTimeMillis());
        transaction.setFeatureCode("ADMIN_ADJUSTMENT");
        transaction.setFeatureName(deduct ? "管理员扣除积分" : "管理员增加积分");
        transaction.setPointsChange(signed);
        transaction.setBalanceAfter(after.getPoints());
        transaction.setRemark(request.reason().trim() + "｜" + request.remark().trim());
        transaction.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beforePoints", before.getPoints());
        result.put("afterPoints", after.getPoints());
        result.put("change", signed);
        return Result.success(result);
    }

    private void requireAdmin() {
        UserAccount current = requireUser(AuthContext.requireUserId());
        if (!"ADMIN".equalsIgnoreCase(current.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无管理员权限");
        }
    }

    private UserAccount requireUser(Long id) {
        UserAccount user = userMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return user;
    }

    private Map<String, Object> userView(UserAccount user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("phone", user.getPhone());
        view.put("role", user.getRole());
        long schoolId = user.getSchoolId() == null ? 0L : user.getSchoolId();
        School school = schoolId == 0 ? null : schoolMapper.selectById(schoolId);
        view.put("schoolId", schoolId);
        view.put("schoolName", school == null ? null : school.getSchoolName());
        view.put("schoolCode", school == null ? null : school.getSchoolCode());
        view.put("ownershipType", school == null ? "普通用户/未绑定学校" : "学校用户");
        view.put("points", user.getPoints());
        view.put("totalPoints", user.getTotalPoints());
        view.put("usedPoints", user.getUsedPoints());
        view.put("createdAt", user.getCreatedAt());
        view.put("updatedAt", user.getUpdatedAt());
        view.put("status", "ACTIVE");
        return view;
    }

    public record PointAdjustment(String type, Integer quantity, String reason, String remark) {}
}
