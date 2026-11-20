package com.example.project;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(MemberRepository members) {
        return username -> members.findByUsername(username)
            .map(member -> User.withUsername(member.getUsername()).password(member.getPassword()).roles(member.isAdmin() ? new String[]{"USER","ADMIN"} : new String[]{"USER"}).build())
            .orElseThrow(() -> new UsernameNotFoundException("회원 정보를 찾을 수 없습니다."));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, com.example.project.security.PersistentLoginRepository tokens, UserDetailsService users) throws Exception {
        http
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; script-src-attr 'none'; style-src 'self'; style-src-attr 'none'; img-src 'self'; font-src 'self'; connect-src 'self'; frame-src 'self'"))
                .crossOriginOpenerPolicy(opener -> opener.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy("camera=(), geolocation=(), microphone=(), payment=(), usb=(), accelerometer=(), gyroscope=(), magnetometer=()")))
            .authorizeHttpRequests(auth -> auth
            .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
            .requestMatchers("/login", "/signup", "/css/**", "/js/**", "/error").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/", "/cases", "/cases/**", "/compare", "/glossary", "/guide", "/api/cases", "/situation", "/law-search", "/law-search/**", "/api/law/**").permitAll()
            .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?error").permitAll())
            .rememberMe(remember -> remember.tokenRepository(tokens).userDetailsService(users)
                .key(com.example.project.security.DataCipher.rememberMeKey()).tokenValiditySeconds(14*24*60*60)
                .rememberMeParameter("remember-me").rememberMeCookieName("legal-remember-me").useSecureCookie(true))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}
