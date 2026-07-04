package com.dropai.rewrite.controller;

import com.dropai.rewrite.dto.SystemNoticeDTO;
import com.dropai.rewrite.service.NoticeService;
import com.dropai.rewrite.vo.Result;
import com.dropai.rewrite.vo.SystemNoticeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/latest")
    public Result<SystemNoticeVO> latest() {
        return Result.success(noticeService.latestPopup());
    }

    @PostMapping("/{noticeId}/read")
    public Result<Boolean> read(@PathVariable Long noticeId) {
        return Result.success(noticeService.markRead(noticeId));
    }

    @GetMapping("/admin")
    public Result<List<SystemNoticeVO>> adminList() {
        return Result.success(noticeService.adminList());
    }

    @PostMapping("/admin")
    public Result<SystemNoticeVO> publish(@RequestBody SystemNoticeDTO dto) {
        return Result.success(noticeService.publish(dto));
    }

    @PutMapping("/admin/{id}")
    public Result<SystemNoticeVO> update(@PathVariable Long id, @RequestBody SystemNoticeDTO dto) {
        return Result.success(noticeService.update(id, dto));
    }
}
