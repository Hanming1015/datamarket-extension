package com.synapse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Registration payload. */
@Data
public class RegisterRequest {

    @NotBlank(message = "username must not be empty")
    private String username;

    @NotBlank(message = "password must not be empty")
    private String password;

    private String name;

    @Email(message = "email format invalid")
    private String email;

    private String organization;
    // role 不由客户端指定:自助注册一律为 consumer,防越权。
}
