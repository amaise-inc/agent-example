/**
 * Example: LegalCase Lifecycle
 *
 * Full LegalCase lifecycle - CRUD operations plus event-driven processing:
 * 1. Start event system (register handlers before triggering pipeline)
 * 2. Create a LegalCase with caseData and metadata
 * 3. Retrieve and update the LegalCase
 * 4. Upload a PDF via presigned URL
 * 5. Create a SourceFile to trigger the processing pipeline
 * 6. List source files and notebooks
 * 7. Receive events as the pipeline processes the SourceFile
 * 8. Clean up (delete SourceFile and LegalCase)
 *
 * Documents the LegalCase CRUD operations and the basic event flow.
 * For detailed SourceFile processing results, see sourcefile-processing.ts.
 */

import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import type {
  LegalCaseCreatedEvent,
  LegalCaseUpdatedEvent,
  LegalCaseDeletedEvent,
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

/** Called when a new LegalCase is created. */
function onLegalCaseCreated(e: LegalCaseCreatedEvent): void {
  if (e.legalCase?.legalCaseId !== legalCaseId) return;
  console.log(`🗂  LegalCase created: ${legalCaseId}`);
}

/** Called when a LegalCase is updated (caseData, metadata, owner, etc.). */
function onLegalCaseUpdated(e: LegalCaseUpdatedEvent): void {
  if (e.legalCase?.legalCaseId !== legalCaseId) return;
  console.log(`🗂  LegalCase updated: ${legalCaseId}`);
}

/** Called when all SourceFiles in the LegalCase have been processed. */
function onLegalCaseReady(e: LegalCaseReadyEvent): void {
  if (e.legalCaseId !== legalCaseId) return;
  console.log(`🗂  LegalCase is ready: ${e.legalCaseUrl ?? legalCaseId}`);
}

/** Called when a LegalCase is deleted. */
function onLegalCaseDeleted(e: LegalCaseDeletedEvent): void {
  if (e.legalCase?.legalCaseId !== legalCaseId) return;
  console.log(`🗑  LegalCase deleted: ${legalCaseId}`);
}

/** Called when the pipeline finishes processing our SourceFile. */
function onSourceFileReady(e: SourceFileReadyEvent): void {
  if (e.sourceFileId !== sourceFileId) return;
  console.log(`📄 SourceFile is ready: ${sourceFileId}`);
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
    LegalCaseCreatedEvent: onLegalCaseCreated,
    LegalCaseUpdatedEvent: onLegalCaseUpdated,
    LegalCaseReadyEvent: onLegalCaseReady,
    LegalCaseDeletedEvent: onLegalCaseDeleted,
    SourceFileReadyEvent: onSourceFileReady,
    SourceFileFailedEvent: onSourceFileFailed,
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
          PII_FIRSTNAME: 'Max',
          PII_LASTNAME: 'Muster',
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

    // Retrieve the LegalCase.
    console.log('\n🔍 Retrieving LegalCase...');
    const { data: legalCase } = await LegalCaseService.getLegalCase({
      path: { legalCaseId },
    });
    console.log(`  ID:        ${legalCase?.legalCaseId}`);
    console.log(`  Reference: ${legalCase?.reference}`);
    console.log(`  Status:    ${legalCase?.status}`);
    console.log(`  Tenant:    ${legalCase?.tenantId}`);
    console.log(`  CaseData:  ${JSON.stringify(legalCase?.caseData)}`);
    console.log(`  Metadata:  ${JSON.stringify(legalCase?.metadata)}`);

    // Update the LegalCase - partial update, only provided fields are overwritten.
    // Supports: caseData, metadata, owner, ownerEmail.
    console.log('\n🤓 Updating LegalCase...');
    await LegalCaseService.updateLegalCase({
      path: { legalCaseId },
      body: {
        legalCaseId,
        reference: `claim-${legalCaseId.slice(0, 8)}`,
        caseData: { PII_FIRSTNAME: 'John' },
        metadata: { 'source.system.id': 'CRM-2024-002' },
      },
    });
    // Re-fetch to see the merged state (PATCH response only contains sent fields).
    const { data: updated } = await LegalCaseService.getLegalCase({
      path: { legalCaseId },
    });
    console.log(
      `  Updated name: ${updated?.caseData?.PII_FIRSTNAME} ${updated?.caseData?.PII_LASTNAME}`,
    );

    // List all LegalCases accessible to this agent (cross-tenant).
    console.log('\n📋 Listing all LegalCases...');
    const { data: allCases } = await LegalCaseService.listLegalCases();
    const cases = allCases ?? [];
    console.log(`  Total: ${cases.length} LegalCase(s)`);
    for (const lc of cases.slice(0, 5)) {
      console.log(`  - ${lc.legalCaseId} (ref: ${lc.reference}, tenant: ${lc.tenantId})`);
    }
    if (cases.length > 5) {
      console.log(`  ... and ${cases.length - 5} more`);
    }

    // Upload PDF via presigned URL (valid 5 min). See lib/api-client.ts.
    console.log('\n📎 Uploading PDF via presigned URL...');
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
        metadata: {},
      },
    });

    // List SourceFiles belonging to this LegalCase.
    const { data: sourceFiles } = await SourceFileService.listSourceFilesByLegalCase({
      path: { legalCaseId },
    });
    console.log(`\n1️⃣  LegalCase has ${sourceFiles?.length ?? 0} source file(s)`);

    // List notebooks - user-written case notes in HTML format.
    // Typically synced back to the source system upon case closure.
    const { data: notebooks } = await LegalCaseService.listNotebooks({
      path: { legalCaseId },
    });
    console.log(`1️⃣  LegalCase has ${notebooks?.length ?? 0} notebook(s)`);

    // In a real application, you would keep the event system running indefinitely
    // (or until a specific condition is met). Here we sleep briefly so the event
    // handlers above have a chance to fire during this demo.
    // NOTE: Real processing typically takes 5–30 minutes depending on document size.
    console.log(
      '\n😴 Waiting 10 s for events (illustration only - processing takes longer in practice)...',
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
  console.log('\n␡  Soft-Deleting SourceFile');
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
