package com.dropai.rewrite.service.diagram;

public class DiagramGenerationException extends RuntimeException {
    private final String code; private final boolean retryable;
    public DiagramGenerationException(String code,String message){this(code,message,false,null);}
    public DiagramGenerationException(String code,String message,boolean retryable,Throwable cause){super(message,cause);this.code=code;this.retryable=retryable;}
    public String code(){return code;} public boolean retryable(){return retryable;}
}
