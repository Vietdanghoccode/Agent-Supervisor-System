package com.viettel.user.controller;

import com.viettel.user.dto.UserDto;
import com.viettel.user.entity.User;
import com.viettel.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/user/internal")
public class InternalUserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/users/by-email")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            UserDto dto = UserDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .passwordHash(user.getPasswordHash())
                    .roleId(user.getRoleId())
                    .status(user.getStatus())
                    .build();
            return ResponseEntity.ok(dto);
        }
        
        return ResponseEntity.notFound().build();
    }
}
