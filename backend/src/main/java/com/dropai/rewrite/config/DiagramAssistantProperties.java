package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;

@Component
@ConfigurationProperties(prefix="diagram-assistant")
public class DiagramAssistantProperties {
    private String provider="doubao"; private String model="doubao-seed-2-1-turbo-260628";
    private String apiKey=""; private String endpoint="https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private boolean stream=true; private double temperature=.1; private Duration connectTimeout=Duration.ofSeconds(5);
    private Duration firstByteTimeout=Duration.ofSeconds(20); private Duration readIdleTimeout=Duration.ofSeconds(20); private Duration hardLimit=Duration.ofSeconds(90);
    private int defaultTokens=1800; private double sqlInferenceConfidenceThreshold=.85;
    private Map<DiagramType,Integer> tokens=new EnumMap<>(Map.of(DiagramType.FLOWCHART,1800,DiagramType.FUNCTION_MODULE,1800,DiagramType.ER_DIAGRAM,2600,DiagramType.ARCHITECTURE,2600,DiagramType.USE_CASE,2000,DiagramType.BLOCK_DIAGRAM,2000,DiagramType.SEQUENCE_DIAGRAM,2600));
    public int tokensFor(DiagramType type){return tokens.getOrDefault(type,defaultTokens);}
    public String getProvider(){return provider;} public void setProvider(String v){provider=v;} public String getModel(){return model;} public void setModel(String v){model=v;}
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;} public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
    public boolean isStream(){return stream;} public void setStream(boolean v){stream=v;} public double getTemperature(){return temperature;} public void setTemperature(double v){temperature=v;}
    public Duration getConnectTimeout(){return connectTimeout;} public void setConnectTimeout(Duration v){connectTimeout=v;} public Duration getFirstByteTimeout(){return firstByteTimeout;} public void setFirstByteTimeout(Duration v){firstByteTimeout=v;}
    public Duration getReadIdleTimeout(){return readIdleTimeout;} public void setReadIdleTimeout(Duration v){readIdleTimeout=v;} public Duration getHardLimit(){return hardLimit;} public void setHardLimit(Duration v){hardLimit=v;}
    public int getDefaultTokens(){return defaultTokens;} public void setDefaultTokens(int v){defaultTokens=v;} public Map<DiagramType,Integer> getTokens(){return tokens;} public void setTokens(Map<DiagramType,Integer> v){tokens=v;}
    public double getSqlInferenceConfidenceThreshold(){return sqlInferenceConfidenceThreshold;} public void setSqlInferenceConfidenceThreshold(double v){sqlInferenceConfidenceThreshold=v;}
}
