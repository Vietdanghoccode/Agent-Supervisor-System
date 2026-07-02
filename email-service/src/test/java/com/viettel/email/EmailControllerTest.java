package com.viettel.email;
import com.viettel.email.api.EmailController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(controllers=EmailController.class,properties="app.mail.from=no-reply@example.com")
class EmailControllerTest {
 @Autowired MockMvc mvc; @MockitoBean JavaMailSender sender;
 @Test void acceptsValidInvitation() throws Exception {
  mvc.perform(post("/internal/emails/invitations").contentType("application/json").content("""
   {"to":"agent@example.com","role":"AGENT","inviter":"lead@example.com","acceptUrl":"http://frontend/accept?token=x","expiresAt":"2099-01-01T00:00:00Z"}
   """)).andExpect(status().isAccepted());
 }
 @Test void rejectsInvalidEmail() throws Exception {
  mvc.perform(post("/internal/emails/invitations").contentType("application/json").content("""
   {"to":"bad","role":"AGENT","inviter":"lead@example.com","acceptUrl":"x","expiresAt":"2099-01-01T00:00:00Z"}
   """)).andExpect(status().isBadRequest());
 }
}
