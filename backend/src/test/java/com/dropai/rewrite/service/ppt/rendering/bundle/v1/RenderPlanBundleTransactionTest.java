package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.production.v1.ProductionRenderPlanCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RenderPlanBundleTransactionTest {
    @TempDir
    Path temp;

    @Test
    void realTransactionPublishesOnlyAfterCommit() {
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        StagedRenderPlanBundle staged=staged();
        TransactionTemplate transaction=transaction();

        transaction.executeWithoutResult(status->{
            RenderPlanBundleTransaction.register(coordinator,staged);
            verifyNoInteractions(coordinator);
        });

        verify(coordinator).publish(staged);
        verify(coordinator,never()).discard(staged);
    }

    @Test
    void realTransactionRollbackDiscardsWithoutPublishing() {
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        StagedRenderPlanBundle staged=staged();

        transaction().executeWithoutResult(status->{
            RenderPlanBundleTransaction.register(coordinator,staged);
            status.setRollbackOnly();
        });

        verify(coordinator,never()).publish(staged);
        verify(coordinator).discard(staged);
    }

    @Test
    void afterCommitPublishFailureDoesNotDiscardAndExposeAnOldCurrent() {
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        StagedRenderPlanBundle staged=staged();
        doThrow(new IllegalStateException("publish failed")).when(coordinator).publish(staged);

        assertThrows(IllegalStateException.class,()->transaction().executeWithoutResult(status->
                RenderPlanBundleTransaction.register(coordinator,staged)));

        verify(coordinator).publish(staged);
        verify(coordinator,never()).discard(staged);
    }

    @Test
    void registrationWithoutATransactionFailsAndDiscardsTheStage() {
        ProductionRenderPlanCoordinator coordinator=mock(ProductionRenderPlanCoordinator.class);
        StagedRenderPlanBundle staged=staged();

        assertThrows(IllegalStateException.class,()->
                RenderPlanBundleTransaction.register(coordinator,staged));

        verify(coordinator).discard(staged);
        verify(coordinator,never()).publish(staged);
    }

    private TransactionTemplate transaction() {
        DriverManagerDataSource dataSource=new DriverManagerDataSource(
                "jdbc:h2:mem:bundle_tx_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private StagedRenderPlanBundle staged() {
        return new StagedRenderPlanBundle(temp,"11111111-1111-1111-1111-111111111111",
                "sha256:"+"1".repeat(64),"sha256:"+"2".repeat(64),
                "sha256:"+"3".repeat(64),"sha256:"+"4".repeat(64),0);
    }
}
