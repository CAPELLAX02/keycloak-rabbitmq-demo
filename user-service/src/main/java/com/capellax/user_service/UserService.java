package com.capellax.user_service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        String keycloakId = UUID.randomUUID().toString();
        String activationCode = generateActivationCode();

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setKeycloakId(keycloakId);
        user.setActivationCode(activationCode);

        return userRepository.save(user);
    }

    private String generateActivationCode() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

}
