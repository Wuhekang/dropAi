package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.service.writing.ChartRenderService;
import com.dropai.rewrite.service.writing.CitationManagerService;
import com.dropai.rewrite.service.writing.DocxExportService;
import com.dropai.rewrite.service.writing.DoubaoWritingService;
import com.dropai.rewrite.service.writing.ReferenceSearchService;
import com.dropai.rewrite.service.writing.WritingGenerationService;
import com.dropai.rewrite.service.writing.WritingQualityGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WritingGenerationStartTest {
    @Test
    void launchesOnlyAfterCommitAndDoesNotCreateDuplicateRunningTask() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:generation-start;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE writing_project (id VARCHAR(64) PRIMARY KEY, user_id BIGINT, title VARCHAR(255), status VARCHAR(30), current_stage VARCHAR(120), progress INT, error_message CLOB, skip_references BOOLEAN, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_chapter (id VARCHAR(64), project_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE writing_chart (id VARCHAR(64), project_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE writing_generation_task (id VARCHAR(64), project_id VARCHAR(64), user_id BIGINT, task_type VARCHAR(60), status VARCHAR(30), stage VARCHAR(120), progress INT, error_message CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_export_file (id VARCHAR(64), project_id VARCHAR(64), created_at TIMESTAMP)");
        jdbc.update("INSERT INTO writing_project (id,user_id,title,status,progress) VALUES ('p1',1,'测试题目','OUTLINE_READY',18)");
        TaskExecutor executor = mock(TaskExecutor.class);
        WritingGenerationService service = new WritingGenerationService(jdbc,
                mock(ReferenceSearchService.class), mock(ChartRenderService.class),
                mock(CitationManagerService.class), mock(DocxExportService.class),
                mock(WritingQualityGate.class), mock(DocumentJobMapper.class),
                mock(PointService.class), executor, new ObjectMapper(), mock(DoubaoWritingService.class));
        AuthContext.setUserId(1L);
        try {
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            transaction.executeWithoutResult(ignored -> {
                Map<String, Object> result = service.start("p1");
                assertEquals("RUNNING", result.get("STATUS"));
                verify(executor, never()).execute(any(Runnable.class));
            });
            verify(executor, times(1)).execute(any(Runnable.class));
            service.start("p1");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM writing_generation_task WHERE project_id='p1'", Integer.class));
            verify(executor, times(1)).execute(any(Runnable.class));
            service.recoverInterruptedTasks();
            assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM writing_project WHERE id='p1'", String.class));
            assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM writing_generation_task WHERE project_id='p1'", String.class));
        } finally {
            AuthContext.clear();
        }
    }
}
