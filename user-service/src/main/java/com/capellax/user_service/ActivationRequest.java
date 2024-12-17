package com.capellax.user_service;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivationRequest {

    @NotBlank(message = "Email cannot be blank")
    private String email;

    @NotBlank(message = "Activation code cannot be blank")
    private String activationCode;

}
