package com.ktalk.domain.user.controller;

import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import com.ktalk.domain.user.service.UserService;
import com.ktalk.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            User user = userService.register(
                    request.get("username"),
                    request.get("password"),
                    request.get("email")
            );
            String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "회원가입 성공",
                    "token", token,
                    "user", toUserMap(user)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            User user = userService.login(
                    request.get("username"),
                    request.get("password")
            );
            String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "로그인 성공",
                    "token", token,
                    "user", toUserMap(user)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "인증되지 않았습니다."));
        }

        try {
            userService.changePassword(userId, request.get("currentPassword"), request.get("newPassword"));
            return ResponseEntity.ok(Map.of("success", true, "message", "비밀번호가 변경되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "인증되지 않았습니다."));
        }

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.of("success", true, "user", toUserMap(user))))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("success", false, "message", "사용자를 찾을 수 없습니다.")));
    }

    private Map<String, Object> toUserMap(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
        );
    }
}