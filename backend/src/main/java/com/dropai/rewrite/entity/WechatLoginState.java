package com.dropai.rewrite.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("wechat_login_state")
public class WechatLoginState {
    @TableId
    private String state;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
