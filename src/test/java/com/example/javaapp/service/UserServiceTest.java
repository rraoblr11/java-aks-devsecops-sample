package com.example.javaapp.service;

import com.example.javaapp.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService();
    }

    @Test
    void getAllUsers_ShouldReturnAllUsers() {
        List<User> users = userService.getAllUsers();
        assertNotNull(users);
        assertEquals(2, users.size());
    }

    @Test
    void getUserById_WhenExists_ShouldReturnUser() {
        Optional<User> user = userService.getUserById(1L);
        assertTrue(user.isPresent());
        assertEquals("John Doe", user.get().getName());
    }

    @Test
    void getUserById_WhenNotExists_ShouldReturnEmpty() {
        Optional<User> user = userService.getUserById(999L);
        assertFalse(user.isPresent());
    }

    @Test
    void createUser_ShouldAddNewUser() {
        User newUser = new User(null, "Test User", "test@example.com", "1111111111");
        User createdUser = userService.createUser(newUser);
        
        assertNotNull(createdUser.getId());
        assertEquals("Test User", createdUser.getName());
        assertEquals(3, userService.getAllUsers().size());
    }

    @Test
    void updateUser_WhenExists_ShouldUpdateUser() {
        User updatedUser = new User(null, "Updated Name", "updated@example.com", "2222222222");
        Optional<User> result = userService.updateUser(1L, updatedUser);
        
        assertTrue(result.isPresent());
        assertEquals("Updated Name", result.get().getName());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void updateUser_WhenNotExists_ShouldReturnEmpty() {
        User updatedUser = new User(null, "Updated Name", "updated@example.com", "2222222222");
        Optional<User> result = userService.updateUser(999L, updatedUser);
        
        assertFalse(result.isPresent());
    }

    @Test
    void deleteUser_WhenExists_ShouldReturnTrue() {
        boolean result = userService.deleteUser(1L);
        assertTrue(result);
        assertEquals(1, userService.getAllUsers().size());
    }

    @Test
    void deleteUser_WhenNotExists_ShouldReturnFalse() {
        boolean result = userService.deleteUser(999L);
        assertFalse(result);
    }
}
