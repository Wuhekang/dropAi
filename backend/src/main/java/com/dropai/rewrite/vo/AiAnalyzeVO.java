package com.dropai.rewrite.vo;

import java.util.ArrayList;
import java.util.List;

public class AiAnalyzeVO {

    private int score;
    private String level;
    private List<String> suggestions = new ArrayList<>();

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}
