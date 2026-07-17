package com.synapse.auth.service;

import com.synapse.auth.dto.LoginRequest;
import com.synapse.auth.dto.RegisterRequest;
import com.synapse.auth.vo.LoginResponse;
import com.synapse.auth.vo.UserVO;

/**
 * Authentication use-cases: login / register / user lookup.
 * Implemented by {@link com.synapse.auth.service.impl.AuthServiceImpl}.
 */
public interface AuthService {

    /** Verify credentials and issue a JWT. */
    LoginResponse login(LoginRequest req);

    /** Create a new (consumer) user. */
    UserVO register(RegisterRequest req);

    /** Look up the current user by the id the gateway injected. */
    UserVO info(String userId);
}
