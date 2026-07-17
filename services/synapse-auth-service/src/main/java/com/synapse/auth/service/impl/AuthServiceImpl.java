package com.synapse.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.auth.dto.LoginRequest;
import com.synapse.auth.dto.RegisterRequest;
import com.synapse.auth.vo.LoginResponse;
import com.synapse.auth.vo.UserVO;
import com.synapse.auth.entity.User;
import com.synapse.auth.mapper.UserMapper;
import com.synapse.auth.service.AuthService;
import com.synapse.common.api.ResultCode;
import com.synapse.common.exception.BusinessException;
import com.synapse.common.security.JwtUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Default {@link AuthService} implementation. Throws {@link BusinessException} on rule
 * violations; the shared {@code GlobalExceptionHandler} turns those into the unified {@code Result}.
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", req.getUsername()));
        // 用户不存在与密码错误返回同一提示,避免暴露"用户名是否存在"
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "invalid username or password");
        }
        String token = jwtUtil.createToken(user.getId());
        return new LoginResponse(token, toVO(user));
    }

    @Override
    public UserVO register(RegisterRequest req) {
        // 预检查给出友好提示(快速路径);唯一键 uk_users_username 才是并发下的最终防线
        Long exists = userMapper.selectCount(
                new QueryWrapper<User>().eq("username", req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException("username already taken");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setOrganization(req.getOrganization());
        // 自助注册一律为 consumer,不采信客户端传入的 role(防越权)
        user.setRole("consumer");
        user.setCreatedAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 预检查与 insert 之间的并发窗口:两个同名请求都过了 selectCount,
            // 由数据库唯一键拦下,这里转成与预检查一致的友好提示,而非 500。
            throw new BusinessException("username already taken");
        }
        return toVO(user);
    }

    @Override
    public UserVO info(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);   // password 不在 UserVO 里,天然不外泄
        return vo;
    }
}
