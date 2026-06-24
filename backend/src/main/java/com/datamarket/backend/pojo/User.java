package com.datamarket.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents the User entity.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("users")
public class User {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String email;

    private String organization;

    private String role;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime createdAt;

    private String username;

    private String password;
}
