package com.datamarket.backend.service.user.account;

import java.util.Map;

/**
 * Service interface for managing Login operations.
 */

public interface LoginService {
    Map<String, Object> login(String username, String password);
}
