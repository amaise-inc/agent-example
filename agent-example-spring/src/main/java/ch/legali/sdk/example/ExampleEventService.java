package ch.legali.sdk.example;

// CHECKSTYLE IGNORE AvoidStarImport FOR NEXT 1 LINES
import ch.legali.api.events.*;
import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.internal.HealthService;
import ch.legali.sdk.services.EventService;
import ch.legali.sdk.services.FileService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** This service is used to react to events form the legal-i cloud. */
@Service
public class ExampleEventService {
  private static final Logger log = LoggerFactory.getLogger(ExampleEventService.class);
  private final FileService fileService;
  private final EventService eventService;
  private final ExampleConfig exampleConfig;
  private final ApplicationEventPublisher applicationEventPublisher;
  private boolean started = false;

  private final HealthService healthService;

  public ExampleEventService(
      FileService fileService,
      EventService eventService,
      ApplicationEventPublisher applicationEventPublisher,
      HealthService healthService,
      ExampleConfig exampleConfig) {
    this.fileService = fileService;
    this.eventService = eventService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.healthService = healthService;
    this.exampleConfig = exampleConfig;
  }

  @PostConstruct
  public void init() {
    // NOTE: all events that the agent subscribes to, need to be handled by an event listener.
    this.eventService.subscribe(
        PongEvent.class,

        // legalcase CRUD through frontend
        LegalCaseCreatedEvent.class,
        LegalCaseStatusChangedEvent.class,
        LegalCaseUpdatedEvent.class,
        NotebookUpdatedEvent.class,

        // all sourcefiles processed
        LegalCaseReadyEvent.class,

        // sourcefiles CRUD through frontend
        SourceFileCreatedEvent.class,
        SourceFileUpdatedEvent.class,
        SourceFileReadyEvent.class,

        // processing error
        SourceFileFailedEvent.class,

        // annotations
        AnnotationCreatedEvent.class,
        AnnotationUpdatedEvent.class,
        AnnotationDeletedEvent.class,

        // export
        ExportCreatedEvent.class,
        ExportSharedEvent.class,
        ExportViewedEvent.class,

        // messaging
        ThreadCreatedEvent.class,
        ThreadClosedEvent.class);
  }

  /** On connector start, ping the API to request a pong event */
  @EventListener
  public void onStartConnectorEvent(@SuppressWarnings("unused") StartConnectorEvent event) {
    log.info("🏓 Requesting a pong remote event for Department 1 and Department 2");
    this.eventService.ping(this.exampleConfig.getTenants().get("department-1"));
    this.eventService.ping(this.exampleConfig.getTenants().get("department-2"));
  }

  @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT3S")
  public void getEvents() {
    for (BaseEvent event : this.healthService.heartbeat()) {
      this.applicationEventPublisher.publishEvent(event);
    }

    // on first successful fetch, signal to app it's ready to do things.
    if (!this.started) {
      this.applicationEventPublisher.publishEvent(new StartConnectorEvent(this));
      this.started = true;
    }
  }

