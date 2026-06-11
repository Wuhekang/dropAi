package com.dropai.rewrite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PhoneAuthDTO {
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的中国大陆手机号")
    private String phone;
    @NotBlank
    @Size(min = 6, max = 72, message = "密码长度为 6-72 位")
    private String password;
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
