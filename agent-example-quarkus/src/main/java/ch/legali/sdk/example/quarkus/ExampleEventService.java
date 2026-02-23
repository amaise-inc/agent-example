package ch.legali.sdk.example.quarkus;

import ch.legali.api.events.BaseEvent;
import ch.legali.api.events.LegalCaseCreatedEvent;
import ch.legali.api.events.PongEvent;
import ch.legali.sdk.internal.HealthService;
import ch.legali.sdk.services.EventService;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ExampleEventService {

  private static final Logger log = LoggerFactory.getLogger(ExampleEventService.class);

  public static final String BUS_STARTED = "started";

  private boolean started = false;

  @Inject Config.Mapping config;

  @Inject EventService eventService;

  @Inject HealthService healthService;

  @Inject EventBus bus;

  @PostConstruct
  void init() {
    // NOTE: every subscribed event type must have a @ConsumeEvent handler that acknowledges it.
    // Only subscribe to events your integration actually handles - subscribed but unacknowledged
    // events accumulate in the queue and are re-delivered after 5 minutes.
    this.eventService.subscribe(
        PongEvent.class,

        // legalcase CRUD through frontend
        LegalCaseCreatedEvent.class
        //                LegalCaseStatusChangedEvent.class,
        //                LegalCaseUpdatedEvent.class,
        //                LegalCaseDeletedEvent.class,
        //                NotebookUpdatedEvent.class,
        //
        //                // all sourcefiles processed
        //                LegalCaseReadyEvent.class,
        //
        //                // sourcefiles CRUD through frontend
        //                SourceFileCreatedEvent.class,
        //                SourceFileUpdatedEvent.class,
        //                SourceFileReadyEvent.class,
        //
        //                // processing error
        //                SourceFileFailedEvent.class,
        //
        //                // export
        //                ExportCreatedEvent.class,
        //                ExportSharedEvent.class,
        //                ExportViewedEvent.class,

        //                // annotations
        //                AnnotationCreatedEvent.class,
        //                AnnotationUpdatedEvent.class,
        //                AnnotationDeletedEvent.class,
        //
        //                // collaboration
        //                TicketCreatedEvent.class,
        //                TicketUpdatedEvent.class
        );
  }

  @Scheduled(every = "10s", delayed = "3s")
  void schedule() {
    for (BaseEvent event : this.healthService.heartbeat()) {
      this.bus.publish(event.getClass().getSimpleName(), event);
    }

    // on first successful fetch, signal to app it's ready to do things.
    if (!this.started) {
      this.bus.publish(BUS_STARTED, Instant.now());
      this.started = true;
    }
  }

  @ConsumeEvent(value = BUS_STARTED)
  void start(Instant when) {
    log.info("🏓 Requesting a pong remote event");
    this.eventService.ping(this.config.tenants().get("department-1"));
    this.eventService.ping(this.config.tenants().get("department-2"));
  }

  @ConsumeEvent(value = "PongEvent")
  void consume(PongEvent event) {
    log.info("got pong event {}", event);
    this.eventService.acknowledge(event);
  }

  @ConsumeEvent(value = "LegalCaseCreatedEvent")
  void consume(LegalCaseCreatedEvent event) {
    String department =
        this.config.tenants().entrySet().stream()
            .filter(entry -> entry.getValue().equals(event.tenantId()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    log.info(
        "LegalCaseCreatedEvent\n Tenant: {} ({}): \n{} {}",
        department,
        event.tenantId(),
        event.legalCase().caseData().get("PII_FIRSTNAME"),
        event.legalCase().caseData().get("PII_LASTNAME"));
    this.eventService.acknowledge(event);
  }
}
