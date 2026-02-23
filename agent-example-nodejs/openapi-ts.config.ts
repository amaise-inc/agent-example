import { defineConfig, defaultPlugins } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'openapi-agent.json',
  output: {
    indexFile: false,
    path: 'generated',
  },
  plugins: [
    ...defaultPlugins,
    '@hey-api/client-fetch',
    {
      name: '@hey-api/sdk',
      operations: { strategy: 'byTags' },
    },
    {
      name: '@hey-api/typescript',
      enums: 'javascript',
      exportInlineEnums: true,
    },
  ],
});
