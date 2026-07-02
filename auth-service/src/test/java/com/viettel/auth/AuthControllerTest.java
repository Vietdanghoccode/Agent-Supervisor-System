package com.viettel.auth;
import com.viettel.auth.controller.AuthController;
import com.viettel.auth.dto.AuthResponse;
import com.viettel.auth.dto.InviteResponse;
import com.viettel.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters=false)
class AuthControllerTest {
 @Autowired MockMvc mvc; @MockitoBean AuthService service;
 @Test void customerSignupReturnsCreatedTokens() throws Exception {
  when(service.signupCustomer(any())).thenReturn(AuthResponse.builder().accessToken("a").refreshToken("r").build());
  mvc.perform(post("/api/auth/signup/customer").with(csrf()).contentType("application/json").content("""
   {"email":"new@example.com","password":"password123","displayName":"New User"}
   """)).andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").value("a"));
 }
 @Test void invalidSignupIsRejected() throws Exception {
  mvc.perform(post("/api/auth/signup/customer").with(csrf()).contentType("application/json").content("""
   {"email":"bad","password":"short","displayName":""}
   """)).andExpect(status().isBadRequest());
 }
 @Test void createsAgentInviteWithSupervisorBearer() throws Exception {
  when(service.createInvite(any(),eq("token"))).thenReturn(new InviteResponse(1,"agent@example.com","AGENT",Instant.now().plusSeconds(60),"PENDING"));
  mvc.perform(post("/api/auth/invites").with(csrf()).header("Authorization","Bearer token").contentType("application/json").content("""
   {"email":"agent@example.com","role":"AGENT","teams":["support"],"permissions":[]}
   """)).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("AGENT"));
 }
}
