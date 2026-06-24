package com.datamarket.backend.service.impl.user.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.mapper.UserMapper;
import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.user.account.RegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Implementation of the RegisterService interface.
 */

@Service
public class RegisterServiceImpl implements RegisterService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User register(String username, String password, String name, String email, String organization, String role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Username and password must not be empty");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).or().eq("email", email);
        User existing = userMapper.selectOne(queryWrapper);
        if (existing != null) {
            if (username.equals(existing.getUsername())) {
                throw new IllegalArgumentException("Username already exists");
            }
            if (email != null && email.equals(existing.getEmail())) {
                throw new IllegalArgumentException("Email already exists");
            }
            throw new IllegalArgumentException("User already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(StringUtils.hasText(name) ? name : username);
        user.setEmail(email);
        user.setOrganization(organization);
        user.setRole(StringUtils.hasText(role) ? role : "consumer");
        user.setCreatedAt(LocalDateTime.now());

        userMapper.insert(user);
        user.setPassword(null);
        return user;
    }
}
