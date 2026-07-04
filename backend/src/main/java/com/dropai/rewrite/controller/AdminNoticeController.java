package com.dropai.rewrite.controller;

import com.dropai.rewrite.dto.SystemNoticeDTO;
import com.dropai.rewrite.service.NoticeService;
import com.dropai.rewrite.vo.Result;
import com.dropai.rewrite.vo.SystemNoticeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notice")
public class AdminNoticeController {
    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/latest")
    public Result<SystemNoticeVO> latest() {
        return Result.success(noticeService.adminLatest());
    }

    @PostMapping("/save")
    public Result<SystemNoticeVO> save(@RequestBody SystemNoticeDTO dto) {
        return Result.success(noticeService.save(dto));
    }

    @PostMapping("/publish/{id}")
    public Result<SystemNoticeVO> publish(@PathVariable Long id) {
        return Result.success(noticeService.publishById(id));
    }
}
