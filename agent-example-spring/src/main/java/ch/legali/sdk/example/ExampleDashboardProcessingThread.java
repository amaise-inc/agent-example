package ch.legali.sdk.example;

import ch.legali.api.events.LegalCaseReadyEvent;
import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.models.AgentDashboardDTO;
import ch.legali.sdk.models.AgentLegalCaseDTO;
import ch.legali.sdk.models.AgentSourceFileDTO;
import ch.legali.sdk.services.DashboardService;
import ch.legali.sdk.services.EventService;
import ch.legali.sdk.services.LegalCaseService;
import ch.legali.sdk.services.SourceFileService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ExampleDashboardProcessingThread implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ExampleDashboardProcessingThread.class);

  private final LegalCaseService legalCaseService;
  private final SourceFileService sourceFileService;
  private final DashboardService dashboardService;
  private final EventService eventService;
  private final ExampleConfig exampleConfig;

  public ExampleDashboardProcessingThread(
      LegalCaseService legalCaseService,
      SourceFileService sourceFileService,
      DashboardService dashboardService,
      EventService eventService,
      ExampleConfig exampleConfig) {
    this.legalCaseService = legalCaseService;
    this.sourceFileService = sourceFileService;
    this.dashboardService = dashboardService;
    this.eventService = eventService;
    this.exampleConfig = exampleConfig;
  }

  /**
   * This example thread requires pipeline processing to be enabled, see application.properties:
   * legali.default-metadata.legali.pipeline.disabled=false Also make sure to not run it along other
   * threads to avoid conflicts.
   */
  @PostConstruct
  public void init() {
    // NOTE: all events that the agent subscribes to, need to be handled by an event listener.
    this.eventService.subscribe(LegalCaseReadyEvent.class);
  }

  @EventListener
  public void handle(LegalCaseReadyEvent event) {
    log.info("LegalCaseReadyEvent: " + "\n" + event.legalCaseId());
    // Acknowledge event
    this.eventService.acknowledge(event);

    log.info(
        "Retrieving processed dashboard {} for legal case {}",
        this.exampleConfig.getDashboardId(),
        event.legalCaseId());

    // Fetch dashboard with answers
    AgentDashboardDTO dashboard =
        this.dashboardService.get(event.legalCaseId(), this.exampleConfig.getDashboardId());

    log.info("Dashboard has {} answers", dashboard.answers().size());
    log.info("Dashboard can be accessed at {}", dashboard.url());

    // Print question-answer pairs
    StringBuilder questionAnswerPairs = new StringBuilder();
    dashboard
        .answers()
        .forEach(
            answer ->
                questionAnswerPairs
                    .append(answer.title())
                    .append(System.lineSeparator())
                    .append(answer.answer())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator()));
    log.info("Question-Answer Pairs:%n%s", questionAnswerPairs.toString());

    /* IMPORTANT: Manage data lifecycle
     * The data lifecycle is fully managed by you, delete the legal case according to
     * your internal policies.
     *
     * Example: automated workspaces, legal cases may be deleted if they do not require manual intervention.
     * if (dashboard.answers().get(0).trafficLight() == AgentDashboardTrafficLightAnswerDTO.TrafficLight.GREEN) {
     *    this.legalCaseService.delete(event.legalCaseId());
     * }
     */
    this.legalCaseService.delete(event.legalCaseId());
  }

  @Override
  public void run() {
    log.info("🚀 Starting ExampleDashboardProcessingThread");

    log.info("🗂  Adding LegalCase");
    AgentLegalCaseDTO legalCase =
        AgentLegalCaseDTO.builder()
            .legalCaseId(UUID.randomUUID())
            .caseData(
                Map.ofEntries(
                    Map.entry("PII_FIRSTNAME", "Maria"), Map.entry("PII_LASTNAME", "Bernasconi")))
            .reference("123-456-789")
            // Pass the UserID from SSO
            .owner("DummyIamUser")
            // or pass the user's e-mail
            // .ownerEmail("dummy@user.com")
            .accessGroup("group1")
            .putMetadata("meta.dummy", "dummy value")
            .build();
    this.legalCaseService.create(legalCase, this.exampleConfig.getTenants().get("department-1"));

    /*
     * To keep a constant memory footprint on the agent, the SDK uses a FileObject and
     * not a ByteArrayResource. PDF files can be large if they contain images (>
     * 500MB), in multithreaded mode this leads to unwanted spikes in memory usage.
     * Ideally the files are chunked downloaded to a temporary file and then passed to
     * the SDK.
     */
    log.info("🧾  Creating SourceFile");
    AgentSourceFileDTO sourceFile =
        AgentSourceFileDTO.builder()
            .sourceFileId(UUID.randomUUID())
            .legalCaseId(legalCase.legalCaseId())
            .folder("unknown")
            // Use filename as reference
            .fileReference("sample.pdf")
            .putMetadata("legali.pipeline.disabled", "false") // Enable pipeline processing
            // Optionally, pass the structured data for this document stored in your system (e.g.
            // Sunet XML for Switzerland)
            .structuredData(
                "<?xml version=\"1.0 encoding=\"UTF-8\"?><claimReport>...</claimReport>")
            .build();

    log.info("All done, waiting for LegalCaseReadyEvent...");

    ClassPathResource cp = new ClassPathResource("sample.pdf");
    try (InputStream is = cp.getInputStream()) {
      this.sourceFileService.create(sourceFile, is);
    } catch (IOException e) {
      log.error("🙅‍  Failed to create SourceFile", e);
    }
  }
}
