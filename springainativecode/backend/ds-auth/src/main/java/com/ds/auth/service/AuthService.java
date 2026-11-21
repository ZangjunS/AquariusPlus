package com.ds.auth.service;

import com.ds.api.dto.AuthRequest;
import com.ds.api.dto.TokenResponse;
import com.ds.api.entity.User;
import com.ds.auth.repository.UserMapper;
import com.ds.common.exception.BusinessException;
import com.ds.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public TokenResponse register(AuthRequest req) {
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw BusinessException.fail("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(encoder.encode(req.getPassword()));
        u.setNickname(req.getNickname() == null ? req.getUsername() : req.getNickname());
        u.setRole("USER");
        userMapper.insert(u);
        return login(u);
    }

    public TokenResponse login(User u) {
        String token = jwtUtil.issue(u.getId(), u.getUsername(), u.getRole());
        User safe = mask(u);
        return TokenResponse.of(token, safe);
    }

    public TokenResponse login(AuthRequest req) {
        User u = userMapper.findByUsername(req.getUsername());
        if (u == null || !encoder.matches(req.getPassword(), u.getPassword())) {
            throw BusinessException.fail("用户名或密码错误");
        }
        return login(u);
    }

    public User verify(String token) {
        var claims = jwtUtil.parse(token);
        Long userId = Long.valueOf(claims.getSubject());
        User u = userMapper.selectById(userId);
        if (u == null) throw BusinessException.notFound("用户不存在");
        return mask(u);
    }

    private User mask(User u) {
        u.setPassword(null);
        return u;
    }
}
