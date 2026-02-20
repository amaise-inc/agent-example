import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('../generated/sdk.gen', () => ({
  EventsService: {
    heartbeat: vi.fn(),
    acknowledgeEvent: vi.fn(),
  },
}));

vi.mock('./config', () => ({
  config: {
    sdkVersion: '1.0.0-test',
    heartbeatIntervalMs: 60_000,
  },
}));

import { EventSystem, type EventHandlers } from './event-system.js';
import { EventsService } from '../generated/sdk.gen.js';

/* eslint-disable @typescript-eslint/no-explicit-any -- test mocks are loosely typed */
const mockHeartbeat = EventsService.heartbeat as any;
const mockAck = EventsService.acknowledgeEvent as any;

/** Build a mock heartbeat response with the given events. */
function heartbeatResponse(events: Array<{ c: string; id?: string; [k: string]: unknown }>) {
  return { data: { events } };
}

describe('EventSystem', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ---------------------------------------------------------------------------
  // Event discriminator normalization
  // ---------------------------------------------------------------------------

  describe('event discriminator normalization', () => {
    it('strips leading dot from Jackson MINIMAL_CLASS format', async () => {
      const handler = vi.fn();
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: '.PongEvent', id: 'evt-1' }]));
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({ PongEvent: handler } as EventHandlers);
      await (es as any).processHeartbeat();

      expect(handler).toHaveBeenCalledWith(expect.objectContaining({ c: 'PongEvent' }));
    });

    it('leaves already-clean discriminator unchanged', async () => {
      const handler = vi.fn();
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'PongEvent', id: 'evt-1' }]));
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({ PongEvent: handler } as EventHandlers);
      await (es as any).processHeartbeat();

      expect(handler).toHaveBeenCalledWith(expect.objectContaining({ c: 'PongEvent' }));
    });
  });

  // ---------------------------------------------------------------------------
  // Handler dispatch
  // ---------------------------------------------------------------------------

  describe('handler dispatch', () => {
    it('calls registered handler with the event', async () => {
      const handler = vi.fn();
      mockHeartbeat.mockResolvedValueOnce(
        heartbeatResponse([{ c: 'PongEvent', id: 'evt-1', tenantId: 't-1' }]),
      );
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({ PongEvent: handler } as EventHandlers);
      await (es as any).processHeartbeat();

      expect(handler).toHaveBeenCalledTimes(1);
      expect(handler).toHaveBeenCalledWith(
        expect.objectContaining({ c: 'PongEvent', id: 'evt-1', tenantId: 't-1' }),
      );
    });

    it('logs unhandled event types without throwing', async () => {
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'UnknownEvent', id: 'evt-2' }]));
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(console.log).toHaveBeenCalledWith('  Unhandled event type: UnknownEvent');
    });

    it('dispatches multiple events sequentially in order', async () => {
      const order: string[] = [];
      const handler1 = vi.fn(() => order.push('first'));
      const handler2 = vi.fn(() => order.push('second'));

      mockHeartbeat.mockResolvedValueOnce(
        heartbeatResponse([
          { c: 'PongEvent', id: 'evt-1' },
          { c: 'LegalCaseCreatedEvent', id: 'evt-2' },
        ]),
      );
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({
        PongEvent: handler1,
        LegalCaseCreatedEvent: handler2,
      } as unknown as EventHandlers);
      await (es as any).processHeartbeat();

      expect(order).toEqual(['first', 'second']);
    });
  });

  // ---------------------------------------------------------------------------
  // Handler error isolation
  // ---------------------------------------------------------------------------

  describe('handler error isolation', () => {
    it('catches handler errors and continues', async () => {
      const failHandler = vi.fn(() => {
        throw new Error('handler boom');
      });
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'PongEvent', id: 'evt-1' }]));
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({ PongEvent: failHandler } as EventHandlers);
      await (es as any).processHeartbeat();

      expect(console.error).toHaveBeenCalledWith('  Handler error for PongEvent:', 'handler boom');
    });

    it('still acknowledges events after handler error', async () => {
      const failHandler = vi.fn(() => {
        throw new Error('boom');
      });
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'PongEvent', id: 'evt-1' }]));
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({ PongEvent: failHandler } as EventHandlers);
      await (es as any).processHeartbeat();

      expect(mockAck).toHaveBeenCalledWith({ body: { eventId: 'evt-1' } });
    });
  });

  // ---------------------------------------------------------------------------
  // Event acknowledgement
  // ---------------------------------------------------------------------------

  describe('event acknowledgement', () => {
    it('acknowledges all events with ids', async () => {
      mockHeartbeat.mockResolvedValueOnce(
        heartbeatResponse([
          { c: 'PongEvent', id: 'evt-1' },
          { c: 'PongEvent', id: 'evt-2' },
          { c: 'PongEvent', id: 'evt-3' },
        ]),
      );
      mockAck.mockResolvedValue({} as any);

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(mockAck).toHaveBeenCalledTimes(3);
      expect(mockAck).toHaveBeenCalledWith({ body: { eventId: 'evt-1' } });
      expect(mockAck).toHaveBeenCalledWith({ body: { eventId: 'evt-2' } });
      expect(mockAck).toHaveBeenCalledWith({ body: { eventId: 'evt-3' } });
    });

    it('does not try to ack events without id', async () => {
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'PongEvent' }]));

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(mockAck).not.toHaveBeenCalled();
    });

    it('logs events missing id', async () => {
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'PongEvent' }]));

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(console.error).toHaveBeenCalledWith(
        '  Event missing id (type: PongEvent), cannot acknowledge',
      );
    });

    it('logs ack failures without crashing', async () => {
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([{ c: 'PongEvent', id: 'evt-1' }]));
      mockAck.mockRejectedValueOnce(new Error('ack failed'));

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(console.error).toHaveBeenCalledWith(
        '  Failed to acknowledge event evt-1:',
        'ack failed',
      );
    });
  });

  // ---------------------------------------------------------------------------
  // Empty heartbeat
  // ---------------------------------------------------------------------------

  describe('empty heartbeat', () => {
    it('handles no events gracefully', async () => {
      mockHeartbeat.mockResolvedValueOnce(heartbeatResponse([]));

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(mockAck).not.toHaveBeenCalled();
    });

    it('handles undefined events array', async () => {
      mockHeartbeat.mockResolvedValueOnce({ data: {} } as any);

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(mockAck).not.toHaveBeenCalled();
    });
  });

  // ---------------------------------------------------------------------------
  // Heartbeat error handling
  // ---------------------------------------------------------------------------

  describe('heartbeat error handling', () => {
    it('catches and logs heartbeat errors', async () => {
      mockHeartbeat.mockRejectedValueOnce(new Error('network error'));

      const es = new EventSystem({});
      await (es as any).processHeartbeat();

      expect(console.error).toHaveBeenCalledWith(
        expect.stringContaining('Heartbeat error'),
        'network error',
      );
    });

    it('suppresses error logging when stopped', async () => {
      mockHeartbeat.mockRejectedValueOnce(new Error('network error'));

      const es = new EventSystem({});
      (es as any).stopped = true;
      await (es as any).processHeartbeat();

      expect(console.error).not.toHaveBeenCalled();
    });
  });

  // ---------------------------------------------------------------------------
  // Start / stop lifecycle
  // ---------------------------------------------------------------------------

  describe('start / stop lifecycle', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('start() triggers immediate heartbeat', async () => {
      mockHeartbeat.mockResolvedValue(heartbeatResponse([]));

      const es = new EventSystem({}, 60_000);
      es.start();
      await vi.advanceTimersByTimeAsync(0);

      expect(mockHeartbeat).toHaveBeenCalledTimes(1);
      es.stop();
    });

    it('schedules next heartbeat after interval', async () => {
      mockHeartbeat.mockResolvedValue(heartbeatResponse([]));

      const es = new EventSystem({}, 1000);
      es.start();
      await vi.advanceTimersByTimeAsync(0); // first heartbeat

      mockHeartbeat.mockClear();
      await vi.advanceTimersByTimeAsync(1000); // interval fires

      expect(mockHeartbeat).toHaveBeenCalledTimes(1);
      es.stop();
    });

    it('stop() prevents new heartbeats', async () => {
      mockHeartbeat.mockResolvedValue(heartbeatResponse([]));

      const es = new EventSystem({}, 100);
      es.start();
      await vi.advanceTimersByTimeAsync(0);

      es.stop();
      mockHeartbeat.mockClear();

      await vi.advanceTimersByTimeAsync(500);

      expect(mockHeartbeat).not.toHaveBeenCalled();
    });
  });
});
