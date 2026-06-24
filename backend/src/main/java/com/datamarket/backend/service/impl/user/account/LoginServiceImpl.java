package com.datamarket.backend.service.impl.user.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.mapper.UserMapper;
import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.user.account.LoginService;
import com.datamarket.backend.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the LoginService interface.
 */

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Username and password must not be empty");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = JwtUtil.createJWT(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "name", user.getName(),
                "email", user.getEmail(),
                "organization", user.getOrganization(),
                "role", user.getRole(),
                "createdAt", user.getCreatedAt()
        ));
        return result;
    }
}
