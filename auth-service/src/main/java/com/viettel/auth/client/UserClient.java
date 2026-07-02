package com.viettel.auth.client;

import com.viettel.auth.dto.UserDto;
import com.viettel.auth.dto.ProvisioningDtos.*;
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

    public ProvisionedUser createCustomer(CustomerProvision request) {
        return restClient.post().uri(userServiceUrl + "/api/user/internal/customers").body(request).retrieve().body(ProvisionedUser.class);
    }
    public InviteView createInvite(InviteProvision request) {
        return restClient.post().uri(userServiceUrl + "/api/user/internal/invites").body(request).retrieve().body(InviteView.class);
    }
    public InviteView resend(long id, ResendProvision request) {
        return restClient.put().uri(userServiceUrl + "/api/user/internal/invites/{id}/resend", id).body(request).retrieve().body(InviteView.class);
    }
    public InviteView claim(ClaimProvision request) {
        return restClient.post().uri(userServiceUrl + "/api/user/internal/invites/claim").body(request).retrieve().body(InviteView.class);
    }
    public ProvisionedUser provision(long id, CompleteProvision request) {
        return restClient.post().uri(userServiceUrl + "/api/user/internal/invites/{id}/provision", id).body(request).retrieve().body(ProvisionedUser.class);
    }
    public void releaseClaim(long id) {
        restClient.put().uri(userServiceUrl + "/api/user/internal/invites/{id}/release", id).retrieve().toBodilessEntity();
    }
    public void rollback(long inviteId, long userId) {
        restClient.delete().uri(userServiceUrl + "/api/user/internal/invites/{inviteId}/users/{userId}", inviteId, userId).retrieve().toBodilessEntity();
    }
}
