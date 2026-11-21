package com.ds.user.service;

import com.ds.api.entity.User;
import com.ds.common.exception.BusinessException;
import com.ds.user.repository.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public Page<User> page(long current, long size) {
        return userMapper.selectPage(Page.of(current, size), new LambdaQueryWrapper<User>().orderByDesc(User::getId));
    }

    public User getById(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw BusinessException.notFound("用户不存在: " + id);
        u.setPassword(null);
        return u;
    }

    public List<User> listAll() {
        return userMapper.selectList(null).stream()
                .peek(u -> u.setPassword(null))
                .collect(Collectors.toList());
    }
}
