package com.tzp.zjzx.model.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileInternalDto {

    private Long userId;
    private String nickName;
}
