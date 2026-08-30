package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.production.v1.ProductionRenderPlanCoordinator;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/** Publishes a staged bundle only after its owning database transaction commits. */
public final class RenderPlanBundleTransaction {
    private RenderPlanBundleTransaction() {
    }

    public static void requireActive() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "RenderPlan bundle publication requires an active transaction");
        }
    }

    public static void register(
            ProductionRenderPlanCoordinator coordinator,
            StagedRenderPlanBundle staged
    ) {
        Objects.requireNonNull(coordinator, "coordinator");
        Objects.requireNonNull(staged, "staged");
        try {
            requireActive();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public int getOrder() {
                            return Ordered.LOWEST_PRECEDENCE;
                        }

                        @Override
                        public void afterCommit() {
                            coordinator.publish(staged);
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) {
                                coordinator.discard(staged);
                            }
                            // COMMITTED or UNKNOWN without a successful publish intentionally leaves
                            // the pending fence in place, so an older current bundle cannot execute.
                        }
                    });
        } catch (RuntimeException exception) {
            coordinator.discard(staged);
            throw exception;
        }
    }
}
