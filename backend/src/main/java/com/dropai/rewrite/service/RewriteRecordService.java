package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dropai.rewrite.dto.RewriteSubmitDTO;
import com.dropai.rewrite.entity.RewriteRecord;
import com.dropai.rewrite.vo.AiAnalyzeVO;
import com.dropai.rewrite.vo.RewriteResultVO;

import java.util.List;

public interface RewriteRecordService extends IService<RewriteRecord> {

    RewriteResultVO submit(RewriteSubmitDTO dto);

    AiAnalyzeVO analyze(String originalText);

    List<RewriteResultVO> listRecords();

    RewriteResultVO detail(Long id);

    boolean deleteRecord(Long id);
}
