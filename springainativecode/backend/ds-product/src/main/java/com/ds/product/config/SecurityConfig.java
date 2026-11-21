package com.ds.product.config;

import com.ds.common.constant.CommonConstants;
import com.ds.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a
                    .antMatchers("/actuator/**", "/v3/api-docs/**", "/doc.html", "/webjars/**", "/swagger-ui/**").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static class JwtAuthFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String token = JwtUtil.fromHeader(req.getHeader(CommonConstants.HEADER_TOKEN));
            if (token != null) {
                try {
                    Claims c = new JwtUtil().parse(token);
                    req.setAttribute("userId", Long.valueOf(c.getSubject()));
                    req.setAttribute("username", c.get("username", String.class));
                    req.setAttribute("role", c.get("role", String.class));
                    // 只塞 request attribute 不算“已认证”，必须建立 SecurityContext，否则 anyRequest().authenticated() 一律 403
                    String role = c.get("role", String.class);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    c.getSubject(), null,
                                    Collections.singletonList(
                                            new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role)))));
                } catch (JwtException | NumberFormatException e) {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":401,\"message\":\"未认证或令牌无效\"}");
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }
}
