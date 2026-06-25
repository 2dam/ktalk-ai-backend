package com.ktalk.config;

import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        // ID가 1인 사용자가 없으면 생성
        if (!userRepository.existsById(1L)) {
            User user = new User();
            user.setId(1L);
            user.setUsername("testuser");
            user.setPassword("password");
            user.setEmail("test@example.com");
            userRepository.save(user);
            System.out.println("✅ 테스트 사용자 생성 완료 (ID: 1)");
        }
    }
}