import { describe, it, expect, vi, beforeEach } from 'vitest';

// ---------------------------------------------------------------------------
// Mocks - applied to all (re-)imports via vi.resetModules()
// ---------------------------------------------------------------------------

vi.mock('simple-oauth2', () => ({
  ClientCredentials: class MockClientCredentials {
    getToken = vi.fn().mockResolvedValue({
      expired: () => false,
      token: { access_token: 'mock-token' },
    });
  },
}));

vi.mock('../generated/client.gen', () => ({
  client: {
    setConfig: vi.fn(),
    interceptors: { request: { use: vi.fn() } },
  },
}));

vi.mock('../generated/sdk.gen', () => ({
  FileService: {
    getPresignedUploadUri: vi.fn(),
  },
}));

vi.mock('./config', () => ({
  config: {
    clientId: 'test-id',
    clientSecret: 'test-secret',
    authUrl: 'https://auth.test.com',
    apiUrl: 'https://api.test.com',
    tenantId: 'test-tenant',
  },
}));

vi.mock('node:fs/promises', () => ({
  readFile: vi.fn(),
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Re-import all modules to get fresh singleton + mock instances. */
async function getModules() {
  const { apiClient } = await import('./api-client.js');
  const { FileService } = await import('../generated/sdk.gen.js');
  const { client } = await import('../generated/client.gen.js');
  const { readFile } = await import('node:fs/promises');
  return {
    apiClient,
    FileService: vi.mocked(FileService),
    client: vi.mocked(client),
    readFile: vi.mocked(readFile),
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('apiClient', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
  });

  // ---------------------------------------------------------------------------
  // configure()
  // ---------------------------------------------------------------------------

  describe('configure', () => {
    it('sets base URL and throwOnError on the generated client', async () => {
      const { apiClient, client } = await getModules();
      apiClient.configure();

      expect(client.setConfig).toHaveBeenCalledWith({
        baseUrl: 'https://api.test.com',
        throwOnError: true,
      });
    });

    it('installs a request interceptor', async () => {
      const { apiClient, client } = await getModules();
      apiClient.configure();

      expect(client.interceptors.request.use).toHaveBeenCalledTimes(1);
    });

    it('is idempotent - second call is a no-op', async () => {
      const { apiClient, client } = await getModules();
      apiClient.configure();
      apiClient.configure();

      expect(client.setConfig).toHaveBeenCalledTimes(1);
      expect(client.interceptors.request.use).toHaveBeenCalledTimes(1);
    });
  });

  // ---------------------------------------------------------------------------
  // uploadFile()
  // ---------------------------------------------------------------------------

  describe('uploadFile', () => {
    it('throws if not configured', async () => {
      const { apiClient } = await getModules();

      await expect(apiClient.uploadFile('/test.pdf')).rejects.toThrow('ApiClient not configured');
    });

    it('throws if presigned URI is missing', async () => {
      const { apiClient, FileService } = await getModules();
      apiClient.configure();

      vi.mocked(FileService.getPresignedUploadUri).mockResolvedValueOnce({
        data: { presignedUploadURI: undefined, tempFilename: 'tmp.pdf' },
      } as any);

      await expect(apiClient.uploadFile('/test.pdf')).rejects.toThrow(
        'Failed to get presigned upload URI',
      );
    });

    it('throws if tempFilename is missing', async () => {
      const { apiClient, FileService } = await getModules();
      apiClient.configure();

      vi.mocked(FileService.getPresignedUploadUri).mockResolvedValueOnce({
        data: { presignedUploadURI: 'https://s3.example.com/upload', tempFilename: undefined },
      } as any);

      await expect(apiClient.uploadFile('/test.pdf')).rejects.toThrow(
        'Failed to get presigned upload URI',
      );
    });

    it('throws on non-ok upload response', async () => {
      const { apiClient, FileService, readFile } = await getModules();
      apiClient.configure();

      vi.mocked(FileService.getPresignedUploadUri).mockResolvedValueOnce({
        data: {
          presignedUploadURI: 'https://s3.example.com/upload',
          tempFilename: 'tmp-123.pdf',
        },
      } as any);
      readFile.mockResolvedValueOnce(Buffer.from('pdf-content') as any);

      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 403,
        text: () => Promise.resolve('Forbidden'),
      } as Response);

      await expect(apiClient.uploadFile('/test.pdf')).rejects.toThrow(
        'File upload failed: 403 Forbidden',
      );
    });

    it('returns tempFilename on successful upload', async () => {
      const { apiClient, FileService, readFile } = await getModules();
      apiClient.configure();

      vi.mocked(FileService.getPresignedUploadUri).mockResolvedValueOnce({
        data: {
          presignedUploadURI: 'https://s3.example.com/upload',
          tempFilename: 'tmp-456.pdf',
        },
      } as any);
      readFile.mockResolvedValueOnce(Buffer.from('pdf-content') as any);

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
      } as Response);

      const result = await apiClient.uploadFile('/test.pdf');

      expect(result).toBe('tmp-456.pdf');
      expect(readFile).toHaveBeenCalledWith('/test.pdf');
      expect(fetchSpy).toHaveBeenCalledWith('https://s3.example.com/upload', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/pdf' },
        body: expect.any(Buffer),
      });
    });
  });
});
