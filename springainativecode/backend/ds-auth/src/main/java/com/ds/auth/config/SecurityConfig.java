package com.ds.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Arrays;
import java.util.List;

/**
 * 认证服务本身放行 /auth/**（登录注册要匿名访问）；
 * 业务侧鉴权交给网关 + 各服务自己的 SecurityFilterChain。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a
                    .antMatchers("/auth/login", "/auth/register", "/auth/verify",
                            "/auth/me", "/actuator/**", "/v3/api-docs/**",
                            "/doc.html", "/webjars/**").permitAll()
                    .anyRequest().permitAll() // 业务侧服务各自收紧；auth 这里先放开
            )
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable());
        return http.build();
    }
}
