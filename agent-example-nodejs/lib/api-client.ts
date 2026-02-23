/**
 * amaise Agent API client.
 *
 * Wraps the generated SDK client with OAuth 2.0 authentication, tenant
 * header injection, and a presigned-URL file upload helper.
 *
 * Usage:
 *   import { apiClient } from '../lib/api-client';
 *   apiClient.configure();          // call once at startup
 *   await apiClient.uploadFile(path);
 *
 * Once configured, the following generated service clients (from generated/sdk.gen.ts)
 * are ready to use - they share the same authenticated HTTP client:
 *
 *   LegalCaseService      - create, update, delete, list LegalCases
 *   SourceFileService     - create, replace, move, delete SourceFiles; retrieve processed data
 *   FileService           - presigned upload/download URLs (used internally by uploadFile()) - recommended over proxy service
 *   FileProxyService      - upload/download via amaise proxy (alternative to presigned URLs)
 *   EventsService         - heartbeat polling, event acknowledgement (used by EventSystem)
 *   DashboardsService     - retrieve dashboard answers and actions
 *   ExportService         - retrieve published exports
 */

import { AccessToken, ClientCredentials } from 'simple-oauth2';
import { readFile } from 'node:fs/promises';
import { client } from '../generated/client.gen';
import { FileService } from '../generated/sdk.gen';
import { config } from './config';

class ApiClient {
  private configured = false;
  private oauthClient: ClientCredentials | null = null;
  private cachedToken: AccessToken | null = null;

  // ---------------------------------------------------------------------------
  // Token management (OAuth 2.0 Client Credentials via simple-oauth2)
  // ---------------------------------------------------------------------------

  private async getAccessToken(): Promise<string> {
    // Refresh 60 seconds before actual expiry
    if (!this.cachedToken || this.cachedToken.expired(60)) {
      this.cachedToken = await this.oauthClient!.getToken({
        audience: 'https://api.amaise.com/AGENTS',
      });
    }
    const accessToken = this.cachedToken.token['access_token'];
    if (typeof accessToken !== 'string') {
      throw new Error('OAuth token response is missing access_token');
    }
    return accessToken;
  }

  // ---------------------------------------------------------------------------
  // Client setup
  // ---------------------------------------------------------------------------

  /**
   * Configures the generated API client with base URL, auth interceptor,
   * and tenant header. Call once at startup before using any SDK classes.
   * Subsequent calls are no-ops.
   */
  configure(): void {
    if (this.configured) return;
    this.configured = true;

    this.oauthClient = new ClientCredentials({
      client: { id: config.clientId, secret: config.clientSecret },
      auth: {
        tokenHost: config.authUrl,
        tokenPath: '/oauth/token',
      },
      options: {
        // Send credentials in the JSON body (matches the amaise auth server expectation)
        authorizationMethod: 'body',
        bodyFormat: 'json',
      },
    });

    client.setConfig({ baseUrl: config.apiUrl, throwOnError: true });

    client.interceptors.request.use(async (request: Request) => {
      const token = await this.getAccessToken();
      request.headers.set('Authorization', `Bearer ${token}`);
      if (config.tenantId) {
        request.headers.set('X-Tenant-ID', config.tenantId);
      }
      return request;
    });
  }

  // ---------------------------------------------------------------------------
  // File upload via presigned URL
  // ---------------------------------------------------------------------------

  /**
   * Uploads a PDF file using the presigned URL flow (recommended):
   * 1. GET  /files/presigned-upload-uri  -> { presignedUploadURI, tempFilename }
   *    The presigned URL is valid for 5 minutes.
   * 2. PUT  presignedUploadURI           <- binary PDF
   * 3. Use  tempFilename as tempFileUri when creating the SourceFile
   *
   * Alternative: POST /proxy accepts multipart file uploads through the amaise
   * backend. Simpler but slower and with lower size limits - use only if direct
   * presigned URL access is restricted by network policies.
   */
  async uploadFile(filePath: string): Promise<string> {
    if (!this.configured) {
      throw new Error('ApiClient not configured. Call apiClient.configure() first.');
    }

    const { data } = await FileService.getPresignedUploadUri();
    if (!data?.presignedUploadURI || !data.tempFilename) {
      throw new Error('Failed to get presigned upload URI');
    }

    const fileBuffer = await readFile(filePath);
    const uploadRes = await fetch(data.presignedUploadURI, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/pdf' },
      body: fileBuffer,
    });
    if (!uploadRes.ok) {
      throw new Error(`File upload failed: ${uploadRes.status} ${await uploadRes.text()}`);
    }

    return data.tempFilename;
  }
}

/** Singleton API client instance. */
export const apiClient = new ApiClient();
