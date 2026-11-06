package com.dropai.rewrite.external;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class XuejieExternalJobStateRepository {
    public static final String CREATED = "CREATED";
    public static final String CONFIGURING = "CONFIGURING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUBMITTING = "SUBMITTING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String POLLING = "POLLING";
    public static final String DOWNLOADING = "DOWNLOADING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String UNKNOWN = "UNKNOWN";

    private final JdbcTemplate jdbc;

    public XuejieExternalJobStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(State state) {
        jdbc.update("""
                INSERT INTO xuejie_external_job_state
                (job_id,user_id,original_name,platform,mode,feature_code,feature_name,cost_points,
                 stage,remote_task_id,remote_status,refund_state,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'NONE',?,?)
                """, state.jobId(), state.userId(), state.originalName(), state.platform(), state.mode(),
                state.featureCode(), state.featureName(), state.costPoints(), state.stage(),
                emptyToNull(state.remoteTaskId()), emptyToNull(state.remoteStatus()),
                state.createdAt(), state.updatedAt());
    }

    public State find(String jobId) {
        try {
            return jdbc.queryForObject("SELECT * FROM xuejie_external_job_state WHERE job_id=?", this::map, jobId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    public List<State> findRecoverable() {
        return jdbc.query("""
                SELECT * FROM xuejie_external_job_state
                WHERE platform='DAYA'
                  AND (stage IN ('CREATED','CONFIGURING','PROCESSING','SUBMITTING','ACCEPTED','POLLING','DOWNLOADING')
                       OR (stage='UNKNOWN' AND remote_task_id IS NOT NULL))
                ORDER BY created_at
                """, this::map);
    }

    public void stage(String jobId, String stage, String remoteTaskId, String remoteStatus) {
        jdbc.update("""
                UPDATE xuejie_external_job_state
                SET stage=?,remote_task_id=COALESCE(?,remote_task_id),remote_status=?,updated_at=?
                WHERE job_id=?
                """, stage, emptyToNull(remoteTaskId), emptyToNull(remoteStatus), LocalDateTime.now(), jobId);
    }

    /** Atomic, database-visible refund ownership claim. The row lock is held until transaction commit. */
    public boolean claimRefund(String jobId) {
        return jdbc.update("""
                UPDATE xuejie_external_job_state
                SET refund_state='CLAIMED',updated_at=?
                WHERE job_id=? AND refund_state='NONE'
                """, LocalDateTime.now(), jobId) == 1;
    }

    /**
     * Completes the refund claim and makes the job non-recoverable in one statement.
     *
     * The caller credits points in the same transaction. Keeping REFUNDED and FAILED in this
     * single write prevents a committed refund from leaving CONFIGURING/CREATED recoverable and
     * therefore prevents a restart from issuing a new paid submission.
     */
    public void refunded(String jobId) {
        int updated = jdbc.update("""
                UPDATE xuejie_external_job_state
                SET refund_state='REFUNDED',stage='FAILED',updated_at=?
                WHERE job_id=? AND refund_state='CLAIMED'
                """, LocalDateTime.now(), jobId);
        if (updated != 1) {
            throw new IllegalStateException("Unable to finalize external-job refund claim: " + jobId);
        }
    }

    private State map(ResultSet rs, int rowNum) throws SQLException {
        return new State(
                rs.getString("job_id"),
                rs.getLong("user_id"),
                rs.getString("original_name"),
                rs.getString("platform"),
                rs.getString("mode"),
                rs.getString("feature_code"),
                rs.getString("feature_name"),
                rs.getInt("cost_points"),
                rs.getString("stage"),
                rs.getString("remote_task_id"),
                rs.getString("remote_status"),
                rs.getString("refund_state"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record State(String jobId, Long userId, String originalName, String platform, String mode,
                        String featureCode, String featureName, int costPoints, String stage,
                        String remoteTaskId, String remoteStatus, String refundState,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static State created(String jobId, Long userId, String originalName,
                                    XuejiePlatform platform, XuejieRewriteMode mode,
                                    String featureCode, String featureName, int costPoints) {
            LocalDateTime now = LocalDateTime.now();
            return new State(jobId, userId, originalName, platform.name(), mode.apiValue(),
                    featureCode, featureName, costPoints, CREATED, "", "", "NONE", now, now);
        }
    }
}
