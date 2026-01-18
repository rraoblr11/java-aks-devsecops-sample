package com.example.javaapp.service;

import com.example.javaapp.model.User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final Map<Long, User> userRepository = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    public UserService() {
        userRepository.put(1L, new User(1L, "John Doe", "john.doe@example.com", "1234567890"));
        userRepository.put(2L, new User(2L, "Jane Smith", "jane.smith@example.com", "0987654321"));
        idCounter.set(3L);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userRepository.values());
    }

    public Optional<User> getUserById(Long id) {
        return Optional.ofNullable(userRepository.get(id));
    }

    public User createUser(User user) {
        Long id = idCounter.getAndIncrement();
        user.setId(id);
        userRepository.put(id, user);
        return user;
    }

    public Optional<User> updateUser(Long id, User user) {
        if (userRepository.containsKey(id)) {
            user.setId(id);
            userRepository.put(id, user);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public boolean deleteUser(Long id) {
        return userRepository.remove(id) != null;
    }
}
