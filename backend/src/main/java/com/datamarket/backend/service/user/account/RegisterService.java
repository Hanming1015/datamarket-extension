package com.datamarket.backend.service.user.account;

import com.datamarket.backend.pojo.User;

/**
 * Service interface for managing Register operations.
 */

public interface RegisterService {
    User register(String username, String password, String name, String email, String organization, String role);
}
