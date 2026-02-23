/**
 * Example: SourceFile Processing
 *
 * Single SourceFile processing lifecycle - upload a PDF, wait for the
 * pipeline to finish, and retrieve the extracted data:
 * 1. Start event system (register handlers before triggering pipeline)
 * 2. Create a LegalCase (required container for every SourceFile)
 * 3. Upload a PDF via presigned URL
 * 4. Create a SourceFile to trigger the processing pipeline
 * 5. Receive SourceFileReadyEvent and retrieve extracted data
 * 6. Clean up (delete SourceFile and LegalCase)
 *
 * Documents the various entity fields available on LegalCase and SourceFile
 * to help integrators understand the full API surface.
 */

import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import type {
  LegalCaseReadyEvent,
  SourceFileReadyEvent,
  SourceFileFailedEvent,
} from '../generated/types.gen';
import { LegalCaseService, SourceFileService } from '../generated/sdk.gen';
import { apiClient } from '../lib/api-client';
import { EventSystem } from '../lib/event-system';

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

apiClient.configure();

const SAMPLE_PDF = resolve(import.meta.dirname, '../assets/sample.pdf');
const legalCaseId = randomUUID();
const sourceFileId = randomUUID();

// ---------------------------------------------------------------------------
// Event handlers
// ---------------------------------------------------------------------------

/** Called when the pipeline finishes processing our SourceFile. */
async function onSourceFileReady(e: SourceFileReadyEvent): Promise<void> {
  if (e.sourceFileId !== sourceFileId) return;
  console.log('📄 SourceFile is ready!');

  // Retrieve extracted documents with metadata, diagnoses, and work inabilities.
  // Each SourceFile can contain multiple documents (multi-document PDFs are split automatically).
  const { data: processed } = await SourceFileService.getProcessedSourceFile({
    path: { sourceFileId },
  });
  console.log('\n--- Processed SourceFile ---');
  console.log(`Status: ${processed?.status}`);
  // Confidence: HIGH = confident, REVIEW = manual review recommended.
  console.log(`Confidence: ${processed?.confidence}`);
  for (const doc of processed?.documents ?? []) {
    console.log(`\nDocument: pages ${doc.startPage}-${doc.endPage}`);

    if (doc.metadata) {
      // Common keys: TITLE, ISSUE_DATE, DOCTYPE, AUTHOR, SUMMARY, FOLDER, etc.
      // Extractor: EXTRACTION (AI), INTEGRATION (agent), MANUAL (user), ANALYZER, EMPTY.
      const title = doc.metadata.TITLE?.value ?? '(untitled)';
      const issueDate = doc.metadata.ISSUE_DATE?.value ?? 'n/a';
      const docType = doc.metadata.DOCTYPE?.value ?? 'n/a';
      const author = doc.metadata.AUTHOR?.value ?? 'n/a';
      const folder = doc.metadata.FOLDER?.value ?? 'n/a';
      console.log(`  Title: ${title}`);
      console.log(`  Issue date: ${issueDate}`);
      console.log(`  Document type: ${docType}`);
      console.log(`  Author: ${author}`);
      console.log(`  Folder: ${folder}`);
    }

    for (const diag of doc.diagnoses ?? []) {
      console.log(`  Diagnosis: ${diag.coveredText} (ICD-10: ${diag.code})`);
    }

    for (const wi of doc.workInabilities ?? []) {
      console.log(`  Work inability: ${wi.fromDate} - ${wi.toDate} (${wi.percentage}%)`);
    }
  }
}

/** Called when all SourceFiles in the LegalCase have been processed. */
function onLegalCaseReady(e: LegalCaseReadyEvent): void {
  if (e.legalCaseId !== legalCaseId) return;
  console.log(`🗂  LegalCase is ready: ${e.legalCaseUrl ?? legalCaseId}`);
}

/** Called when the pipeline fails to process our SourceFile. */
function onSourceFileFailed(e: SourceFileFailedEvent): void {
  if (e.sourceFileId !== sourceFileId) return;
  console.error(`🙅  SourceFile processing failed (sourceFileId: ${e.sourceFileId})`);
}

// ---------------------------------------------------------------------------
// Application lifecycle
// ---------------------------------------------------------------------------

