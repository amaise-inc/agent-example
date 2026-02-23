/**
 * Central configuration - single source of truth for all env vars.
 *
 * All other modules import `config` from here; none read process.env directly.
 */

import 'dotenv/config';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const pkg = require('../package.json') as { version: string };

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `Missing required environment variable: ${name}. Copy .env.example to .env and fill in your credentials.`,
    );
  }
  return value;
}

export const config = {
  /** Auth0 token endpoint base URL. */
  authUrl: requireEnv('LEGALI_AUTH_URL'),
  /**
   * Agent API base URL. The generated SDK paths already include /agents/v1,
   * so we strip that suffix if the user-provided URL includes it.
   */
  apiUrl: requireEnv('LEGALI_API_URL').replace(/\/agents\/v1\/?$/, ''),
  /** OAuth 2.0 client ID from workspace settings. */
  clientId: requireEnv('LEGALI_CLIENT_ID'),
  /** OAuth 2.0 client secret from workspace settings. */
  clientSecret: requireEnv('LEGALI_CLIENT_SECRET'),
  /** Workspace tenant ID - required for multi-tenant agents, optional otherwise. */
  tenantId:
    process.env.TENANT_ID ||
    Object.entries(process.env).find(([k]) => k.startsWith('LEGALI_TENANTS_'))?.[1],
  /** Dashboard ID - required for the dashboard-processing example. */
  dashboardId: process.env.DASHBOARD_ID,
  /** SDK version sent in every heartbeat. Read from package.json. */
  sdkVersion: pkg.version,
  /** Heartbeat interval in ms. Default: 10 min. Override via HEARTBEAT_INTERVAL_MS for CI. */
  heartbeatIntervalMs: Number(process.env.HEARTBEAT_INTERVAL_MS) || 10 * 60 * 1000,
} as const;
