package com.dropai.rewrite.vo;

public class DocumentPrecheckVO {
    private int charCount;
    private int costPoints;
    private int currentPoints;
    private boolean canProcess;

    public DocumentPrecheckVO() {
    }

    public DocumentPrecheckVO(int charCount, int costPoints, int currentPoints, boolean canProcess) {
        this.charCount = charCount;
        this.costPoints = costPoints;
        this.currentPoints = currentPoints;
        this.canProcess = canProcess;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public int getCostPoints() {
        return costPoints;
    }

    public void setCostPoints(int costPoints) {
        this.costPoints = costPoints;
    }

    public int getCurrentPoints() {
        return currentPoints;
    }

    public void setCurrentPoints(int currentPoints) {
        this.currentPoints = currentPoints;
    }

    public boolean isCanProcess() {
        return canProcess;
    }

    public void setCanProcess(boolean canProcess) {
        this.canProcess = canProcess;
    }
}
