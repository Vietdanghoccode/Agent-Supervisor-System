package com.viettel.email.api;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/internal/emails")
public class EmailController {
 private final JavaMailSender sender; private final String from;
 public EmailController(JavaMailSender sender,@Value("${app.mail.from}") String from){this.sender=sender;this.from=from;}
 @PostMapping("/invitations")
 public ResponseEntity<Void> invitation(@Valid @RequestBody InvitationEmailRequest request){
  SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(from); message.setTo(request.to());
  message.setSubject("Invitation to Agent Supervisor System");
  message.setText("You were invited by "+request.inviter()+" as "+request.role()+".\n\nAccept: "+request.acceptUrl()+"\n\nThis invitation expires at "+request.expiresAt()+".");
  sender.send(message); return ResponseEntity.accepted().build();
 }
}
