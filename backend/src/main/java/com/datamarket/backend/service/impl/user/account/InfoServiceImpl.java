package com.datamarket.backend.service.impl.user.account;

import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.impl.utils.UserDetailsImpl;
import com.datamarket.backend.service.user.account.InfoService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the InfoService interface.
 */

@Service
public class InfoServiceImpl implements InfoService {

    @Override
    public Map<String, Object> getInfo() {
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        UserDetailsImpl loginUser = (UserDetailsImpl) authentication.getPrincipal();
        User user = loginUser.getUser();

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("organization", user.getOrganization());
        map.put("role", user.getRole());
        map.put("createdAt", user.getCreatedAt());

        return map;
    }
}
