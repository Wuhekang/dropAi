package com.dropai.rewrite.service;

import com.dropai.rewrite.vo.PayRequiredResponse;

public class PointsNotEnoughException extends RuntimeException {
    private final int currentPoints;
    private final int requiredPoints;
    private final int missingPoints;

    public PointsNotEnoughException(String message) {
        super(message);
        this.currentPoints = 0;
        this.requiredPoints = 0;
        this.missingPoints = 0;
    }

    public PointsNotEnoughException(int currentPoints, int requiredPoints) {
        super("积分不足，需要充值");
        this.currentPoints = currentPoints;
        this.requiredPoints = requiredPoints;
        this.missingPoints = Math.max(0, requiredPoints - currentPoints);
    }

    public int getCurrentPoints() {
        return currentPoints;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public int getMissingPoints() {
        return missingPoints;
    }

    public PayRequiredResponse toResponse() {
        return new PayRequiredResponse(requiredPoints, currentPoints, missingPoints);
    }
}
