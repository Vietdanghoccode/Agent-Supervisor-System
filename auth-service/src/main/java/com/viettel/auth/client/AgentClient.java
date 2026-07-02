package com.viettel.auth.client;
import com.viettel.auth.dto.ProvisioningDtos.AgentProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component
public class AgentClient {
 private final RestClient client; private final String url;
 public AgentClient(RestClient client,@Value("${app.agent-service.url}") String url){this.client=client;this.url=url;}
 public void createProfile(long userId, AgentProfile profile){
  client.put().uri(url+"/agents/{id}/profile",userId).body(profile).retrieve().toBodilessEntity();
 }
}
