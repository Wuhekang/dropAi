package com.dropai.rewrite.controller;

import com.dropai.rewrite.service.DiagramService;
import com.dropai.rewrite.vo.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/diagram")
public class DiagramController {
    private final DiagramService service;
    public DiagramController(DiagramService service){this.service=service;}
    @GetMapping("/health") public Result<Map<String,Object>> health(){return Result.success(service.health());}
    @PostMapping("/validate") public Result<JsonNode> validate(@RequestBody DslRequest r){return Result.success(service.validate(r.dsl()));}
    @PostMapping("/render") public Result<Map<String,Object>> render(@RequestBody RenderRequest r){return Result.success(service.render(r.projectId(),r.dsl()));}
    @GetMapping("/previews/{previewId}/download/{format}") public ResponseEntity<byte[]> download(@PathVariable String previewId,@PathVariable String format){var f=service.download(previewId,format);return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.name(),StandardCharsets.UTF_8).build().toString()).body(f.content());}
    @GetMapping("/previews/{previewId}") public Result<Map<String,Object>> preview(@PathVariable String previewId){return Result.success(service.preview(previewId));}
    @PostMapping("/projects") public Result<Long> save(@RequestBody SaveRequest r){return Result.success(service.save(r.id(),r.title(),r.dsl()));}
    @GetMapping("/projects") public Result<List<Map<String,Object>>> projects(){return Result.success(service.projects());}
    public record DslRequest(String dsl,String source){public String sourceText(){return source!=null?source:dsl;}} public record RenderRequest(Long projectId,String dsl){} public record SaveRequest(Long id,String title,String dsl){}
}
