package com.dropai.rewrite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDTO {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "账号只能包含字母、数字和下划线，长度为 3-32 位")
    private String username;
    @NotBlank
    @Size(min = 6, max = 72, message = "密码长度为 6-72 位")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
