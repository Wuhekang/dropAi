package com.dropai.rewrite.service.diagram;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Component
public class SqlSchemaExtractor {
    public SqlSchema extract(String sql){if(sql==null||sql.isBlank())throw new DiagramGenerationException("SQL_PARSE_FAILED","SQL文件为空，原图已恢复。");String text=stripComments(sql);Map<String,Table> tables=new LinkedHashMap<>();
        Matcher create=Pattern.compile("(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"]?([A-Za-z0-9_]+)[`\"]?\\s*\\((.*?)\\)\\s*(?=ENGINE\\b|COMMENT\\b|;|$)").matcher(text);while(create.find()){String name=create.group(1);List<String> parts=splitTopLevel(create.group(2));LinkedHashMap<String,Column> columns=new LinkedHashMap<>();List<List<String>> unique=new ArrayList<>(),primary=new ArrayList<>();List<ForeignKey> fks=new ArrayList<>();for(String raw:parts){String p=raw.trim();Matcher col=Pattern.compile("^[`\"]?([A-Za-z0-9_]+)[`\"]?\\s+([A-Za-z]+(?:\\s+(?:PRECISION|VARYING|WITH(?:OUT)?\\s+TIME\\s+ZONE))?(?:\\([^)]*\\))?)(.*)$",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(p);if(col.find()&&!p.matches("(?is)^(PRIMARY|FOREIGN|UNIQUE|CONSTRAINT|KEY|INDEX)\\b.*")){String tail=col.group(3);columns.put(col.group(1),new Column(col.group(1),col.group(2).trim().toLowerCase(),p.matches("(?is).*\\bNOT\\s+NULL\\b.*"),p.matches("(?is).*\\bPRIMARY\\s+KEY\\b.*"),p.matches("(?is).*\\bUNIQUE\\b.*"),comment(tail)));continue;}parseConstraint(name,p,primary,unique,fks);}tables.put(name,new Table(name,columns,primary,unique,fks,tableComment(create.group(0))));}
        Matcher alter=Pattern.compile("(?is)ALTER\\s+TABLE\\s+[`\"]?([A-Za-z0-9_]+)[`\"]?\\s+ADD\\s+(.*?);").matcher(text);while(alter.find()){Table t=tables.get(alter.group(1));if(t!=null)parseConstraint(t.name(),alter.group(2),t.primaryKeys(),t.uniqueKeys(),t.foreignKeys());}
        if(tables.isEmpty())throw new DiagramGenerationException("SQL_PARSE_FAILED","未识别到CREATE TABLE，原图已恢复。");return new SqlSchema(new ArrayList<>(tables.values()));}
    private void parseConstraint(String table,String p,List<List<String>> primary,List<List<String>> unique,List<ForeignKey> fks){Matcher pk=Pattern.compile("(?is)PRIMARY\\s+KEY\\s*\\(([^)]+)\\)").matcher(p);if(pk.find())primary.add(names(pk.group(1)));Matcher uq=Pattern.compile("(?is)UNIQUE(?:\\s+(?:KEY|INDEX))?(?:\\s+[`\"]?[A-Za-z0-9_]+[`\"]?)?\\s*\\(([^)]+)\\)").matcher(p);if(uq.find())unique.add(names(uq.group(1)));Matcher fk=Pattern.compile("(?is)FOREIGN\\s+KEY\\s*\\(([^)]+)\\)\\s+REFERENCES\\s+[`\"]?([A-Za-z0-9_]+)[`\"]?\\s*\\(([^)]+)\\)").matcher(p);if(fk.find())fks.add(new ForeignKey(table,names(fk.group(1)),fk.group(2),names(fk.group(3))));}
    private List<String> splitTopLevel(String body){List<String> out=new ArrayList<>();int depth=0,start=0;boolean quote=false;char qc=0;for(int i=0;i<body.length();i++){char c=body.charAt(i);if((c=='\''||c=='\"')&&(i==0||body.charAt(i-1)!='\\')){if(!quote){quote=true;qc=c;}else if(qc==c)quote=false;}if(quote)continue;if(c=='(')depth++;else if(c==')')depth--;else if(c==','&&depth==0){out.add(body.substring(start,i));start=i+1;}}out.add(body.substring(start));return out;}
    private static List<String> names(String s){return Arrays.stream(s.split(",")).map(x->x.replaceAll("[`\"\\s]","")).filter(x->!x.isEmpty()).toList();}
    private static String stripComments(String s){return s.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)--.*$","");}private static String comment(String s){Matcher m=Pattern.compile("(?is)COMMENT\\s+['\"]([^'\"]*)['\"]").matcher(s);return m.find()?m.group(1):"";}private static String tableComment(String s){return comment(s);}
    public record Column(String name,String type,boolean notNull,boolean inlinePrimaryKey,boolean inlineUnique,String comment){}
    public record ForeignKey(String sourceTable,List<String> sourceColumns,String targetTable,List<String> targetColumns){}
    public record Table(String name,LinkedHashMap<String,Column> columns,List<List<String>> primaryKeys,List<List<String>> uniqueKeys,List<ForeignKey> foreignKeys,String comment){}
    public record SqlSchema(List<Table> tables){}
}
