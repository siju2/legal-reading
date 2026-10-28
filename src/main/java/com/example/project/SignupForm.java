package com.example.project;
import jakarta.validation.constraints.*;
public class SignupForm {
    @NotBlank(message = "아이디를 입력해 주세요.")
    @Pattern(regexp = "[a-zA-Z0-9_]{4,30}", message = "아이디는 영문, 숫자, 밑줄 4~30자로 입력해 주세요.")
    private String username;
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
    private String password;
    @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
    private String passwordConfirm;
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String value) { passwordConfirm = value; }
}