package com.dropai.rewrite.mechanicalpaper;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class MechanicalPaperFieldUpdater {
    private final Environment environment;
    public MechanicalPaperFieldUpdater(Environment environment) {this.environment=environment;}
    public synchronized void update(Path file) throws Exception {
        boolean windows=System.getProperty("os.name", "").toLowerCase().contains("windows");
        boolean enabled=environment.getProperty("mechanical.paper.word-field-refresh",Boolean.class,windows);
        if(!enabled) return; // Non-Office deployments retain a real TOC with update-on-open enabled.
        if(!windows) throw new IllegalStateException("Doki 目录更新需要 Windows Word 服务，当前环境请关闭自动更新设置");
        String path=file.toAbsolutePath().toString().replace("'", "''");
        String script="""
            $ErrorActionPreference='Stop'
            $word=$null; $doc=$null
            try {
              $word=New-Object -ComObject Word.Application
              $word.Visible=$false; $word.DisplayAlerts=0
              $doc=$word.Documents.Open('%s')
              foreach($math in $doc.OMaths){$math.BuildUp()}
              [void]$doc.Fields.Update()
              foreach($toc in $doc.TablesOfContents){$toc.Update()}
              [void]$doc.Repaginate()
              foreach($toc in $doc.TablesOfContents){$toc.UpdatePageNumbers()}
              $doc.Save()
            } finally {
              if($doc){$doc.Close(0)}
              if($word){$word.Quit()}
            }
            """.formatted(path);
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded)
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if(!process.waitFor(120,TimeUnit.SECONDS)) {process.destroyForcibly();throw new IllegalStateException("Doki 目录更新超时，请检查 Word 服务");}
        if(process.exitValue()!=0) throw new IllegalStateException("Doki 目录更新失败，请检查 Word 服务");
    }
}
