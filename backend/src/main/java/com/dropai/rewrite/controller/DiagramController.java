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
    @PostMapping("/validate") public Result<JsonNode> validate(@RequestBody DslRequest r){return Result.success(service.validate(r.dsl()));}
    @PostMapping("/render") public Result<JsonNode> render(@RequestBody DslRequest r){return Result.success(service.render(r.dsl()));}
    @PostMapping("/ai/generate") public Result<JsonNode> generate(@RequestBody AiGenerateRequest r){return Result.success(service.aiGenerate(r.diagramType(),r.description()));}
    @PostMapping("/ai/review") public Result<JsonNode> review(@RequestBody DslRequest r){return Result.success(service.aiReview(r.dsl()));}
    @PostMapping("/export") public ResponseEntity<byte[]> export(@RequestBody ExportRequest r){var f=service.export(r.dsl(),r.format());return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.name(), StandardCharsets.UTF_8).build().toString()).body(f.content());}
    @PostMapping("/projects") public Result<Long> save(@RequestBody SaveRequest r){return Result.success(service.save(r.id(),r.title(),r.dsl()));}
    @GetMapping("/projects") public Result<List<Map<String,Object>>> projects(){return Result.success(service.projects());}
    public record DslRequest(String dsl){} public record ExportRequest(String dsl,String format){} public record AiGenerateRequest(String diagramType,String description){} public record SaveRequest(Long id,String title,String dsl){}
}
