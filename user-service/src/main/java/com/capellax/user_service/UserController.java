package com.capellax.user_service;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

@Controller
@RequestMapping("/api/users/")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Validated @RequestBody RegisterRequest request) {
        String message = userService.registerUser(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/activate")
    public ResponseEntity<String> activateUser(@Validated @RequestBody ActivationRequest request) {
        String message = userService.activateUser(request);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        UserProfileResponse profile = userService.getUserProfileByEmail(email);
        return ResponseEntity.ok(profile);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> loginUser(@Validated LoginRequest loginRequest) {
        try {
            Map<String, Object> tokens = userService.loginUser(loginRequest);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }


}
