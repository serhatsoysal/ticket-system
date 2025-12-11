package com.heditra.userservice.service;

import com.heditra.userservice.model.User;
import com.heditra.userservice.model.UserRole;

import java.util.List;

public interface UserService {

    User createUser(User user);

    User getUserById(Long id);

    User getUserByUsername(String username);

    User getUserByEmail(String email);

    List<User> getAllUsers();

    List<User> getUsersByRole(UserRole role);

    User updateUser(Long id, User user);

    void deleteUser(Long id);
}

