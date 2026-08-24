package com.dropai.rewrite.controller;
import com.dropai.rewrite.service.SchoolService;
import com.dropai.rewrite.vo.Result;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class SchoolController {
 private final SchoolService service; public SchoolController(SchoolService service){this.service=service;}
 @GetMapping("/api/admin/schools") public Result<List<Map<String,Object>>> list(){return Result.success(service.list());}
 @PostMapping("/api/admin/schools") public Result<Map<String,Object>> create(@RequestBody SchoolService.SchoolInput in){return Result.success(service.save(null,in));}
 @PutMapping("/api/admin/schools/{id}") public Result<Map<String,Object>> update(@PathVariable Long id,@RequestBody SchoolService.SchoolInput in){return Result.success(service.save(id,in));}
 @PutMapping("/api/admin/schools/{id}/enabled") public Result<Void> enabled(@PathVariable Long id,@RequestBody Map<String,Boolean> in){service.enabled(id,Boolean.TRUE.equals(in.get("enabled")));return Result.success(null);}
 @PostMapping("/api/admin/schools/{id}/viewers") public Result<Map<String,Object>> viewer(@PathVariable Long id,@RequestBody SchoolService.ViewerInput in){return Result.success(service.createViewer(id,in));}
 @PutMapping("/api/admin/school-viewers/{id}") public Result<Void> viewerUpdate(@PathVariable Long id,@RequestBody SchoolService.ViewerUpdate in){service.updateViewer(id,in);return Result.success(null);}
 @GetMapping("/api/school-viewer/statistics") public Result<Map<String,Object>> stats(@RequestParam(defaultValue="30d") String range){return Result.success(service.viewerStats(range));}
 @GetMapping("/api/school-viewer/students") public Result<List<Map<String,Object>>> students(){return Result.success(service.students());}
 @PostMapping("/api/school-viewer/students/{id}/gift") public Result<Map<String,Object>> gift(@PathVariable Long id,@RequestBody SchoolService.GiftInput in){return Result.success(service.gift(id,in));}
}
