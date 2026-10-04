package com.dropai.rewrite.mechanicalpaper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class MechanicalPaperState {
    public String id, title, status = "RUNNING", stage = "设计规划", error = "";
    public long startedAt = System.currentTimeMillis(), updatedAt = startedAt;
    public int wordCount;
    public JsonNode design;
    public Map<String, Task> tasks = new LinkedHashMap<>();
    public static class Task {
        public String label, status = "waiting", error = "", file = "", pageUrl = "", imageUrl = "";
        public long startedAt, finishedAt;
        public JsonNode content;
        public Task() {}
        public Task(String label) { this.label = label; }
    }
}
