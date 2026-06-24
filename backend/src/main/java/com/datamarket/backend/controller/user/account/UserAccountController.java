package com.datamarket.backend.controller.user.account;

import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.user.account.InfoService;
import com.datamarket.backend.service.user.account.LoginService;
import com.datamarket.backend.service.user.account.RegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for managing UserAccount related endpoints and operations.
 */

@RestController
@RequestMapping("/api/user/account")
public class UserAccountController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private InfoService infoService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return ResponseEntity.ok(loginService.login(username, password));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String name = body.get("name");
        String email = body.get("email");
        String organization = body.get("organization");
        String role = body.get("role");

        User user = registerService.register(username, password, name, email, organization, role);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/info")
    public ResponseEntity<?> getInfo() {
        return ResponseEntity.ok(infoService.getInfo());
    }
}
