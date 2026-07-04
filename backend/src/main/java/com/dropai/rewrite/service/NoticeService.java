package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.dto.SystemNoticeDTO;
import com.dropai.rewrite.entity.SystemNotice;
import com.dropai.rewrite.entity.UserAccount;
import com.dropai.rewrite.mapper.SystemNoticeMapper;
import com.dropai.rewrite.mapper.UserAccountMapper;
import com.dropai.rewrite.vo.SystemNoticeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NoticeService {
    private final SystemNoticeMapper noticeMapper;
    private final UserAccountMapper userMapper;

    public NoticeService(SystemNoticeMapper noticeMapper, UserAccountMapper userMapper) {
        this.noticeMapper = noticeMapper;
        this.userMapper = userMapper;
    }

    public SystemNoticeVO latestPopup() {
        Long userId = AuthContext.requireUserId();
        UserAccount user = requireUser(userId);
        if ("ADMIN".equalsIgnoreCase(user.getRole())) return null;
        SystemNotice notice = noticeMapper.selectOne(new LambdaQueryWrapper<SystemNotice>()
                .eq(SystemNotice::getStatus, "active")
                .eq(SystemNotice::getIsPopup, true)
                .orderByDesc(SystemNotice::getUpdatedAt)
                .last("LIMIT 1"));
        if (notice == null) return null;
        if (notice.getId() != null && notice.getId().equals(user.getNoticeReadId())
                && user.getLastNoticeTime() != null
                && user.getLastNoticeTime().isAfter(LocalDateTime.now().minusHours(24))) {
            return null;
        }
        return SystemNoticeVO.of(notice);
    }

    @Transactional
    public boolean markRead(Long noticeId) {
        userMapper.markNoticeRead(AuthContext.requireUserId(), noticeId);
        return true;
    }

    public List<SystemNoticeVO> adminList() {
        requireAdmin();
        return noticeMapper.selectList(new LambdaQueryWrapper<SystemNotice>()
                        .orderByDesc(SystemNotice::getUpdatedAt)
                        .last("LIMIT 50"))
                .stream().map(SystemNoticeVO::of).toList();
    }

    public SystemNoticeVO adminLatest() {
        requireAdmin();
        SystemNotice notice = noticeMapper.selectOne(new LambdaQueryWrapper<SystemNotice>()
                .orderByDesc(SystemNotice::getUpdatedAt)
                .last("LIMIT 1"));
        return notice == null ? null : SystemNoticeVO.of(notice);
    }

    @Transactional
    public SystemNoticeVO save(SystemNoticeDTO dto) {
        requireAdmin();
        SystemNotice notice = dto.getId() == null ? null : noticeMapper.selectById(dto.getId());
        if (notice == null) {
            notice = new SystemNotice();
            notice.setCreatedBy(AuthContext.requireUserId());
            notice.setCreatedAt(LocalDateTime.now());
        }
        apply(notice, dto);
        notice.setUpdatedAt(LocalDateTime.now());
        if (notice.getId() == null) {
            noticeMapper.insert(notice);
        } else {
            noticeMapper.updateById(notice);
        }
        return SystemNoticeVO.of(notice);
    }

    @Transactional
    public SystemNoticeVO publishById(Long id) {
        requireAdmin();
        SystemNotice notice = noticeMapper.selectById(id);
        if (notice == null) throw new IllegalArgumentException("\u516c\u544a\u4e0d\u5b58\u5728");
        notice.setStatus("active");
        notice.setIsPopup(true);
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
        return SystemNoticeVO.of(notice);
    }

    @Transactional
    public SystemNoticeVO publish(SystemNoticeDTO dto) {
        requireAdmin();
        SystemNotice notice = new SystemNotice();
        apply(notice, dto);
        notice.setCreatedBy(AuthContext.requireUserId());
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
        return SystemNoticeVO.of(notice);
    }

    @Transactional
    public SystemNoticeVO update(Long id, SystemNoticeDTO dto) {
        requireAdmin();
        SystemNotice notice = noticeMapper.selectById(id);
        if (notice == null) throw new IllegalArgumentException("\u516c\u544a\u4e0d\u5b58\u5728");
        apply(notice, dto);
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
        return SystemNoticeVO.of(notice);
    }

    private void apply(SystemNotice notice, SystemNoticeDTO dto) {
        notice.setTitle(nonBlank(dto.getTitle(), "DropAI \u7cfb\u7edf\u516c\u544a"));
        notice.setContent(nonBlank(dto.getContent(), "# DropAI \u7cfb\u7edf\u516c\u544a\n\n\u6b22\u8fce\u4f7f\u7528 DropAI\u3002"));
        notice.setStatus(nonBlank(dto.getStatus(), "active"));
        notice.setIsPopup(dto.getIsPopup() == null || dto.getIsPopup());
    }

    private void requireAdmin() {
        UserAccount user = requireUser(AuthContext.requireUserId());
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("\u65e0\u7ba1\u7406\u5458\u6743\u9650");
        }
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) throw new IllegalStateException("\u7528\u6237\u4e0d\u5b58\u5728");
        return user;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
