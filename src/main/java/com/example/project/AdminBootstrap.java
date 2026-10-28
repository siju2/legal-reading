package com.example.project;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
@Component
public class AdminBootstrap implements ApplicationRunner {
 private final MemberRepository members;private final PasswordEncoder encoder;private final String password;
 public AdminBootstrap(MemberRepository m,PasswordEncoder e,@Value("${legal.bootstrap-admin-password:}")String p){members=m;encoder=e;password=p;}
 @Override public void run(ApplicationArguments args){if(password.isBlank())return;if(password.length()<16)throw new IllegalArgumentException("관리자 초기 비밀번호는 16자 이상이어야 합니다.");if(!members.existsByUsername("legal_admin")){var member=new Member("legal_admin",encoder.encode(password));member.makeAdmin();members.saveAndFlush(member);}}
}
