package com.capellax.user_service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserProfileResponse {

    private String username;
    private String firstName;
    private String lastName;
    private String email;

}
