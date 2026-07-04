package com.dropai.rewrite.vo;

import com.dropai.rewrite.entity.SystemNotice;

import java.time.LocalDateTime;

public record SystemNoticeVO(Long id, String title, String content, String status, Boolean isPopup,
                             Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static SystemNoticeVO of(SystemNotice notice) {
        return new SystemNoticeVO(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getStatus(), notice.getIsPopup(), notice.getCreatedBy(),
                notice.getCreatedAt(), notice.getUpdatedAt());
    }
}
