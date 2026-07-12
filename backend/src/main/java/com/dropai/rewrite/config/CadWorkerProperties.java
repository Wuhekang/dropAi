package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cad.worker")
public class CadWorkerProperties {
    private boolean enabled = true;
    private String script = "";
    private String python = "python";
    private String workDir = "";
    private int timeoutSeconds = 240;
    private String engine = "cadquery";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }
    public String getPython() { return python; }
    public void setPython(String python) { this.python = python; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
}
