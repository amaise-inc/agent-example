import { describe, it, expect, vi, beforeEach } from 'vitest';

// Prevent dotenv from trying to load a real .env file
vi.mock('dotenv/config', () => ({}));

/** Set the four required env vars to valid defaults. */
function setRequiredEnv() {
  vi.stubEnv('LEGALI_AUTH_URL', 'https://auth.example.com');
  vi.stubEnv('LEGALI_API_URL', 'https://api.example.com');
  vi.stubEnv('LEGALI_CLIENT_ID', 'test-client-id');
  vi.stubEnv('LEGALI_CLIENT_SECRET', 'test-client-secret');
}

describe('config', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllEnvs();
    // Clear any LEGALI_TENANTS_* vars that might leak from the host environment
    for (const key of Object.keys(process.env)) {
      if (key.startsWith('LEGALI_TENANTS_')) delete process.env[key];
    }
  });

  // ---------------------------------------------------------------------------
  // requireEnv
  // ---------------------------------------------------------------------------

  describe('requireEnv', () => {
    it('throws when a required env var is missing', async () => {
      // Don't set any env vars - first requireEnv('LEGALI_AUTH_URL') should throw
      await expect(() => import('./config.js')).rejects.toThrow(
        'Missing required environment variable: LEGALI_AUTH_URL',
      );
    });

    it('error message mentions .env.example', async () => {
      await expect(() => import('./config.js')).rejects.toThrow('.env.example');
    });
  });

  // ---------------------------------------------------------------------------
  // apiUrl - suffix stripping
  // ---------------------------------------------------------------------------

  describe('apiUrl', () => {
    it('strips /agents/v1 suffix', async () => {
      setRequiredEnv();
      vi.stubEnv('LEGALI_API_URL', 'https://api.example.com/agents/v1');
      const { config } = await import('./config.js');
      expect(config.apiUrl).toBe('https://api.example.com');
    });

    it('strips /agents/v1/ with trailing slash', async () => {
      setRequiredEnv();
      vi.stubEnv('LEGALI_API_URL', 'https://api.example.com/agents/v1/');
      const { config } = await import('./config.js');
      expect(config.apiUrl).toBe('https://api.example.com');
    });

    it('leaves URL without the suffix unchanged', async () => {
      setRequiredEnv();
      vi.stubEnv('LEGALI_API_URL', 'https://api.example.com');
      const { config } = await import('./config.js');
      expect(config.apiUrl).toBe('https://api.example.com');
    });

    it('does not strip /agents/v1 when it is not at the end', async () => {
      setRequiredEnv();
      vi.stubEnv('LEGALI_API_URL', 'https://api.example.com/agents/v1/extra');
      const { config } = await import('./config.js');
      expect(config.apiUrl).toBe('https://api.example.com/agents/v1/extra');
    });
  });

  // ---------------------------------------------------------------------------
  // tenantId - fallback chain
  // ---------------------------------------------------------------------------

  describe('tenantId', () => {
    it('uses TENANT_ID when set', async () => {
      setRequiredEnv();
      vi.stubEnv('TENANT_ID', 'tenant-direct');
      const { config } = await import('./config.js');
      expect(config.tenantId).toBe('tenant-direct');
    });

    it('falls back to first LEGALI_TENANTS_* env var', async () => {
      setRequiredEnv();
      process.env['LEGALI_TENANTS_DEV'] = 'tenant-fallback';
      const { config } = await import('./config.js');
      expect(config.tenantId).toBe('tenant-fallback');
    });

    it('prefers TENANT_ID over LEGALI_TENANTS_*', async () => {
      setRequiredEnv();
      vi.stubEnv('TENANT_ID', 'preferred');
      process.env['LEGALI_TENANTS_DEV'] = 'fallback';
      const { config } = await import('./config.js');
      expect(config.tenantId).toBe('preferred');
    });

    it('is undefined when neither is set', async () => {
      setRequiredEnv();
      const { config } = await import('./config.js');
      expect(config.tenantId).toBeUndefined();
    });
  });

  // ---------------------------------------------------------------------------
  // heartbeatIntervalMs - default & override
  // ---------------------------------------------------------------------------

  describe('heartbeatIntervalMs', () => {
    it('uses HEARTBEAT_INTERVAL_MS when set', async () => {
      setRequiredEnv();
      vi.stubEnv('HEARTBEAT_INTERVAL_MS', '5000');
      const { config } = await import('./config.js');
      expect(config.heartbeatIntervalMs).toBe(5000);
    });

    it('defaults to 10 minutes', async () => {
      setRequiredEnv();
      const { config } = await import('./config.js');
      expect(config.heartbeatIntervalMs).toBe(600_000);
    });
  });

  // ---------------------------------------------------------------------------
  // sdkVersion
  // ---------------------------------------------------------------------------

  describe('sdkVersion', () => {
    it('reads version from package.json', async () => {
      setRequiredEnv();
      const { config } = await import('./config.js');
      expect(typeof config.sdkVersion).toBe('string');
      expect(config.sdkVersion).toMatch(/^\d+\.\d+\.\d+/);
    });
  });
});
