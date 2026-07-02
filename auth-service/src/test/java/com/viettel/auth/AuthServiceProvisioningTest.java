package com.viettel.auth;
import com.viettel.auth.client.AgentClient;
import com.viettel.auth.client.EmailClient;
import com.viettel.auth.client.UserClient;
import com.viettel.auth.dto.AcceptInviteRequest;
import com.viettel.auth.dto.ProvisioningDtos.*;
import com.viettel.auth.service.AuthService;
import com.viettel.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AuthServiceProvisioningTest {
 UserClient users=mock(UserClient.class); AgentClient agents=mock(AgentClient.class); EmailClient emails=mock(EmailClient.class);
 PasswordEncoder encoder=mock(PasswordEncoder.class); AuthService service;
 @BeforeEach void setup(){
  service=new AuthService(agents,emails, Duration.ofHours(24),"http://frontend/accept");
  JwtUtil jwt=new JwtUtil(); ReflectionTestUtils.setField(jwt,"secret","404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
  ReflectionTestUtils.setField(jwt,"expirationTime",3600000L); ReflectionTestUtils.setField(jwt,"refreshExpirationTime",86400000L);
  ReflectionTestUtils.setField(service,"jwtUtil",jwt); ReflectionTestUtils.setField(service,"userClient",users); ReflectionTestUtils.setField(service,"passwordEncoder",encoder);
  when(encoder.encode(anyString())).thenReturn("hash");
 }
 @Test void rollsBackUserWhenAgentProfileCreationFails(){
  when(users.claim(any())).thenReturn(new InviteView(5L,"agent@example.com",2,Set.of("support"),Set.of(),Instant.now().plusSeconds(60),"CLAIMED"));
  when(users.provision(eq(5L),any())).thenReturn(new ProvisionedUser(20L,"agent@example.com",2,"ACTIVE","Agent"));
  doThrow(new RuntimeException("redis unavailable")).when(agents).createProfile(eq(20L),any());
  AcceptInviteRequest request=new AcceptInviteRequest("token","password123","Agent",2,Set.of("support"),Set.of("webchat"));
  assertThatThrownBy(()->service.acceptInvite(request)).isInstanceOf(ResponseStatusException.class);
  verify(users).rollback(5L,20L); verify(users).releaseClaim(5L);
 }
}
