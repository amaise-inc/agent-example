package ch.legali.sdk.example.config;

import ch.legali.sdk.internal.HealthService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AgentHealthIndicator implements HealthIndicator {
  private final HealthService healthService;

  public AgentHealthIndicator(HealthService healthService) {
    this.healthService = healthService;
  }

  @Override
  public Health health() {

    Health.Builder status = Health.up();
    if (this.healthService.getState() == HealthService.HealthState.DOWN) {
      status = Health.down();
    }
    return status.build();
  }
}
