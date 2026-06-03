package com.dropai.rewrite.vo;

import java.util.ArrayList;
import java.util.List;

public class QualityCheckVO {

    private boolean meaningPreserved;
    private boolean academicTone;
    private boolean templateReduced;
    private boolean fluent;
    private List<String> issues = new ArrayList<>();

    public boolean isMeaningPreserved() {
        return meaningPreserved;
    }

    public void setMeaningPreserved(boolean meaningPreserved) {
        this.meaningPreserved = meaningPreserved;
    }

    public boolean isAcademicTone() {
        return academicTone;
    }

    public void setAcademicTone(boolean academicTone) {
        this.academicTone = academicTone;
    }

    public boolean isTemplateReduced() {
        return templateReduced;
    }

    public void setTemplateReduced(boolean templateReduced) {
        this.templateReduced = templateReduced;
    }

    public boolean isFluent() {
        return fluent;
    }

    public void setFluent(boolean fluent) {
        this.fluent = fluent;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }
}
