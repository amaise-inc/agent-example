package ch.legali.sdk.example.quarkus;

import ch.legali.api.events.LegalCaseReadyEvent;
import ch.legali.sdk.models.AgentDashboardDTO;
import ch.legali.sdk.models.AgentDashboardJsonAnswerDTO;
import ch.legali.sdk.models.AgentDashboardListAnswerDTO;
import ch.legali.sdk.models.AgentDashboardListHeaderDTO;
import ch.legali.sdk.models.AgentDashboardTrafficLightAnswerDTO;
import ch.legali.sdk.services.DashboardService;
import ch.legali.sdk.services.LegalCaseService;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example service demonstrating dashboard answering. After all source files in a legal case have
 * been processed (LegalCaseReadyEvent), it retrieves the dashboard answers and actions.
 *
 * <p>Requires:
 *
 * <ul>
 *   <li>Pipeline processing enabled: {@code legali.default-metadata.legali.pipeline.disabled=false}
 *   <li>Dashboard ID configured: {@code legali.dashboard-id=<uuid>}
 * </ul>
 */
@ApplicationScoped
public class ExampleDashboardProcessingService {

  private static final Logger log =
      LoggerFactory.getLogger(ExampleDashboardProcessingService.class);

  @Inject Config.Mapping config;

  @Inject DashboardService dashboardService;

  @Inject LegalCaseService legalCaseService;

  @ConsumeEvent(value = "LegalCaseReadyEvent")
  void onLegalCaseReady(LegalCaseReadyEvent event) {
    if (this.config.dashboardId().isEmpty()) {
      log.debug(
          "Dashboard processing skipped — legali.dashboard-id not configured (legalCaseId={})",
          event.legalCaseId());
      return;
    }

    UUID dashboardId = UUID.fromString(this.config.dashboardId().get());
    log.info("Retrieving dashboard {} for legal case {}", dashboardId, event.legalCaseId());

    // Fetch dashboard with answers
    AgentDashboardDTO dashboard = this.dashboardService.get(event.legalCaseId(), dashboardId);

    log.info("Dashboard has {} answers", dashboard.answers().size());
    log.info("Dashboard can be accessed at {}", dashboard.url());

    // Print question-answer pairs — answers are polymorphic, check the type for richer data
    StringBuilder questionAnswerPairs = new StringBuilder();
    dashboard
        .answers()
        .forEach(
            answer -> {
              questionAnswerPairs.append(answer.title()).append(System.lineSeparator());
              if (answer instanceof AgentDashboardTrafficLightAnswerDTO trafficLight) {
                questionAnswerPairs
                    .append(trafficLight.answer())
                    .append(" [")
                    .append(trafficLight.trafficLight())
                    .append("]");
              } else if (answer instanceof AgentDashboardListAnswerDTO listAnswer) {
                // List answers include typed column headers and structured items
                questionAnswerPairs.append("(").append(listAnswer.items().size()).append(" items)");
                for (AgentDashboardListHeaderDTO header : listAnswer.headers()) {
                  questionAnswerPairs
                      .append(System.lineSeparator())
                      .append("  header: ")
                      .append(header.key())
                      .append(" (")
                      .append(header.type())
                      .append(") label=")
                      .append(header.label());
                }
                listAnswer
                    .items()
                    .forEach(
                        item -> questionAnswerPairs.append(System.lineSeparator()).append(item));
              } else if (answer instanceof AgentDashboardJsonAnswerDTO jsonAnswer) {
                // JSON answers contain structured data matching the item's output schema
                questionAnswerPairs
                    .append("Schema: v")
                    .append(jsonAnswer.schemaVersion())
                    .append(System.lineSeparator());
                jsonAnswer
                    .data()
                    .forEach(
                        (key, value) ->
                            questionAnswerPairs
                                .append("  ")
                                .append(key)
                                .append(": ")
                                .append(value)
                                .append(System.lineSeparator()));
              } else {
                questionAnswerPairs.append(answer.answer());
              }
              questionAnswerPairs.append(System.lineSeparator()).append(System.lineSeparator());
            });
    log.info("Question-Answer Pairs:\n{}", questionAnswerPairs);

    // Handle actions: dispatch each action to the corresponding operation in your core system.
    // Action names and parameters are custom and defined together with amaise during the
    // dashboard configuration process.
    dashboard
        .actions()
        .forEach(
            action -> {
              switch (action.name()) {
                case "send_confirmation_email" ->
                    log.info("[send_confirmation_email] params={}", action.params());
                case "send_rejection_email" ->
                    log.info("[send_rejection_email] params={}", action.params());
                case "notify_adjustor" -> log.info("[notify_adjustor] params={}", action.params());
                default -> log.warn("Unknown action: {}", action.name());
              }
            });

    /* IMPORTANT: Manage data lifecycle
     * The data lifecycle is fully managed by you, delete the legal case according to
     * your internal policies.
     *
     * Example: automated workspaces, legal cases may be deleted if they do not require
     * manual intervention.
     * if (dashboard.answers().get(0) instanceof AgentDashboardTrafficLightAnswerDTO tl
     *     && tl.trafficLight() == AgentDashboardTrafficLightAnswerDTO.TrafficLight.GREEN) {
     *   this.legalCaseService.delete(event.legalCaseId());
     * }
     */
  }
}
