package com.viettel.auth.client;

import com.viettel.auth.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {

    private final RestClient restClient;
    private final String userServiceUrl;

    public UserClient(RestClient restClient, @Value("${app.user-service.url}") String userServiceUrl) {
        this.restClient = restClient;
        this.userServiceUrl = userServiceUrl;
    }

    public UserDto getUserByEmail(String email) {
        try {
            return restClient.get()
                    .uri(userServiceUrl + "/api/user/internal/users/by-email?email={email}", email)
                    .retrieve()
                    .body(UserDto.class);
        } catch (Exception e) {
            System.err.println("Error fetching user: " + e.getMessage());
            return null;
        }
    }
}
