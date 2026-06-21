package com.dropai.rewrite.vo;

import java.util.List;

public class DocumentLibraryPageVO {
    private List<DocumentLibraryItemVO> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public DocumentLibraryPageVO(List<DocumentLibraryItemVO> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public List<DocumentLibraryItemVO> getList() { return list; }
    public void setList(List<DocumentLibraryItemVO> list) { this.list = list; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
