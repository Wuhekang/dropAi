package com.dropai.rewrite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PhoneAuthDTO {
    @NotBlank
    @Pattern(regexp = "^\\d{11}$", message = "请输入11位账号")
    private String phone;
    @NotBlank
    @Size(min = 6, max = 72, message = "密码长度为 6-72 位")
    private String password;
    @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "学校编号格式不正确")
    private String college;
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
}
