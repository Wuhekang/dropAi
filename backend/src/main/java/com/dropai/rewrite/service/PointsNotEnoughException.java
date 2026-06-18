package com.dropai.rewrite.service;

public class PointsNotEnoughException extends RuntimeException {
    public PointsNotEnoughException(String message) {
        super(message);
    }
}
