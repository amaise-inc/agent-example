/**
 * Example: Dashboard Processing
 *
 * Dashboard retrieval after case processing:
 * 1. Start event system (register handlers before triggering pipeline)
 * 2. Create a LegalCase and upload a SourceFile
 * 3. Receive LegalCaseReadyEvent (all files processed) via shared event system
 * 4. Retrieve dashboard answers and actions
 * 5. Clean up
 *
 * Requires config.dashboardId to be set in your .env file.
 */

import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import type { LegalCaseReadyEvent, SourceFileFailedEvent } from '../generated/types.gen';
import { DashboardsService, LegalCaseService, SourceFileService } from '../generated/sdk.gen';
import { apiClient } from '../lib/api-client';
import { config } from '../lib/config';
import { EventSystem } from '../lib/event-system';

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

apiClient.configure();

if (!config.dashboardId) {
  throw new Error('Set config.dashboardId in your .env file');
}

const SAMPLE_PDF = resolve(import.meta.dirname, '../assets/sample.pdf');
const legalCaseId = randomUUID();
const sourceFileId = randomUUID();

// ---------------------------------------------------------------------------
// Event handlers
// ---------------------------------------------------------------------------

/** Called when all SourceFiles in the LegalCase have been processed. */
async function onLegalCaseReady(e: LegalCaseReadyEvent): Promise<void> {
  if (e.legalCaseId !== legalCaseId) return;
  console.log('🗂  LegalCase is ready!');

  console.log(`\nFetching dashboard ${config.dashboardId}...`);
  const { data: dashboard } = await DashboardsService.getDashboard({
    path: { legalCaseId, dashboardId: config.dashboardId! },
  });

  console.log('\n--- Dashboard Results ---');
  // Answers are polymorphic: "answer" (text) or "trafficLight" (text + color).
  for (const baseAnswer of dashboard?.answers ?? []) {
    if (baseAnswer.type === 'trafficLight') {
      console.log(`Q: ${baseAnswer.title}`);
      console.log(`A: ${baseAnswer.answer ?? '(no answer)'} [${baseAnswer.trafficLight}]`);
    } else {
      console.log(`Q: ${baseAnswer.title}`);
      console.log(`A: ${baseAnswer.answer ?? '(no answer)'}`);
    }
    console.log();
  }
  for (const action of dashboard?.actions ?? []) {
    console.log(`Action: ${action.name} (itemId: ${action.itemId})`);
  }
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
    LegalCaseReadyEvent: onLegalCaseReady,
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
        fileReference: 'sample.pdf',

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
