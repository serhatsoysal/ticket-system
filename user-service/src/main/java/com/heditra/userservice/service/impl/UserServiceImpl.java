package com.heditra.userservice.service.impl;

import com.heditra.userservice.model.User;
import com.heditra.userservice.model.UserRole;
import com.heditra.userservice.repository.UserRepository;
import com.heditra.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_CREATED_TOPIC = "user-created";
    private static final String USER_UPDATED_TOPIC = "user-updated";
    private static final String USER_DELETED_TOPIC = "user-deleted";

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public User createUser(User user) {
        log.info("Creating new user with username: {}", user.getUsername());

        validateUserUniqueness(user);

        User savedUser = userRepository.save(user);
        publishUserEvent(USER_CREATED_TOPIC, savedUser);

        log.info("User created successfully with ID: {}", savedUser.getId());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        log.debug("Fetching user by ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "'username:' + #username")
    public User getUserByUsername(String username) {
        log.debug("Fetching user by username: {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "'email:' + #email")
    public User getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        log.debug("Fetching all users");
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUsersByRole(UserRole role) {
        log.debug("Fetching users by role: {}", role);
        return userRepository.findByRole(role);
    }

    @Override
    @Transactional
    @CachePut(value = "users", key = "#id")
    public User updateUser(Long id, User user) {
        log.info("Updating user with ID: {}", id);

        User existingUser = getUserById(id);
        updateUserFields(existingUser, user);

        User updatedUser = userRepository.save(existingUser);
        publishUserEvent(USER_UPDATED_TOPIC, updatedUser);

        log.info("User updated successfully with ID: {}", id);
        return updatedUser;
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        User user = getUserById(id);
        userRepository.delete(user);
        publishUserEvent(USER_DELETED_TOPIC, user);

        log.info("User deleted successfully with ID: {}", id);
    }

    private void validateUserUniqueness(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists: " + user.getUsername());
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists: " + user.getEmail());
        }
    }

    private void updateUserFields(User existing, User updated) {
        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setEmail(updated.getEmail());
        existing.setRole(updated.getRole());
    }

    private void publishUserEvent(String topic, User user) {
        kafkaTemplate.send(topic, user.getId().toString(), user)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("User event published to topic: {}", topic);
                    } else {
                        log.error("Failed to publish user event to topic: {}", topic, ex);
                    }
                });
    }
}

