package com.synapse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Login payload. */
@Data
public class LoginRequest {

    @NotBlank(message = "username must not be empty")
    private String username;

    @NotBlank(message = "password must not be empty")
    private String password;
}
