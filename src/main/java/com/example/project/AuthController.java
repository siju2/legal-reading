package com.example.project;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
@Controller
public class AuthController {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    public AuthController(MemberRepository members, PasswordEncoder encoder) { this.members = members; this.encoder = encoder; }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/signup") public String signup(Model model) { model.addAttribute("signupForm", new SignupForm()); return "signup"; }
    @PostMapping("/signup")
    public String register(@Valid @ModelAttribute SignupForm signupForm, BindingResult errors) {
        if (signupForm.getPassword() != null && signupForm.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            errors.rejectValue("password", "length", "비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.");
        if (signupForm.getPassword() != null && !signupForm.getPassword().equals(signupForm.getPasswordConfirm()))
            errors.rejectValue("passwordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
        if (errors.hasErrors()) return "signup";
        if ("legal_admin".equalsIgnoreCase(signupForm.getUsername())) {
            errors.rejectValue("username", "reserved", "관리자용 예약 아이디입니다."); return "signup";
        }
        if (members.existsByUsername(signupForm.getUsername())) {
            errors.rejectValue("username", "duplicate", "이미 사용 중인 아이디입니다."); return "signup";
        }
        try { members.saveAndFlush(new Member(signupForm.getUsername(), encoder.encode(signupForm.getPassword()))); }
        catch (DataIntegrityViolationException exception) {
            errors.rejectValue("username", "duplicate", "이미 사용 중인 아이디입니다."); return "signup";
        }
        return "redirect:/login?registered";
    }
}