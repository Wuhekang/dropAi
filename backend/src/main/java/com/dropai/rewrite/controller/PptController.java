package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.PointsNotEnoughException;
import com.dropai.rewrite.service.ppt.PptProjectService;
import com.dropai.rewrite.vo.Result;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ppt")
public class PptController {
    private final PptProjectService service;
    public PptController(PptProjectService service){this.service=service;}

    @GetMapping("/projects") public Result<List<Map<String,Object>>> list(){return Result.success(service.list());}
    @PostMapping("/projects") public Result<Map<String,Object>> create(@RequestBody Map<String,Object> input){return Result.success(service.create(input));}
    @GetMapping("/projects/{id}") public Result<Map<String,Object>> get(@PathVariable String id){return Result.success(service.get(id));}
    @PostMapping("/projects/{id}/upload") public Result<Map<String,Object>> upload(@PathVariable String id,@RequestParam("file") MultipartFile file)throws Exception{return Result.success(service.upload(id,file));}
    @PostMapping("/projects/{id}/analyze") public Result<Map<String,Object>> analyze(@PathVariable String id)throws Exception{return Result.success(service.analyze(id));}
    @PostMapping("/projects/{id}/outline") public Result<Map<String,Object>> outline(@PathVariable String id){return Result.success(service.generateOutline(id));}
    @PutMapping("/projects/{id}/outline") public Result<Map<String,Object>> saveOutline(@PathVariable String id,@RequestBody List<Map<String,Object>> items){return Result.success(service.saveOutline(id,items));}
    @PostMapping("/projects/{id}/plan") public Result<Map<String,Object>> plan(@PathVariable String id){return Result.success(service.plan(id));}
    @PutMapping("/projects/{id}/slides/{slideId}") public Result<Map<String,Object>> updateSlide(@PathVariable String id,@PathVariable String slideId,@RequestBody Map<String,Object> input){return Result.success(service.updateSlide(id,slideId,input));}
    @PostMapping("/projects/{id}/slides/{slideId}/regenerate") public Result<Map<String,Object>> regenerate(@PathVariable String id,@PathVariable String slideId){return Result.success(service.regenerateSlide(id,slideId));}
    @PostMapping("/projects/{id}/generate") public Result<Map<String,Object>> generate(@PathVariable String id)throws Exception{return Result.success(service.generate(id));}
    @GetMapping("/projects/{id}/progress") public Result<Map<String,Object>> progress(@PathVariable String id){return Result.success(service.progress(id));}
    @GetMapping("/projects/{id}/download") public ResponseEntity<FileSystemResource> download(@PathVariable String id)throws Exception{FileSystemResource file=service.download(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")).contentLength(file.contentLength()).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(service.downloadName(id),StandardCharsets.UTF_8).build().toString()).body(file);}

    @org.springframework.web.bind.annotation.ExceptionHandler(PointsNotEnoughException.class)
    public Result<?> points(PointsNotEnoughException e){return Result.fail("PAY_REQUIRED","积分不足，请前往积分中心充值",e.toResponse());}
}
