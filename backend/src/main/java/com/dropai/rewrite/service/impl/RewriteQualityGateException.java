package com.dropai.rewrite.service.impl;

/**
 * Signals that model output was usable text but could not safely pass the document quality gate.
 * Document processing may recover by retaining the complete original paragraph.
 */
final class RewriteQualityGateException extends IllegalStateException {

    RewriteQualityGateException(String message) {
        super(message);
    }

    RewriteQualityGateException(String message, Throwable cause) {
        super(message, cause);
    }
}
