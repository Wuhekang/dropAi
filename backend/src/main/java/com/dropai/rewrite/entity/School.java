package com.dropai.rewrite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("school")
public class School {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolCode;
    private String schoolName;
    private BigDecimal rechargePricePer10;
    private BigDecimal studentRechargePricePer10;
    private BigDecimal studentRechargeMinPricePer10;
    private Boolean enabled;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private String deleteReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getSchoolCode(){return schoolCode;} public void setSchoolCode(String v){schoolCode=v;}
    public String getSchoolName(){return schoolName;} public void setSchoolName(String v){schoolName=v;}
    public BigDecimal getRechargePricePer10(){return rechargePricePer10;} public void setRechargePricePer10(BigDecimal v){rechargePricePer10=v;}
    public BigDecimal getStudentRechargePricePer10(){return studentRechargePricePer10;} public void setStudentRechargePricePer10(BigDecimal v){studentRechargePricePer10=v;}
    public BigDecimal getStudentRechargeMinPricePer10(){return studentRechargeMinPricePer10;} public void setStudentRechargeMinPricePer10(BigDecimal v){studentRechargeMinPricePer10=v;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
    public LocalDateTime getDeletedAt(){return deletedAt;} public void setDeletedAt(LocalDateTime v){deletedAt=v;}
    public Long getDeletedBy(){return deletedBy;} public void setDeletedBy(Long v){deletedBy=v;}
    public String getDeleteReason(){return deleteReason;} public void setDeleteReason(String v){deleteReason=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
