package com.viettel.user.seeder;

import com.viettel.user.entity.User;
import com.viettel.user.repository.UserRepository;
import com.viettel.user.repository.UserProfileRepository;
import com.viettel.user.entity.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            System.out.println("No users found. Seeding initial data...");

            User customer = User.builder()
                    .email("customer@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .roleId(1) // 1 - customer
                    .status("ACTIVE")
                    .build();

            User agent = User.builder()
                    .email("agent@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .roleId(2) // 2 - agent
                    .status("ACTIVE")
                    .build();

            User supervisor = User.builder()
                    .email("supervisor@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .roleId(3) // 3 - supervisor
                    .status("ACTIVE")
                    .build();

            userRepository.save(customer);
            userRepository.save(agent);
            userRepository.save(supervisor);
            userProfileRepository.save(UserProfile.builder().userId(customer.getId()).displayName("Demo Customer").build());
            userProfileRepository.save(UserProfile.builder().userId(agent.getId()).displayName("Demo Agent").build());
            userProfileRepository.save(UserProfile.builder().userId(supervisor.getId()).displayName("Demo Supervisor").build());

            System.out.println("Seeding completed successfully.");
        }
    }
}