async function run(): Promise<void> {
  // Register event handlers BEFORE creating resources that trigger processing,
  // so no events are missed between resource creation and the first heartbeat.
  console.log('🚀 Starting event system...');
  const eventSystem = new EventSystem({
    SourceFileReadyEvent: onSourceFileReady,
    SourceFileFailedEvent: onSourceFileFailed,
    LegalCaseReadyEvent: onLegalCaseReady,
  });
  eventSystem.start();

  try {
    // Create LegalCase - container for all documents belonging to one claim.
    // Idempotent: if a LegalCase with the same ID exists, it is reopened (HTTP 200).
    console.log(`🗂  Creating LegalCase ${legalCaseId}...`);
    await LegalCaseService.createLegalCase({
      body: {
        legalCaseId,

        // -- caseData (required) -------------------------------------------
        // PII (Personally Identifiable Information), job details, and incident data about the claimant. Powers data
        // extraction and is displayed in the amaise UI.
        // REQUIRED: either (PII_FIRSTNAME + PII_LASTNAME) or PII_COMPANY.
        // All other keys are optional but improve extraction quality.
        // We recommend providing all PII_* fields; JOB_* and INCIDENT_* are
        // usually covered by structuredData (e.g. SUNET XML) on the SourceFile.
        caseData: {
          PII_FIRSTNAME: 'Jane',
          PII_LASTNAME: 'Doe',
          // Or use PII_COMPANY instead of name fields:
          // PII_COMPANY: 'Acme Corp',
          //
          // Additional optional categories:
          //   PII_*       - personal info (birthdate, address, AHV number, etc.)
          //   JOB_*       - employment details (employer, role, income, etc.)
          //   INCIDENT_*  - claim/incident details (date, ICD-10 code, etc.)
          //   CUSTOM_1-3  - free-form integration-specific fields
          // See AgentLegalCaseDTO in the Swagger docs for the full list.
        },

        // -- reference (required) ------------------------------------------
        // Internal case reference (e.g. claim number). Displayed in the amaise
        // UI, useful for cross-referencing with the source system.
        reference: `claim-${legalCaseId.slice(0, 8)}`,

        // -- metadata (optional) -------------------------------------------
        // Integration-specific key-value pairs. Stored as-is, returned in API
        // responses, but not displayed in the UI - purely for the integration.
        metadata: {
          'source.system.id': 'CRM-2024-001',
          'source.system.url': 'https://crm.example.com/cases/001',
        },

        // -- owner / ownerEmail (at least one required) --------------------
        // Assigns the case to a user. Use `owner` with a technical user ID
        // (from SSO/IdP), or `ownerEmail` for email-based lookup.
        // owner: 'auth0|user-id-from-sso',
        // ownerEmail: 'jane.handler@insurance.com',

        // -- accessGroup (optional) ----------------------------------------
        // Restricts which user group can see the case in the amaise UI.
        // accessGroup: 'department-claims',
      },
    });

    // Upload PDF via presigned URL (valid 5 min). See lib/api-client.ts.
    console.log('📎 Uploading PDF via presigned URL...');
    const tempFileUri = await apiClient.uploadFile(SAMPLE_PDF);
    console.log(`Uploaded to temp location: ${tempFileUri}`);

    // Create SourceFile - one PDF document within the LegalCase.
    // Pipeline runs automatically after creation.
    // Idempotent - see Swagger docs for conflict resolution logic.
    console.log(`🧾 Creating SourceFile ${sourceFileId}...`);
    await SourceFileService.createSourceFile({
      body: {
        sourceFileId,
        legalCaseId,
        tempFileUri,

        // -- fileReference (required) --------------------------------------
        // Internal document ID (e.g. DMS ID or filename). Must be unique within
        // the LegalCase. Used for deduplication (same ref + same binary = no-op).
        fileReference: 'medical-report-2024-06.pdf',

        // -- folder (recommended) ------------------------------------------
        // Document classification folder. Use 'unknown' to let amaise classify
        // automatically. See Swagger docs for supported values.
        folder: 'unknown',

        // -- metadata (optional) -------------------------------------------
        // Controls processing behavior and sets document properties.
        // See MetadataKeys enum in Swagger docs for the full list.
        metadata: {
          // Mapping key - maps to pre-configured document type rules (folder,
          // doctype, splitting, etc.). Configured by workspace admins.
          // 'legali.mapping.key': 'SUNET_MEDICAL_REPORT',
          //
          // Document display metadata:
          // 'legali.metadata.title': 'Medical Report',
          // 'legali.metadata.alttitle': 'Alt title from source system',
          // 'legali.metadata.issuedate': '2024-06-01',
          // 'legali.metadata.receiptdate': '2024-06-15',
          //
          // Export pagination (ASTV Art. 8):
          // 'legali.metadata.pagination.sequencenumber': '001',
          // 'legali.metadata.pagination.documentid': 'Z7KJ39A4',
          //
          // @deprecated (use legali.mapping.key instead):
          // 'legali.metadata.doctype': 'type_medical',
          // 'legali.pipeline.splitting.disabled': 'false',
          //
          // Debug only (not for production):
          // 'legali.pipeline.disabled': 'true',
        },

        // -- structuredData (optional) -------------------------------------
        // Structured data from your system relevant to this document (XML, JSON).
        // Common: SUNET XML. The extraction pipeline cross-references this with
        // the PDF content to enhance results.
        //
        // structuredData: '<?xml version="1.0" encoding="UTF-8"?><claimReport>...</claimReport>',
        // structuredData: JSON.stringify({ claimId: 'CLM-2024-001', injuryType: 'fracture' }),

        // -- annotationsXfdf (optional) ------------------------------------
        // Pre-existing annotations in Adobe XFDF format, imported into the PDF
        // viewer. Retrieve user-added annotations via GET /sourcefiles/{id}/annotations.
        // annotationsXfdf: '<?xml version="1.0" encoding="UTF-8"?><xfdf>...</xfdf>',
      },
    });

    // In a real application, you would keep the event system running indefinitely
    // (or until a specific condition is met). Here we sleep briefly so the event
    // handlers above have a chance to fire during this demo.
    // NOTE: Real processing typically takes 5–30 minutes depending on document size.
    console.log(
      '😴 Waiting 10 s for events (illustration only - processing takes longer in practice)...',
    );
    await new Promise((resolve) => setTimeout(resolve, 10_000));
  } finally {
    eventSystem.stop();
    // Clean up: soft-delete SourceFile, permanently delete LegalCase.
    // In production, define a lifecycle policy (replication → archiving → deletion).
    await cleanup();
  }
}

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

async function cleanup(): Promise<void> {
  console.log('\n␡  Deleting SourceFile');
  await SourceFileService.deleteSourceFile({ path: { sourceFileId } }).catch((e: unknown) =>
    console.warn('🙅  Failed to delete SourceFile:', e instanceof Error ? e.message : String(e)),
  );
  console.log('🗑  Deleting LegalCase');
  await LegalCaseService.deleteLegalCase({ path: { legalCaseId } }).catch((e: unknown) =>
    console.warn('🙅  Failed to delete LegalCase:', e instanceof Error ? e.message : String(e)),
  );
  console.log('🥳 Done!');
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

run().catch((err) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
