package com.example.javaapp.controller;

import com.example.javaapp.model.User;
import com.example.javaapp.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "John Doe", "john.doe@example.com", "1234567890");
    }

    @Test
    void getAllUsers_ShouldReturnUserList() throws Exception {
        when(userService.getAllUsers()).thenReturn(Arrays.asList(testUser));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].email").value("john.doe@example.com"));
    }

    @Test
    void getUserById_WhenExists_ShouldReturnUser() throws Exception {
        when(userService.getUserById(1L)).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void getUserById_WhenNotExists_ShouldReturn404() throws Exception {
        when(userService.getUserById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_WithValidData_ShouldReturnCreated() throws Exception {
        when(userService.createUser(any(User.class))).thenReturn(testUser);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"John Doe\",\"email\":\"john.doe@example.com\",\"phone\":\"1234567890\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void updateUser_WhenExists_ShouldReturnUpdatedUser() throws Exception {
        User updatedUser = new User(1L, "Jane Doe", "jane.doe@example.com", "0987654321");
        when(userService.updateUser(eq(1L), any(User.class))).thenReturn(Optional.of(updatedUser));

        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"email\":\"jane.doe@example.com\",\"phone\":\"0987654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void deleteUser_WhenExists_ShouldReturnNoContent() throws Exception {
        when(userService.deleteUser(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isNoContent());
    }
}