  public static class StartConnectorEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    public StartConnectorEvent(ExampleEventService source) {
      super(source);
    }
  }

  /*
   * NOTE: all events that the agent subscribes to, need to be handled by an event listener.
   */

  @EventListener
  public void handle(PongEvent event) {
    log.info("🏓 PingPong Event received:\n" + event.message());
    this.eventService.acknowledge(event);
  }

  // legalcase handlers
  @EventListener
  public void handle(LegalCaseCreatedEvent event) {
    String department =
        this.exampleConfig.getTenants().entrySet().stream()
            .filter(entry -> entry.getValue().equals(event.tenantId()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    log.info(
        "LegalCaseCreatedEvent\n "
            + "Tenant: "
            + department
            + " ("
            + event.tenantId()
            + "): "
            + "\n"
            + event.legalCase().caseData().get("PII_LASTNAME")
            + " "
            + event.legalCase().caseData().get("PII_LASTNAME"));
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(LegalCaseStatusChangedEvent event) {
    log.info("LegalCaseStatusChangedEvent: " + "\n" + event.legalCaseId() + " " + event.status());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(LegalCaseUpdatedEvent event) {
    log.info(
        "LegalCaseUpdatedEvent: "
            + "\n"
            + event.legalCase().firstname()
            + " "
            + event.legalCase().lastname());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(LegalCaseReadyEvent event) {
    // TODO: show how to resolve the department from the tenantId
    log.info("LegalCaseReadyEvent: " + "\n" + event.legalCaseId());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(NotebookUpdatedEvent event) {
    log.info("📓 NotebookUpdatedEvent: " + "\n" + event.notebook());
    this.eventService.acknowledge(event);
  }

  // sourcefiles handler

  @EventListener
  public void handle(SourceFileCreatedEvent event) {
    log.info("SourceFileCreatedEvent: " + "\n" + event.sourceFile().sourceFileId());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(SourceFileUpdatedEvent event) {
    log.info("SourceFileUpdatedEvent: " + "\n" + event.sourceFile().folder());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(SourceFileReadyEvent event) {
    log.info("SourceFileReadyEvent: " + "\n" + event.sourceFileId());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(SourceFileFailedEvent event) {
    log.info("SourceFileFailedEvent: " + "\n" + event.sourceFileId());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(ExportCreatedEvent event) {
    log.info("🍻  ExportCreatedEvent: " + event.export().exportId());
    log.info("    Recipient : " + event.export().recipient());
    log.info("    Case Id   : " + event.export().legalCaseId());
    log.info("    Timestamp : " + event.ts());

    try (InputStream is = this.fileService.downloadFile(event.export().file().uri())) {
      Files.createDirectories(Paths.get("./temp"));
      Files.copy(
          is,
          Path.of("./temp/" + event.export().file().filename()),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      e.printStackTrace();
    }
    log.info("⤵️  Downloaded file: {}", event.export().file().filename());

    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(ExportSharedEvent event) {
    log.info(
        "✉️ ExportSharedEvent: "
            + event.export().exportId()
            + "\n"
            + event.method()
            + "\n"
            + event.export().file().uri()
            + "\n"
            + event.email());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(ExportViewedEvent event) {
    log.info(
        "📖 ExportViewedEvent: "
            + "\n"
            + event.export().legalCaseId()
            + " "
            + event.user().remoteAddr());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(AnnotationCreatedEvent event) {
    log.info(
        "📖 AnnotationCreatedEvent: "
            + "\n"
            + event.annotation().legalCaseId()
            + " "
            + event.user().remoteAddr()
            + "\nXFDF: "
            + event.annotation().xfdf());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(AnnotationUpdatedEvent event) {
    log.info(
        "📖 AnnotationUpdatedEvent: "
            + "\n"
            + event.annotation().legalCaseId()
            + " "
            + event.user().remoteAddr()
            + "\nXFDF: "
            + event.annotation().xfdf());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(AnnotationDeletedEvent event) {
    log.info(
        "📖 AnnotationDeletedEvent: "
            + "\n"
            + event.annotation().legalCaseId()
            + " "
            + event.user().remoteAddr()
            + "\nAnnotation XFDF: "
            + event.annotation().xfdf()
            + "\nSourceFile XFDF: "
            + event.annotation().sourceFileXfdf());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(ThreadCreatedEvent event) {
    log.info("🧵 ThreadCreatedEvent: " + "\n" + "Subject: " + event.subject());
    this.eventService.acknowledge(event);
  }

  @EventListener
  public void handle(ThreadClosedEvent event) {
    log.info(
        "🧵 ThreadClosedEvent: "
            + "\n"
            + "Subject: "
            + event.subject()
            + "\n"
            + (event.attachments().size() > 0
                ? "Message attachment URI(s):"
                    + "\n"
                    + event.attachments().stream().collect(Collectors.joining("\n"))
                : ""));

    this.eventService.acknowledge(event);
  }
}
