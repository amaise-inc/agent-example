/**
 * Shared event system.
 *
 * Handles the heartbeat, event dispatch, and acknowledgement for all examples.
 * Pass a handler map keyed by event class name; unregistered events are logged
 * and acknowledged automatically.
 *
 * IMPORTANT: Every event returned by heartbeat MUST be acknowledged, including
 * unregistered ones. Failing to do so causes them to accumulate and be
 * re-delivered after 5 minutes.
 */

import { EventsService } from '../generated/sdk.gen';
import type { BaseEvent } from '../generated/types.gen';
import { config } from './config';

export type AppEvent = BaseEvent;
export type EventByC<C extends AppEvent['c']> = Extract<AppEvent, { c: C }>;
export type EventHandlers = Partial<{
  [C in AppEvent['c']]: (e: EventByC<C>) => void | Promise<void>;
}>;

export class EventSystem {
  private handlers: EventHandlers;
  private intervalMs: number;
  private timer: NodeJS.Timeout | undefined;
  private stopped = false;

  constructor(handlers: EventHandlers, intervalMs = config.heartbeatIntervalMs) {
    this.handlers = handlers;
    this.intervalMs = intervalMs;
  }

  /** Starts the polling loop. Calls heartbeat immediately, then on interval. */
  start(): void {
    this.stopped = false;
    void this.loop();
  }

  /** Stops the polling loop. In-flight heartbeats finish but no new ones start. */
  stop(): void {
    this.stopped = true;
    clearTimeout(this.timer);
  }

  private async loop(): Promise<void> {
    await this.processHeartbeat();
    if (!this.stopped) {
      this.timer = setTimeout(() => void this.loop(), this.intervalMs);
    }
  }

  private async processHeartbeat(): Promise<void> {
    try {
      const { data } = await EventsService.heartbeat({
        body: { agentSdkVersion: config.sdkVersion },
      });
      const events = data?.events ?? [];

      if (events.length > 0) {
        console.log(`[${new Date().toISOString()}] Received ${events.length} event(s)`);
      }

      // Normalize "c": the API uses Jackson MINIMAL_CLASS format (e.g. ".PongEvent") - strip the
      // leading dot so handler registry lookups match the OpenAPI spec names.
      const normalized = events.map((e) => ({ ...e, c: e.c.replace(/^\./, '') }) as AppEvent);

      // Dispatch handlers sequentially, then ack all in parallel.
      for (const event of normalized) {
        const handler = this.handlers[event.c];
        if (handler) {
          try {
            await (handler as (e: AppEvent) => void | Promise<void>)(event);
          } catch (handlerErr) {
            console.error(
              `  Handler error for ${event.c}:`,
              handlerErr instanceof Error ? handlerErr.message : String(handlerErr),
            );
          }
        } else {
          console.log(`  Unhandled event type: ${event.c}`);
        }
      }

      // Ack all events in parallel - even if a handler threw, every event must be
      // acknowledged to prevent re-delivery on the next heartbeat.
      const ackable = normalized.filter((e) => e.id);
      const results = await Promise.allSettled(
        ackable.map((e) => EventsService.acknowledgeEvent({ body: { eventId: e.id! } })),
      );
      for (const [i, result] of results.entries()) {
        if (result.status === 'rejected') {
          const reason = result.reason;
          console.error(
            `  Failed to acknowledge event ${ackable[i]!.id}:`,
            reason instanceof Error ? reason.message : String(reason),
          );
        }
      }
      for (const event of normalized) {
        if (!event.id) {
          console.error(`  Event missing id (type: ${event.c}), cannot acknowledge`);
        }
      }
    } catch (err) {
      if (this.stopped) return;
      console.error(
        `[${new Date().toISOString()}] Heartbeat error:`,
        err instanceof Error ? err.message : String(err),
      );
    }
  }
}
