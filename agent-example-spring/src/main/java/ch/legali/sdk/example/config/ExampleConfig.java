package ch.legali.sdk.example.config;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "legali.example")
public class ExampleConfig {

  /** Example Connector iterations (per thread) */
  private int iterations;

  /** Optional path for sample pdf files. If blank the sample.pdf form resources will be used. */
  private String filesPath;

  /** cleanup environment before starting: deletes all legal cases added by this example. */
  private boolean cleanup = false;

  /** Map of workspaces that reflect departments . */
  private Map<String, UUID> tenants = Map.of();

  private UUID dashboardId;

  public int getIterations() {
    return this.iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public String getFilesPath() {
    return this.filesPath;
  }

  public void setFilesPath(String filesPath) {
    this.filesPath = filesPath;
  }

  public boolean isCleanup() {
    return this.cleanup;
  }

  public void setCleanup(boolean cleanup) {
    this.cleanup = cleanup;
  }

  public Map<String, UUID> getTenants() {
    return Map.copyOf(this.tenants);
  }

  public void setTenants(Map<String, UUID> tenants) {
    this.tenants = tenants;
  }

  public UUID getDashboardId() {
    return this.dashboardId;
  }

  public void setDashboardId(UUID dashboardId) {
    this.dashboardId = dashboardId;
  }
}
