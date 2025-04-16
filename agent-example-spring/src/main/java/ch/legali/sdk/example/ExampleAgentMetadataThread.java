package ch.legali.sdk.example;

import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.models.AgentLegalCaseDTO;
import ch.legali.sdk.models.AgentSourceFileDTO;
import ch.legali.sdk.models.AgentSourceFileDTO.MetadataKeys;
import ch.legali.sdk.models.AgentSourceFileDTO.SourceFileStatus;
import ch.legali.sdk.services.LegalCaseService;
import ch.legali.sdk.services.SourceFileService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Note that the connector API is thread-safe. */
@Component
public class ExampleAgentMetadataThread implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ExampleThread.class);

  private final LegalCaseService legalCaseService;
  private final SourceFileService sourceFileService;
  private final ExampleConfig exampleConfig;

  public ExampleAgentMetadataThread(
      LegalCaseService legalCaseService,
      SourceFileService sourceFileService,
      ExampleConfig exampleConfig) {
    this.legalCaseService = legalCaseService;
    this.sourceFileService = sourceFileService;
    this.exampleConfig = exampleConfig;
  }

  /**
   * This example is specifically designed to create a series of legal cases, with arbitrary
   * firstname, lastname, filename (from the resources) and mapping key, and then let the
   * sourcefiles process and verify the results (e.g. the mappings) Make sure the mappings actually
   * exist in the database, otherwise the metadata will not be set accordingly. As always, see the
   * documentation for more details. BEFORE RUNNING: Make sure pipeline processing is enabled in the
   * application.properties, otherwise sourcefiles will not be processed.
   */
  @Override
  public void run() {
    // Samples, adapt mapping key accordingly
    // NOTE: sample.pdf and sample2.pdf are single-page PDF documents with a short dummy text
    // sample3.pdf is a single-page PDF document with a full dummy text that emulates a medical
    // report, if no other mapping for doctype is found, it will be classified as
    // type_medical_report
    List<List<String>> config =
        List.of(
            // firstname, lastname, filename, mapping key
            // NOTE: use firstname and lastname to describe what to expect from that legalcase, so
            // it's easier to debug later when verifying the results
            List.of("Basic", "Test", "sample3.pdf", "KEY1"),
            List.of("Expect", "type_medical", "sample.pdf", "KEY2"),
            List.of("Expect", "type_medical_report", "sample3.pdf", "KEY2"));

    // Run each use case
    for (List<String> row : config) {
      String legalCaseFirstname = row.get(0);
      String legalCaseLastname = row.get(1);
      String filename = row.get(2);
      String mappingKey = row.get(3);
      runExample(legalCaseFirstname, legalCaseLastname, filename, mappingKey);
    }
  }

  private void runExample(
      String legalCaseFirstname, String legalCaseLastname, String filename, String mappingKey) {
    // Create
    log.info("🗂  Adding LegalCase");
    AgentLegalCaseDTO legalCase =
        AgentLegalCaseDTO.builder()
            .legalCaseId(UUID.randomUUID())
            .firstname(legalCaseFirstname)
            .lastname(legalCaseLastname)
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
     * 500MB), in multi-threaded mode this leads to unwanted spikes in memory usage.
     * Ideally the files are chunked downloaded to a temporary file and then passed to
     * the SDK.
     */

    // add / delete a sourcefile
    // We set the metadata properties here, including a mapping key.
    // The mapping key is used to look up the agent mappings stored in the db, if one is provided
    // and a matching key is found, the metadata is set accordingly.
    // The metadata defined by the mapping has always the highest priority, i.e. it will overwrite
    // any metadata set by the agent via the individual properties.
    AgentSourceFileDTO sourceFile =
        AgentSourceFileDTO.builder()
            .sourceFileId(UUID.randomUUID())
            .legalCaseId(legalCase.legalCaseId())
            .folder("iv-zh")
            .fileReference("hello.pdf")

            // To pass metadata properties, you can use strings...
            .putMetadata("legali.metadata.title", "Sample Document")
            .putMetadata("legali.metadata.doctype", "type_admin")
            .putMetadata("legali.metadata.issuedate", "2012-12-12")

            // or using the enums keys
            .putMetadata(MetadataKeys.LEGALI_METADATA_RECEIPTDATE.key(), "2012-12-11")

            // for boolean value, pass "true" or "false" as strings
            .putMetadata(MetadataKeys.LEGALI_PIPELINE_SPLITTING_DISABLED.key(), "true")

            // pass a mapping key instead, this will look up the agent mappings stored in the db
            // if a matching key is found, the metadata is set accordingly
            .putMetadata("legali.mapping.key", mappingKey)

            // if a property is set to an empty string, it is ignored and the default is used
            .putMetadata("legali.metadata.some-property", "")
            .build();

    log.info("🧾  Creating SourceFile");
    ClassPathResource cp = new ClassPathResource(filename);
    try (InputStream is = cp.getInputStream()) {
      this.sourceFileService.create(sourceFile, is);
    } catch (IOException e) {
      log.error("🙅‍  Failed to create SourceFile", e);
    }

    log.info("😴  Waiting for SourceFile to be processed  (will timeout after 3 seconds!)");
    // NOTE: use with care, busy waiting and usually not required
    SourceFileStatus status =
        this.sourceFileService.waitForSourceFileReadyOrTimeout(
            sourceFile.sourceFileId(), TimeUnit.SECONDS.toSeconds(3));

    // NOTE: will always time out, if processing is disabled
    if (status.equals(SourceFileStatus.ERROR) || status.equals(SourceFileStatus.TIMEOUT)) {
      log.warn("💥 amaise was not fast enough to process this file {}", sourceFile.sourceFileId());
    }
  }

  public void cleanup() {
    List<AgentLegalCaseDTO> allCases = this.legalCaseService.list();
    for (AgentLegalCaseDTO currentLegalCase : allCases) {
      if ("example-agent".equals(currentLegalCase.metadata().getOrDefault("legali.uploader", ""))) {
        log.info("🧹 Cleaning up {}", currentLegalCase.legalCaseId());
        this.legalCaseService.delete(currentLegalCase.legalCaseId());
      }
    }
  }
}
