package ch.legali.sdk.example;

import ch.legali.sdk.example.config.ExampleConfig;
import ch.legali.sdk.exceptions.FileConflictException;
import ch.legali.sdk.exceptions.NotFoundException;
import ch.legali.sdk.models.AgentExportDTO;
import ch.legali.sdk.models.AgentFileDTO;
import ch.legali.sdk.models.AgentLegalCaseDTO;
import ch.legali.sdk.models.AgentNotebookDTO;
import ch.legali.sdk.models.AgentSourceFileAnnotationsDTO;
import ch.legali.sdk.models.AgentSourceFileDTO;
import ch.legali.sdk.models.AgentSourceFileDTO.MetadataKeys;
import ch.legali.sdk.models.AgentSourceFileDTO.SourceFileStatus;
import ch.legali.sdk.services.ExportService;
import ch.legali.sdk.services.FileService;
import ch.legali.sdk.services.LegalCaseService;
import ch.legali.sdk.services.SourceFileService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Note that the connector API is thread-safe. */
@Component
@SuppressWarnings("deprecation")
public class ExampleThread implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ExampleThread.class);

  private final LegalCaseService legalCaseService;
  private final SourceFileService sourceFileService;
  private final ExportService exportService;
  private final FileService fileService;
  private final ExampleConfig exampleConfig;

  private Optional<Runnable> optionalExampleSpecialThread;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public ExampleThread(
      LegalCaseService legalCaseService,
      SourceFileService sourceFileService,
      ExportService exportService,
      FileService fileService,
      /* Special example threads available:
       - ExampleAgentMetadataThread: demonstrates how to create legal cases and sourcefiles with
           specific metadata, then lets the sourcefiles process and verifies the results.
       - ExampleBulkProcessingThread: demonstrates how to bulk upload files from a local folder
           into legal cases.
       - ExampleSourceFileProcessingThread: demonstrates how to listen to sourcefile processing
           events and retrieve processed sourcefiles.
       - ExampleDashboardProcessingThread: demonstrates how to listen to legalcase processing
           events and retrieve dashboards.

       IMPORTANT: Processing threads that use EventListeners require to be enabled in
       the application.properties file, e.g. legali.example.enable-sourcefile-processing-thread=false
      */
      @Autowired(required = false) @Qualifier("Dummy")
          Optional<Runnable> optionalExampleSpecialThread,
      ExampleConfig exampleConfig) {
    this.legalCaseService = legalCaseService;
    this.sourceFileService = sourceFileService;
    this.exportService = exportService;
    this.fileService = fileService;
    this.exampleConfig = exampleConfig;

    this.optionalExampleSpecialThread = optionalExampleSpecialThread;
  }

  @Override
  public void run() {
    int i = 0;
    while (i++ < this.exampleConfig.getIterations()) {
      log.info("🚀  Starting run {}", i);
      this.runExample();
      this.runExampleTwoDepartments();
      log.info("ExampleAgent run successful");
    }

    // Run special thread
    if (this.optionalExampleSpecialThread.isPresent()) {
      log.info(
          "Running special example thread: {}",
          this.optionalExampleSpecialThread.get().getClass().getSimpleName());
      this.optionalExampleSpecialThread.get().run();
    }
  }

  /**
   * This is the dummy logic of the connector. You can download and check JavaDoc of each method and
   * entity of the SDK.
   */
  private void runExample() {
    // Create
    log.info("🗂  Creating LegalCase");
    AgentLegalCaseDTO legalCase =
        AgentLegalCaseDTO.builder()
            .legalCaseId(UUID.randomUUID())
            .caseData(
                // IMPORTANT: every case must have a PII_FIRSTNAME and PII_LASTNAME, or PII_COMPANY
                // set.
                // Generally speaking, if the case belongs to a person, PII_FIRSTNAME and
                // PII_LASTNAME should be set. If the case belongs to a company or any other case,
                // PII_COMPANY should be set.
                Map.ofEntries(Map.entry("PII_FIRSTNAME", "John"), Map.entry("PII_LASTNAME", "Doe")))
            .reference("123-456-789")
            // Pass the UserID from SSO
            .owner("DummyIamUser")
            // or pass the user's e-mail
            // .ownerEmail("dummy@user.com")
            .accessGroup("group1")
            .putMetadata("meta.dummy", "dummy value")
            .build();
    this.legalCaseService.create(legalCase, this.exampleConfig.getTenants().get("department-2"));

    // update legal case
    log.info("🤓  Updating LegalCase");
    AgentLegalCaseDTO legalCaseResponse = this.legalCaseService.get(legalCase.legalCaseId());
    AgentLegalCaseDTO nameChanged =
        AgentLegalCaseDTO.builder()
            .from(legalCaseResponse)
            .caseData(Map.of("PII_FIRSTNAME", "Jane"))
            .reference("John changed his name")
            .build();
    this.legalCaseService.update(nameChanged);

    /*
     * To keep a constant memory footprint on the agent, the SDK uses a FileObject and
     * not a ByteArrayResource. PDF files can be large if they contain images (>
     * 500MB), in multithreaded mode this leads to unwanted spikes in memory usage.
     * Ideally the files are chunked downloaded to a temporary file and then passed to
     * the SDK.
     */
    Path fileToUpload = this.chooseLocalFile();

    // add / delete a sourcefile
    AgentSourceFileDTO sourceFile =
        AgentSourceFileDTO.builder()
            .sourceFileId(UUID.randomUUID())
            .legalCaseId(legalCase.legalCaseId())
            .folder(chooseFolder())
            .fileReference(UUID.randomUUID().toString())

            // To pass metadata properties, you can use strings...
            .putMetadata("legali.metadata.title", "Sample Document")
            .putMetadata("legali.metadata.alttitle", "Alternative Title")
            .putMetadata("legali.metadata.doctype", this.chooseDocType())
            .putMetadata("legali.metadata.issuedate", "2012-12-12")
            .putMetadata("legali.metadata.pagination.number", "123")
            .putMetadata("legali.metadata.pagination.id", "Wf0ZoNA5")

            // or using the enums keys
            .putMetadata(MetadataKeys.LEGALI_METADATA_RECEIPTDATE.key(), "2012-12-11")

            // for boolean value, pass "true" or "false" as strings
            .putMetadata(MetadataKeys.LEGALI_PIPELINE_SPLITTING_DISABLED.key(), "true")

            // pass a mapping key instead, this will look up the agent mappings stored in the db
            // if a matching key is found, the metadata is set accordingly
            .putMetadata("legali.mapping.key", "M1")

            // if a property is set to an empty string, it is ignored and the default is used
            .putMetadata("legali.metadata.some-property", "")

            // Annotations in XFDF format
            .annotationsXfdf(this.getExampleXfdf())
            // Pass the structured data for this document stored in your system (e.g.
            // Sunet XML for Switzerland)
            .structuredData(
                "<?xml version=\"1.0 encoding=\"UTF-8\"?><claimReport>...</claimReport>")
            .build();

    log.info("🧾  Creating SourceFile");
    try (InputStream is = Files.newInputStream(fileToUpload)) {
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

    // Try to create same sourcefile with another file
    try {
      ClassPathResource cp = new ClassPathResource("sample2.pdf");
      try (InputStream file2 = cp.getInputStream()) {
        this.sourceFileService.create(sourceFile, file2);
      } catch (IOException e) {
        log.error("🙅‍  Failed to open sample2.pdf file", e);
      }
    } catch (FileConflictException fileConflictException) {
      log.info("🙅‍  Sourcefile files are different, refused due to conflict!‍️");
    }
    log.info("🧾  Creating the same SourceFile AGAIN (creates are idempotent)");
    try (InputStream is = Files.newInputStream(fileToUpload)) {
      this.sourceFileService.create(sourceFile, is);
    } catch (IOException e) {
      log.error("🙅‍  Failed to create SourceFile", e);
    }

    // Try to create same sourcefile again with another file, this time with a different id but with
    // the same file reference
    log.info(
        "🧾  Creating the same SourceFile AGAIN using a different UUID but same fileReference"
            + " (creates are idempotent)");
    try (InputStream is = Files.newInputStream(fileToUpload)) {
      AgentSourceFileDTO sourceFile2 =
          AgentSourceFileDTO.builder().from(sourceFile).sourceFileId(UUID.randomUUID()).build();
      this.sourceFileService.create(sourceFile2, is);
    } catch (IOException e) {
      log.error("🙅‍  Failed to create SourceFile", e);
    }

    List<AgentSourceFileDTO> list = this.sourceFileService.getByLegalCase(legalCase.legalCaseId());
    log.info("1️⃣ LegalCase has {} source files", list.size());

    // download file again and verify md5
    AgentFileDTO downloadedFile = list.getFirst().originalFile();
    try (InputStream is = this.fileService.downloadFile(downloadedFile.uri())) {
      Path target = Path.of("./" + downloadedFile.filename());
      Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);

      MessageDigest md = MessageDigest.getInstance("MD5");
      String md5 = Base64.getEncoder().encodeToString(md.digest(Files.readAllBytes(target)));
      if (!md5.equals(downloadedFile.md5())) {
        throw new RuntimeException("MD5 of downloaded file is not correct");
      }
      log.info("🧮 MD5 of downloaded file is {}: correct", downloadedFile.md5());

      Files.delete(target);
    } catch (NoSuchAlgorithmException | IOException e) {
      log.error(e.getMessage());
    }

    // replace sourcefile within the same legalcase, it will fail since the sourcefile does not
    // exist
    log.info("\uD83D\uDD28  Replace SourceFile in LegalCase");
    AgentSourceFileDTO sourceFileToReplace =
        AgentSourceFileDTO.builder()
            .from(sourceFile)
            // NOTE: you must use the same UUID of the sourcefile you want to replace!
            // this is just to make it fail for the sake of the example
            .sourceFileId(UUID.randomUUID())
            .folder("accident")
            .putMetadata("legali.mapping.key", "M2")
            .build();
    try {
      try (InputStream is = Files.newInputStream(fileToUpload)) {
        this.sourceFileService.replace(sourceFileToReplace, is);
      } catch (IOException e) {
        log.error("🙅‍  Failed to replace SourceFile", e);
      }
    } catch (NotFoundException e) {
      log.info("🙅‍  SourceFile {} does not exist", sourceFileToReplace.sourceFileId());
    }

    // move sourcefile to another legalcase, it will fail since the legalcase does not exist
    log.info("🚚  Moving SourceFile to another LegalCase");
    AgentSourceFileDTO sourceFileToMove =
        AgentSourceFileDTO.builder().from(sourceFile).legalCaseId(UUID.randomUUID()).build();
    try {
      try (InputStream is = Files.newInputStream(fileToUpload)) {
        this.sourceFileService.move(sourceFileToMove, is);
      } catch (IOException e) {
        log.error("🙅‍  Failed to move SourceFile", e);
      }
    } catch (NotFoundException e) {
      log.info("🙅‍  LegalCase {} does not exist", sourceFileToMove.legalCaseId());
    }

    List<AgentExportDTO> exportsList = this.exportService.list(legalCase.legalCaseId());
    log.info("1️⃣  LegalCase has {} exports", exportsList.size());

    UUID exportId = UUID.randomUUID();
    try {
      AgentExportDTO export = this.exportService.get(exportId);
      log.info("1️⃣  LegalCase has export with uuid {}", export.exportId());
    } catch (NotFoundException e) {
      log.info("1️⃣  LegalCase does not have export with uuid {}", exportId);
    }

    List<AgentNotebookDTO> notebooks = this.legalCaseService.listNotebooks(legalCase.legalCaseId());
    log.info("1️⃣  LegalCase has {} notebooks", notebooks.size());

    AgentSourceFileAnnotationsDTO annotations =
        this.sourceFileService.getAnnotations(sourceFile.sourceFileId());
    log.info(
        "1️⃣  LegalCase has annotations XFDF of length {}: {}",
        annotations.xfdf().length(),
        annotations.xfdf());

    // UPDATE METADATA
    log.info("🔄 Updating sourcefile metadata");

    AgentSourceFileDTO metadataUpdateSourceFile =
        AgentSourceFileDTO.builder()
            .sourceFileId(sourceFile.sourceFileId())
            .legalCaseId(sourceFile.legalCaseId())
            .folder(chooseFolder())
            .fileReference(UUID.randomUUID().toString())

            // To pass metadata properties, you can use strings...
            .putMetadata("legali.metadata.alttitle", "New Alt Title")
            // .putMetadata("legali.metadata.pagination.number", "0124")
            // .putMetadata("legali.metadata.pagination.id", "af0enXL1")
            .build();

    this.sourceFileService.updateMetadata(metadataUpdateSourceFile);
    log.info("⬆️ SourceFile metadata updated");

    log.info("␡  Deleting SourceFile");
    this.sourceFileService.delete(sourceFile.sourceFileId());

    log.info("🗄  Archiving LegalCase");
    this.legalCaseService.archive(legalCaseResponse.legalCaseId());

    list = this.sourceFileService.getByLegalCase(legalCase.legalCaseId());
    log.info("😅  LegalCase has {} source files", list.size());

    log.info("🗑  Deleting LegalCase");
    this.legalCaseService.delete(legalCaseResponse.legalCaseId());

    try {
      this.legalCaseService.get(legalCase.legalCaseId());
    } catch (NotFoundException ignored) {
      log.info(
          "🥳  LegalCase is not in any authorized tenants! It has successfully been deleted, well"
              + " done!");
    }
  }

  private void runExampleTwoDepartments() {
    log.info("🗂  Creating LegalCase in Department 1");
    AgentLegalCaseDTO legalCaseDept1 =
        AgentLegalCaseDTO.builder()
            .legalCaseId(UUID.randomUUID())
            .caseData(
                Map.ofEntries(Map.entry("PII_FIRSTNAME", "John"), Map.entry("PII_LASTNAME", "Doe")))
            .reference("123-456-789")
            // Pass the UserID from SSO
            .owner("DummyIamUser")
            // or pass the user's e-mail
            // .ownerEmail("dummy@user.com")
            .accessGroup("group1")
            .putMetadata("meta.dummy", "dummy value")
            .build();
    this.legalCaseService.create(
        legalCaseDept1, this.exampleConfig.getTenants().get("department-1"));

    log.info("🗂  Creating LegalCase in Department 2");
    AgentLegalCaseDTO legalCaseDept2 =
        AgentLegalCaseDTO.builder().from(legalCaseDept1).legalCaseId(UUID.randomUUID()).build();
    this.legalCaseService.create(
        legalCaseDept2, this.exampleConfig.getTenants().get("department-2"));

    this.legalCaseService
        .list()
        .forEach(
            lc -> log.info("🗂  LegalCase {} is in tenant {}", lc.legalCaseId(), lc.tenantId()));

    // add files
    Path fileToUpload = this.chooseLocalFile();
    AgentSourceFileDTO sourceFileDept1 =
        AgentSourceFileDTO.builder()
            .sourceFileId(UUID.randomUUID())
            .legalCaseId(legalCaseDept1.legalCaseId())
            .folder(chooseFolder())
            .fileReference(UUID.randomUUID().toString())
            .build();
    try (InputStream is = Files.newInputStream(fileToUpload)) {
      this.sourceFileService.create(sourceFileDept1, is);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    AgentSourceFileDTO sourceFileDept2 = AgentSourceFileDTO.builder().from(sourceFileDept1).build();
    try (InputStream is = Files.newInputStream(fileToUpload)) {
      this.sourceFileService.create(sourceFileDept2, is);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // try to access both
    String sourceFileDept1Uri =
        this.sourceFileService.get(sourceFileDept1.sourceFileId()).originalFile().uri();
    String sourceFileDept2Uri =
        this.sourceFileService.get(sourceFileDept2.sourceFileId()).originalFile().uri();
    try (InputStream is = this.fileService.downloadFile(sourceFileDept1Uri)) {
      log.info("📁 Dept1: File with length: {}", is.readAllBytes().length);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try (InputStream is = this.fileService.downloadFile(sourceFileDept2Uri)) {
      log.info("📁 Dept2: File 2 length: {}", is.readAllBytes().length);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // DELETE
    log.info("🗑  Deleting LegalCase - Department 1");
    this.legalCaseService.delete(legalCaseDept1.legalCaseId());

    log.info("🗑  Deleting LegalCase - Department 2");
    this.legalCaseService.delete(legalCaseDept2.legalCaseId());
    log.info("🥳 LegalCases for Department 1 and Department 2 have been created and deleted!");
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

  /**
   * Returns either a random file from the given directory or the sample.pdf
   *
   * @return File
   */
  private Path chooseLocalFile() {
    // NOTE: if a directory has been specified, the connector loads a random file form
    // there
    try {
      if (this.exampleConfig.getFilesPath() != null
          && !this.exampleConfig.getFilesPath().isBlank()) {
        List<Path> files = Files.list(Paths.get(this.exampleConfig.getFilesPath())).toList();
        int randomIndex = (int) Math.floor(Math.random() * files.size());
        Path f = files.get(randomIndex);
        log.info(
            "Chosen file {}, {} MB",
            f.getFileName(),
            Math.round((double) Files.size(f) / (1024 * 1024)));
        return f;
      }

      // fall back to sample, if no or invalid path specified
      log.debug("Using sample.pdf");
      ClassPathResource cp = new ClassPathResource("sample.pdf");
      return cp.getFile().toPath();
    } catch (IOException e) {
      log.error(e.getMessage());
      return null;
    }
  }

  /**
   * @return String random doc type
   */
  private String chooseDocType() {
    return List.of("type_medical", "type_profession_ikstatement", "type_legal_disposal")
        .get((int) Math.floor(Math.random() * 3));
  }

  /**
   * @return String random folder
   */
  private String chooseFolder() {
    return List.of("accident", "liability", "iv-be").get((int) Math.floor(Math.random() * 3));
  }

  /**
   * @return String an example xfdf file
   */
  private String getExampleXfdf() {
    return """
<?xml version="1.0" encoding="UTF-8"?>
<xfdf xmlns="http://ns.adobe.com/xfdf/">
    <annots>
        <highlight page="0" rect="75.071,516.351,289.625,531.103"\
 color="#FF9800"
            name="1234567" title="test title" subject="hello world"
            date="D:20230706160122+02'00'" opacity="0.5"\
 creationdate="D:20230706160122+02'00'"
           \
 coords="75.32109928446837,531.1031977120231,289.62462070530665,524.6739848693966,75.07139920622396,522.7800005529471,289.37492062706224,516.3507877103207">
            <contents>Hello World</contents>
        </highlight>
    </annots>
</xfdf>""";
  }
}
