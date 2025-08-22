package ch.legali.sdk.example;

import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.models.AgentLegalCaseDTO;
import ch.legali.sdk.models.AgentSourceFileDTO;
import ch.legali.sdk.services.FileService;
import ch.legali.sdk.services.LegalCaseService;
import ch.legali.sdk.services.SourceFileService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExampleBulkProcessingThread implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ExampleBulkProcessingThread.class);

  private final LegalCaseService legalCaseService;
  private final SourceFileService sourceFileService;
  private final FileService fileService;
  private final ExampleConfig exampleConfig;

  public ExampleBulkProcessingThread(
      LegalCaseService legalCaseService,
      SourceFileService sourceFileService,
      FileService fileService,
      ExampleConfig exampleConfig) {
    this.legalCaseService = legalCaseService;
    this.sourceFileService = sourceFileService;
    this.fileService = fileService;
    this.exampleConfig = exampleConfig;
  }

  /*
    INSTRUCTIONS:

    1. Setup agent credentials and legali.example.tenants.department-1 (destination tenant) in the application.properties
    2. Place your PDF files in a local folder and make sure the sourcePath points to it.
    3. Provide a valid user ID as owner of the AgentLegalCaseDTO and adapt any other default values of the DTOs as needed.
    4. In the ExampleThread, in the run() method, comment out the main while-loop and uncomment this.exampleBulkProcessingThread.run() below.
    5. Run the example and wait for all files to be processed.
  */

  @Override
  public void run() {
    log.info("🚀  Starting ExampleBulkProcessingThread");

    // Configure source path
    final Path sourcePath = Path.of("/path/to/local/pdf/files");

    try {
      log.info("🔍 Searching for PDF files in directory: {}", sourcePath);
      List<Path> filePaths =
          Files.walk(sourcePath)
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
              .toList();

      // Fetching already uploaded files to avoid duplicates
      Set<String> alreadyUploadedFiles =
          this.legalCaseService.list().stream()
              .map(AgentLegalCaseDTO::reference)
              .collect(Collectors.toSet());
      log.info("Found {} already uploaded files", alreadyUploadedFiles.size());

      log.info("Found {} PDF files to process", filePaths.size());
      filePaths.forEach(
          filePath -> {
            // NOTE: we use the filename as unique reference
            String filename = this.getFilename(filePath);
            if (alreadyUploadedFiles.contains(filename)) {
              log.info("File {} already processed, skipping", filePath);
              return;
            }
            this.processFile(filePath);
          });
    } catch (IOException e) {
      log.error("Error processing files in directory: {}", sourcePath, e);
    }

    log.info("✅ Finished processing files in directory: {}", sourcePath);
  }

  private void processFile(Path filePath) {
    log.info("⚙\uFE0F Processing file: {}", filePath);

    // Create LegalCase
    String filename = this.getFilename(filePath);

    AgentLegalCaseDTO legalCase =
        AgentLegalCaseDTO.builder()
            .tenantId(this.exampleConfig.getTenants().get("department-1"))
            .legalCaseId(UUID.randomUUID())
            // Use the filename as reference
            .reference(filename)
            // Pass the UserID from SSO
            .owner("DummyIamUser")
            .caseData(
                Map.ofEntries(
                    Map.entry("PII_FIRSTNAME", "Maria"), Map.entry("PII_LASTNAME", "Bernasconi")))
            // Optional: include the reference in the metadata
            .putMetadata("reference", filename)
            .build();

    this.legalCaseService.create(legalCase, this.exampleConfig.getTenants().get("department-1"));

    // Add SourceFile and upload the file
    AgentSourceFileDTO sourceFile =
        AgentSourceFileDTO.builder()
            .sourceFileId(UUID.randomUUID())
            .legalCaseId(legalCase.legalCaseId())
            .folder("unknown")
            // Use filename as reference
            .fileReference(filename)
            .build();

    try (InputStream is = Files.newInputStream(filePath)) {
      this.sourceFileService.create(sourceFile, is);
    } catch (Exception e) {
      log.error("🙅 Failed to create SourceFile with file " + filePath, e);
      this.legalCaseService.delete(legalCase.legalCaseId());
    }

    log.info("✅ Successfully processed file: {}", filePath);

    // Sleep 5s
    try {
      TimeUnit.SECONDS.sleep(5);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private String getFilename(Path filePath) {
    return Objects.requireNonNull(filePath.getFileName()).toString().replace(".pdf", "");
  }
}
