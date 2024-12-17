package com.capellax.user_service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final Keycloak keycloak;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    @Value("${keycloak.login.token-url}")
    private String keycloakLoginUrl;

    @Value("${keycloak.login.grant_type}")
    private String keycloakLoginGrantType;

    @Value("${keycloak.login.client_id}")
    private String keycloakLoginClientId;

    @Value("${keycloak.login.client_secret}")
    private String keycloakLoginClientSecret;

    public UserService(UserRepository userRepository, Keycloak keycloak, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, PasswordEncoder passwordEncoder, RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.keycloak = keycloak;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.rabbitTemplate = rabbitTemplate;
    }

    public String registerUser(RegisterRequest request) {
        String activationCode = generateActivationCode();
        saveUserToRedis(request, activationCode);

        Map<String, String> message = new HashMap<>();
        message.put("email", request.getEmail());
        message.put("firstName", request.getFirstName());
        message.put("lastName", request.getLastName());
        message.put("activationCode", activationCode);

        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend("activation-queue", jsonMessage);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to RabbitMQ", e);
        }
        return "User registered successfully. Check your email for the activation code.";
    }

    private void saveUserToRedis(RegisterRequest request, String activationCode) {
        try {
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", UUID.randomUUID().toString());
            userData.put("username", request.getUsername());
            userData.put("firstName", request.getFirstName());
            userData.put("lastName", request.getLastName());
            userData.put("email", request.getEmail());
            userData.put("password", request.getPassword());
            userData.put("activationCode", activationCode);

            redisTemplate.opsForValue().set(request.getEmail(),
                    objectMapper.writeValueAsString(userData),
                    Duration.ofMinutes(10));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error saving user data to Redis", e);
        }
    }

    @Transactional
    public String activateUser(ActivationRequest request) {
        try {
            String userDataJson = redisTemplate.opsForValue().get(request.getEmail());
            if (userDataJson == null) {
                throw new RuntimeException("Activation code expired or invalid.");
            }

            Map<String, Object> userData = objectMapper.readValue(userDataJson, new TypeReference<>() {});
            if (!request.getActivationCode().equals(userData.get("activationCode"))) {
                throw new RuntimeException("Invalid activation code.");
            }

            UsersResource usersResource = keycloak.realm("demo-realm").users();
            UserRepresentation user = new UserRepresentation();
            user.setUsername((String) userData.get("username"));
            user.setFirstName((String) userData.get("firstName"));
            user.setLastName((String) userData.get("lastName"));
            user.setEmail((String) userData.get("email"));
            user.setEnabled(true);
            user.setEmailVerified(true);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue((String) userData.get("password"));
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            Response response = usersResource.create(user);
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create user in Keycloak. Status: " + response.getStatus());
            }

            String locationHeader = response.getHeaderString("Location");
            String keycloakId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);

            User dbUser = new User();
            dbUser.setKeycloakId(keycloakId);
            dbUser.setUsername(userData.get("username").toString());
            dbUser.setFirstName((String) userData.get("firstName"));
            dbUser.setLastName((String) userData.get("lastName"));
            dbUser.setEmail((String) userData.get("email"));
            dbUser.setPassword(passwordEncoder.encode((String) userData.get("password")));
            dbUser.setActivated(true);

            userRepository.save(dbUser);

            redisTemplate.delete(request.getEmail());

            return "User activated and registered successfully.";

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing user data from Redis", e);
        }
    }

    private String generateActivationCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    public UserProfileResponse getUserProfileByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        return new UserProfileResponse(
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }

    public Map<String, Object> loginUser(LoginRequest loginRequest) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", keycloakLoginGrantType);
            requestBody.add("client_id", keycloakLoginClientId);
            requestBody.add("client_secret", keycloakLoginClientSecret);
            requestBody.add("username", loginRequest.getUsername());
            requestBody.add("password", loginRequest.getPassword());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(keycloakLoginUrl, HttpMethod.POST, requestEntity, Map.class);

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }


}
