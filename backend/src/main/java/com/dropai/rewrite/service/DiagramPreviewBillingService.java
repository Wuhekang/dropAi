package com.dropai.rewrite.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DiagramPreviewBillingService {
    private final JdbcTemplate jdbc;
    private final PointService points;

    public DiagramPreviewBillingService(JdbcTemplate jdbc, PointService points) { this.jdbc=jdbc; this.points=points; }

    public Map<String,Object> success(Long userId, Long projectId, String hash) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM diagram_preview WHERE user_id=? AND project_id=? AND render_hash=? AND status='SUCCESS'",userId,projectId,hash);
        return rows.isEmpty()?null:rows.get(0);
    }

    public Map<String,Object> successByDsl(Long userId,Long projectId,String type,String normalizedDsl) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM diagram_preview WHERE user_id=? AND project_id=? AND diagram_type=? AND normalized_dsl=? AND status='SUCCESS' ORDER BY updated_at DESC LIMIT 1",userId,projectId,type,normalizedDsl);
        return rows.isEmpty()?null:rows.get(0);
    }

    public String createTask(Long userId,Long projectId,String type,String hash,String rendererVersion) {
        String id=UUID.randomUUID().toString().replace("-",""); LocalDateTime now=LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO diagram_render_task(id,user_id,project_id,diagram_type,render_hash,renderer_version,status,created_at,updated_at) VALUES(?,?,?,?,?,?,'RENDERING',?,?)",id,userId,projectId,type,hash,rendererVersion,now,now);
            return id;
        } catch (DuplicateKeyException duplicate) {
            String status=jdbc.queryForObject("SELECT status FROM diagram_render_task WHERE user_id=? AND project_id=? AND render_hash=?",String.class,userId,projectId,hash);
            if ("FAILED".equals(status)||"REFUNDED".equals(status)) {
                int changed=jdbc.update("UPDATE diagram_render_task SET id=?,status='RENDERING',error_message=NULL,updated_at=? WHERE user_id=? AND project_id=? AND render_hash=? AND status IN ('FAILED','REFUNDED')",id,now,userId,projectId,hash);
                if(changed==1)return id;
            }
            return null;
        }
    }

    public void rendered(String taskId){jdbc.update("UPDATE diagram_render_task SET status='RENDERED',updated_at=? WHERE id=?",LocalDateTime.now(),taskId);}
    public void failed(String taskId,String message){jdbc.update("UPDATE diagram_render_task SET status='FAILED',error_message=?,updated_at=? WHERE id=?",truncate(message),LocalDateTime.now(),taskId);}

    @Transactional
    public Finalized finalizeRendered(String taskId,String previewId,Long userId,Long projectId,String type,String hash,String rendererVersion,String normalizedDsl,String svg,List<ArtifactDraft> artifacts) {
        Map<String,Object> existing=success(userId,projectId,hash);
        if(existing!=null)return new Finalized(String.valueOf(existing.get("id")),false,0,points.currentPoints(userId));
        int locked=jdbc.update("UPDATE diagram_render_task SET status='CHARGING',updated_at=? WHERE id=? AND status='RENDERED'",LocalDateTime.now(),taskId);
        if(locked!=1)throw new IllegalStateException("预览任务状态已变化，请重新加载");
        Long transactionId=points.deductDiagramPreview(userId,taskId,"智能画图预览生成 · "+hash.substring(0,Math.min(12,hash.length())));
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("INSERT INTO diagram_preview(id,task_id,user_id,project_id,diagram_type,render_hash,renderer_version,status,charged_points,charge_transaction_id,normalized_dsl,svg_content,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'PUBLISHING',10,?,?,?,?,?)",previewId,taskId,userId,projectId,type,hash,rendererVersion,transactionId,normalizedDsl,svg,now,now);
        String chargeId=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO diagram_preview_charge(id,task_id,preview_id,user_id,project_id,render_hash,kind,points,transaction_id,status,created_at) VALUES(?,?,?,?,?,?,'CHARGE',10,?,'SUCCESS',?)",chargeId,taskId,previewId,userId,projectId,hash,transactionId,now);
        for(ArtifactDraft a:artifacts) jdbc.update("INSERT INTO diagram_artifact(id,preview_id,format,status,file_path,file_size,failure_reason,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString().replace("-",""),previewId,a.format(),a.status(),a.path(),a.size(),a.reason(),now,now);
        jdbc.update("UPDATE diagram_render_task SET status='PUBLISHING',charged_points=10,charge_transaction_id=?,updated_at=? WHERE id=?",transactionId,now,taskId);
        return new Finalized(previewId,true,10,points.currentPoints(userId));
    }

    @Transactional
    public void published(String taskId,String previewId) {
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("UPDATE diagram_preview SET status='SUCCESS',updated_at=? WHERE id=? AND status='PUBLISHING'",now,previewId);
        jdbc.update("UPDATE diagram_render_task SET status='SUCCESS',updated_at=? WHERE id=?",now,taskId);
    }

    @Transactional
    public void refundPublishFailure(String taskId,String previewId,Long userId,String reason) {
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM diagram_preview_charge WHERE task_id=? AND kind='REFUND'",Integer.class,taskId);
        if(exists!=null&&exists>0)return;
        Long refundTx=points.refundDiagramPreview(userId,taskId,"预览发布失败自动退回："+truncate(reason));
        String chargeId=jdbc.queryForObject("SELECT id FROM diagram_preview_charge WHERE task_id=? AND kind='CHARGE'",String.class,taskId);
        LocalDateTime now=LocalDateTime.now(); String refundId=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO diagram_preview_charge(id,task_id,preview_id,user_id,project_id,render_hash,kind,points,transaction_id,status,related_charge_id,created_at) SELECT ?,id,?,user_id,project_id,render_hash,'REFUND',10,?,'SUCCESS',?,? FROM diagram_render_task WHERE id=?",refundId,previewId,refundTx,chargeId,now,taskId);
        jdbc.update("UPDATE diagram_preview SET status='REFUNDED',refund_transaction_id=?,updated_at=? WHERE id=?",refundTx,now,previewId);
        jdbc.update("UPDATE diagram_render_task SET status='REFUNDED',refund_transaction_id=?,error_message=?,updated_at=? WHERE id=?",refundTx,truncate(reason),now,taskId);
    }

    public Map<String,Object> ownedPreview(String previewId,Long userId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM diagram_preview WHERE id=? AND user_id=?",previewId,userId);return rows.isEmpty()?null:rows.get(0);}
    public void refreshRenderer(String previewId,Long userId,String rendererVersion,String svg){
        int changed=jdbc.update("UPDATE diagram_preview SET renderer_version=?,svg_content=?,updated_at=? WHERE id=? AND user_id=? AND status='SUCCESS'",rendererVersion,svg,LocalDateTime.now(),previewId,userId);
        if(changed!=1)throw new IllegalStateException("预览不存在、无权访问或状态不可刷新");
    }
    public Map<String,Object> artifact(String previewId,String format){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM diagram_artifact WHERE preview_id=? AND format=?",previewId,format.toLowerCase());return rows.isEmpty()?null:rows.get(0);}
    public Map<String,String> artifactStates(String previewId){return jdbc.query("SELECT format,status FROM diagram_artifact WHERE preview_id=?",rs->{java.util.LinkedHashMap<String,String> m=new java.util.LinkedHashMap<>();while(rs.next())m.put(rs.getString(1),rs.getString(2));return m;},previewId);}
    @Transactional
    public void upsertReadyArtifact(String previewId,String format,String path,long size){
        LocalDateTime now=LocalDateTime.now();String kind=format.toLowerCase();
        int changed=jdbc.update("UPDATE diagram_artifact SET status='READY',file_path=?,file_size=?,failure_reason=NULL,updated_at=? WHERE preview_id=? AND format=?",path,size,now,previewId,kind);
        if(changed==0){
            try{jdbc.update("INSERT INTO diagram_artifact(id,preview_id,format,status,file_path,file_size,failure_reason,created_at,updated_at) VALUES(?,?,?,'READY',?,?,NULL,?,?)",UUID.randomUUID().toString().replace("-",""),previewId,kind,path,size,now,now);}
            catch(DuplicateKeyException race){jdbc.update("UPDATE diagram_artifact SET status='READY',file_path=?,file_size=?,failure_reason=NULL,updated_at=? WHERE preview_id=? AND format=?",path,size,now,previewId,kind);}
        }
    }
    private static String truncate(String s){String v=s==null?"":s;return v.substring(0,Math.min(480,v.length()));}
    public record ArtifactDraft(String format,String status,String path,long size,String reason){}
    public record Finalized(String previewId,boolean charged,int chargedPoints,int balance){}
}
