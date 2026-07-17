package com.synapse.auth.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Safe user view returned to clients — deliberately excludes the password hash.
 */
@Data
public class UserVO {

    private String id;
    private String username;
    private String name;
    private String email;
    private String organization;
    private String role;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime createdAt;
}
