package com.gayadi.server.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request.nickname());
    }

    @GetMapping("/{userId}")
    Map<String, Object> get(@PathVariable String userId) {
        return service.get(userId);
    }

    public record CreateUserRequest(@NotBlank @Size(max = 80) String nickname) {
    }
}
