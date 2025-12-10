package ch.legali.sdk.example;

import ch.legali.api.events.SourceFileReadyEvent;
import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.models.AgentDiagnosisDTO;
import ch.legali.sdk.models.AgentDocumentDTO;
import ch.legali.sdk.models.AgentDocumentMetadataDTO;
import ch.legali.sdk.models.AgentLegalCaseDTO;
import ch.legali.sdk.models.AgentProcessedSourceFileDTO;
import ch.legali.sdk.models.AgentSourceFileDTO;
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
public class ExampleSourceFileProcessingThread implements Runnable {
  private static final Logger log =
      LoggerFactory.getLogger(ExampleSourceFileProcessingThread.class);

  private final LegalCaseService legalCaseService;
  private final SourceFileService sourceFileService;
  private final EventService eventService;
  private final ExampleConfig exampleConfig;

  public ExampleSourceFileProcessingThread(
      LegalCaseService legalCaseService,
      SourceFileService sourceFileService,
      EventService eventService,
      ExampleConfig exampleConfig) {
    this.legalCaseService = legalCaseService;
    this.sourceFileService = sourceFileService;
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
    this.eventService.subscribe(SourceFileReadyEvent.class);
  }

  @EventListener
  public void handle(SourceFileReadyEvent event) {
    log.info("SourceFileReadyEvent: \n{}", event.sourceFileId());
    this.eventService.acknowledge(event);

    log.info("📄 Retrieving processed source file: {}", event.sourceFileId());
    AgentProcessedSourceFileDTO processedSourceFile =
        this.sourceFileService.getProcessed(event.sourceFileId());
    log.info(
        "Processed Source File '{}' has {} documents.",
        processedSourceFile.originalFilename(),
        processedSourceFile.documents().size());

    // Do something with the processed source file, e.g. print results
    log.info("Printing first document details:");
    for (AgentDocumentDTO documentDTO : processedSourceFile.documents()) {
      log.info("Document from page {} to {}", documentDTO.startPage(), documentDTO.endPage());
      // metadata
      for (Map.Entry<AgentDocumentMetadataDTO.Key, AgentDocumentMetadataDTO> entry :
          documentDTO.metadata().entrySet()) {
        log.info("  Metadata - {}: {}", entry.getKey().name(), entry.getValue().value());
      }
      // diagnoses
      for (AgentDiagnosisDTO diagnosisDTO : documentDTO.diagnoses()) {
        log.info(
            "  Diagnosis - ICD10 Code: {}, Tags: {}", diagnosisDTO.code(), diagnosisDTO.tags());
      }
      // work inabilities
      for (ch.legali.sdk.models.AgentWorkInabilityDTO workInabilityDTO :
          documentDTO.workInabilities()) {
        log.info(
            "  Work Inability - From: {}, To: {}, Percentage: {}%",
            workInabilityDTO.fromDate(), workInabilityDTO.toDate(), workInabilityDTO.percentage());
      }

      // Stop after one document to keep the example simple and limit the log output
      break; // NOPMD - intentionally stop after one document for demo/log simplicity
    }

    this.sourceFileService.delete(processedSourceFile.sourceFileId());
    log.info("🗑  Deleted processed source file and its source file: {}", event.sourceFileId());
  }

  @Override
  public void run() {
    log.info("🚀 Starting ExampleSourceFileProcessingThread");

    log.info("🗂  Adding LegalCase");
    AgentLegalCaseDTO legalCase =
        AgentLegalCaseDTO.builder()
            .legalCaseId(UUID.randomUUID())
            .caseData(
                Map.ofEntries(
                    Map.entry("PII_FIRSTNAME", "Maria"),
                    Map.entry("PII_LASTNAME", "Bernasconi"),
                    // Special use case for Switzerland: SUNET XML data can be stored directly in
                    // the key 'ADDITIONAL_SUNETXML' (it does not replace mapping other case data).
                    Map.entry(
                        "ADDITIONAL_SUNETXML",
                        "<?xml version=\"1.0\""
                            + " encoding=\"UTF-8\"?><claimReport>...</claimReport>")))
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
            .build();

    log.info("All done, waiting for SourceFileReadyEvent...");

    ClassPathResource cp = new ClassPathResource("sample.pdf");
    try (InputStream is = cp.getInputStream()) {
      this.sourceFileService.create(sourceFile, is);
    } catch (IOException e) {
      log.error("🙅‍  Failed to create SourceFile", e);
    }
  }
}
