package com.datamarket.backend.utils;

import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.impl.utils.UserDetailsImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class providing helper methods for Security.
 */

public class SecurityUtil {

    private SecurityUtil() {
    }

    public static User getCurrentUser() {
        UsernamePasswordAuthenticationToken authenticationToken =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        if (authenticationToken == null || !authenticationToken.isAuthenticated()) {
            throw new RuntimeException("Current user context is null. User not authenticated.");
        }

        UserDetailsImpl loginUser = (UserDetailsImpl) authenticationToken.getPrincipal();
        return loginUser.getUser();
    }
}
