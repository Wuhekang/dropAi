package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class XuejieExternalJobStateRepositoryTest {

    @Test
    void databaseClaimAllowsOnlyOneRefundOwner() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:xuejie-state;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new XuejieExternalSchemaInitializer(jdbc).run(null);
        XuejieExternalJobStateRepository repository = new XuejieExternalJobStateRepository(jdbc);
        repository.insert(XuejieExternalJobStateRepository.State.created(
                "job-1", 7L, "paper.docx", XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE,
                "DOCUMENT_HUMANIZE", "文档降AI", 10));
        repository.stage("job-1", XuejieExternalJobStateRepository.CONFIGURING, null, null);

        assertThat(repository.claimRefund("job-1")).isTrue();
        assertThat(repository.claimRefund("job-1")).isFalse();
        repository.refunded("job-1");
        assertThat(repository.find("job-1").refundState()).isEqualTo("REFUNDED");
        assertThat(repository.find("job-1").stage()).isEqualTo(XuejieExternalJobStateRepository.FAILED);
        assertThat(repository.findRecoverable()).isEmpty();
    }

    @Test
    void onlyUnknownJobsWithRemoteTaskIdAreRecoverable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:xuejie-recoverability;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new XuejieExternalSchemaInitializer(jdbc).run(null);
        XuejieExternalJobStateRepository repository = new XuejieExternalJobStateRepository(jdbc);
        repository.insert(XuejieExternalJobStateRepository.State.created(
                "unknown-submit", 7L, "paper.docx", XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE,
                "DOCUMENT_HUMANIZE", "文档降AI", 10));
        repository.insert(XuejieExternalJobStateRepository.State.created(
                "unknown-poll", 7L, "paper.docx", XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE,
                "DOCUMENT_HUMANIZE", "文档降AI", 10));
        LocalDateTime now = LocalDateTime.now();
        repository.insert(new XuejieExternalJobStateRepository.State(
                "legacy-platform", 7L, "legacy.docx", "CNKI", XuejieRewriteMode.HUMANIZE.apiValue(),
                "DOCUMENT_HUMANIZE", "文档降AI", 10,
                XuejieExternalJobStateRepository.PROCESSING, "", "legacy", "NONE", now, now));
        repository.stage("unknown-submit", XuejieExternalJobStateRepository.UNKNOWN,
                null, "submission_unknown");
        repository.stage("unknown-poll", XuejieExternalJobStateRepository.UNKNOWN,
                "remote-7", "poll_timeout");

        assertThat(repository.findRecoverable())
                .extracting(XuejieExternalJobStateRepository.State::jobId)
                .containsExactly("unknown-poll");
    }
}
