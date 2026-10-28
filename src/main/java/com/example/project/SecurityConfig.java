package com.example.project;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(MemberRepository members) {
        return username -> members.findByUsername(username)
            .map(member -> User.withUsername(member.getUsername()).password(member.getPassword()).roles(member.isAdmin() ? new String[]{"USER","ADMIN"} : new String[]{"USER"}).build())
            .orElseThrow(() -> new UsernameNotFoundException("회원 정보를 찾을 수 없습니다."));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
            .requestMatchers("/login", "/signup", "/css/**", "/error").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/", "/cases", "/cases/**", "/compare", "/glossary", "/guide", "/api/cases", "/situation", "/law-search", "/law-search/**", "/api/law/**").permitAll()
            .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}