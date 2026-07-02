package com.viettel.user;
import com.viettel.user.dto.ProvisionDtos.*;
import com.viettel.user.repository.UserRepository;
import com.viettel.user.service.ProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:provisioning;MODE=PostgreSQL","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class ProvisioningServiceTest {
 @Autowired ProvisioningService service; @Autowired UserRepository users;
 @Test void customerIsActiveAndDuplicateEmailIsRejected(){
  ProvisionedUser user=service.createCustomer(new CustomerRequest("New@Example.com","hash","New Customer"));
  assertThat(user.status()).isEqualTo("ACTIVE"); assertThat(user.roleId()).isEqualTo(1);
  assertThat(users.findByEmail("new@example.com")).isPresent();
  assertThatThrownBy(()->service.createCustomer(new CustomerRequest("NEW@example.com","hash","Again"))).isInstanceOf(ResponseStatusException.class);
 }
 @Test void inviteIsSingleClaimAndSupervisorAssignmentCanBeProvisioned(){
  InviteView invite=service.createInvite(new CreateInviteRequest("lead2@example.com",3,Set.of("support"),Set.of("MANAGE_AGENT"),3L,"abc",Instant.now().plusSeconds(3600)));
  InviteView claimed=service.claim(new ClaimInviteRequest("abc")); assertThat(claimed.status()).isEqualTo("CLAIMED");
  assertThatThrownBy(()->service.claim(new ClaimInviteRequest("abc"))).isInstanceOf(ResponseStatusException.class);
  ProvisionedUser user=service.provision(invite.id(),new ProvisionInviteRequest("hash","Lead"));
  assertThat(user.roleId()).isEqualTo(3); assertThat(user.status()).isEqualTo("ACTIVE");
 }
}
