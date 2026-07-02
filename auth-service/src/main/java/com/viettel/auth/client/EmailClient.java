package com.viettel.auth.client;
import com.viettel.auth.dto.ProvisioningDtos.InvitationEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component
public class EmailClient {
 private final RestClient client; private final String url;
 public EmailClient(RestClient client,@Value("${app.email-service.url}") String url){this.client=client;this.url=url;}
 public void sendInvitation(InvitationEmail email){
  try { client.post().uri(url+"/internal/emails/invitations").body(email).retrieve().toBodilessEntity(); }
  catch (RuntimeException failure) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Invitation email delivery failed",failure); }
 }
}
