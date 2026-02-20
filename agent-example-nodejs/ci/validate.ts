/**
 * CI Smoke Test - internal use only, not a customer integration example.
 *
 * Verifies end-to-end connectivity: authenticates, sends a debug ping,
 * and polls heartbeat until PongEvent is received and acknowledged.
 * Run by the GitHub Actions workflow.
 *
 * NOTE: This script does NOT use lib/event-system.ts because it
 * intentionally only acks the PongEvent (see comment in Step 2 below).
 * The shared event system acks all events unconditionally, which would
 * interfere with the real agent's event delivery.
 *
 * Exits 0 on success, non-zero on failure.
 * CI grep markers: "Pong received" and "Validation successful"
 */

import { apiClient } from '../lib/api-client';
import { EventsService } from '../generated/sdk.gen';
import { config } from '../lib/config';

const MAX_ATTEMPTS = 10;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main(): Promise<void> {
  apiClient.configure();

  // Step 1: send debug PingEvent, will trigger a PongEvent
  console.log('Sending debug ping...');
  await EventsService.debugPing({
    body: { message: 'ci-validation' },
  });

  // Brief pause: give time to make the PongEvent visible before the first poll.
  await sleep(1_000);

  // Step 2: poll heartbeat until PongEvent arrives.
  //
  // INTENTIONAL DEVIATION from the "ack every event" rule (see lib/event-system.ts):
  // This CI probe runs alongside the production agent and only acks the PongEvent
  // it triggered. Other business events are deliberately left unacked so the real
  // agent re-receives them after the 5-minute re-delivery timeout. This is the
  // ONLY acceptable exception - production integrations MUST ack every event.
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    const { data } = await EventsService.heartbeat({
      body: { agentSdkVersion: config.sdkVersion },
    });

    const events = data?.events ?? [];
    console.log(`Heartbeat attempt ${attempt}/${MAX_ATTEMPTS}: ${events.length} event(s)`);

    const pong = events.find((e) => e.c.replace(/^\./, '') === 'PongEvent');
    if (pong) {
      if (!pong.id) throw new Error('PongEvent missing id');
      await EventsService.acknowledgeEvent({ body: { eventId: pong.id } });
      console.log('Pong received');
      console.log('Validation successful');
      return;
    }

    if (attempt < MAX_ATTEMPTS) {
      await sleep(config.heartbeatIntervalMs);
    }
  }

  throw new Error(`No PongEvent after ${MAX_ATTEMPTS} attempts`);
}

main().catch((err) => {
  console.error('Validation failed:', err instanceof Error ? err.message : String(err));
  process.exit(1);
});
