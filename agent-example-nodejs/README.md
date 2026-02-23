# amaise Agent Example - Node.js / TypeScript (REST API)

A minimal TypeScript example that integrates with the amaise Agent API using a generated TypeScript SDK client.

It demonstrates the core integration flows:

1. **Authentication**: OAuth 2.0 Client Credentials
2. **LegalCase Lifecycle**: CRUD operations, event handling, SourceFile upload
3. **File Upload**: Presigned URLs (valid for 5 minutes)
4. **SourceFile Processing**: Upload PDF, wait for pipeline, retrieve extracted data
5. **Event System**: Heartbeat & acknowledge lifecycle
6. **Dashboard Retrieval**: Fetch dashboard answers and actions

## Prerequisites

- Node.js >= 22
- Agent credentials from your amaise workspace (Settings > Integration)

## Setup

```bash
npm install
cp .env.example .env
# Edit .env with your credentials
```

## Code Generation

The TypeScript API client is auto-generated from the Agent API OpenAPI spec using [`@hey-api/openapi-ts`](https://heyapi.dev). Before first use, generate the client:

```bash
make generate
```

Re-run whenever the API changes. The `generated/` directory is git-ignored - each developer generates locally.

## Usage

Each example can be run independently:

```bash
# Full LegalCase lifecycle: CRUD, events, SourceFile upload
npm run legalcase-lifecycle

# Process a single SourceFile: upload PDF, wait for pipeline, retrieve extracted data
npm run sourcefile-processing

# Process files and retrieve dashboard results (requires DASHBOARD_ID in .env)
npm run dashboard-processing
```

## Development

```bash
# Format code with Prettier
make format

# Type-check (tsc) and lint (Prettier)
make lint
```

## Configuration

All configuration is done via environment variables (see `.env.example`):

| Variable                | Description                                                                                                                                                                             |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LEGALI_AUTH_URL`       | Auth endpoint                                                                                                                                                                           |
| `LEGALI_API_URL`        | Agent API base URL                                                                                                                                                                      |
| `LEGALI_CLIENT_ID`      | OAuth client ID from workspace settings                                                                                                                                                 |
| `LEGALI_CLIENT_SECRET`  | OAuth client secret from workspace settings                                                                                                                                             |
| `TENANT_ID`             | Workspace tenant ID (single-tenant setups)                                                                                                                                              |
| `LEGALI_TENANTS_*`      | Tenant map: `LEGALI_TENANTS_<NAME>=<uuid>`. The first entry is used as the active tenant. Alternative to `TENANT_ID` for multi-tenant setups. E.g. `LEGALI_TENANTS_DEPARTMENT-1=<uuid>` |
| `DASHBOARD_ID`          | Dashboard ID (required for dashboard example)                                                                                                                                           |
| `HEARTBEAT_INTERVAL_MS` | Heartbeat poll interval override (default: 10 min)                                                                                                                                      |

## Project Structure

```
agent-example-nodejs/
├── generated/                   # Auto-generated SDK (git-ignored)
│   ├── client/                  # Fetch client internals
│   ├── core/                    # Core utilities (auth, serialization)
│   ├── client.gen.ts            # Fetch client singleton
│   ├── sdk.gen.ts               # SDK classes grouped by API tag
│   └── types.gen.ts             # TypeScript types from OpenAPI spec
├── assets/
│   └── sample.pdf               # Sample PDF for testing
├── ci/
│   └── validate.ts              # CI smoke test (internal)
├── lib/
│   ├── config.ts                # Central env var configuration
│   ├── api-client.ts            # Auth, client setup, file upload helper
│   └── event-system.ts          # Shared heartbeat/dispatch/ack loop
├── examples/
│   ├── legalcase-lifecycle.ts   # Full LegalCase lifecycle: CRUD, events, SourceFile upload
│   ├── sourcefile-processing.ts # Process a single SourceFile and retrieve extracted data
│   └── dashboard-processing.ts  # Retrieve dashboard answers after processing
├── openapi-ts.config.ts         # Code generation config
├── tsconfig.json
├── .env.example
├── .prettierrc                  # Prettier config (printWidth: 100, singleQuote)
├── Makefile                     # Build, run, lint, generate commands
├── Dockerfile                   # Multi-stage Docker build (CI smoke test)
├── package.json
└── README.md
```

### Generated SDK Classes

The SDK is generated with the `byTags` strategy, producing one class per API tag:

| Class               | Description                                     |
| ------------------- | ----------------------------------------------- |
| `LegalCaseService`  | Create, update, delete, and list LegalCases     |
| `SourceFileService` | Create, move, replace, delete SourceFiles       |
| `EventsService`     | Heartbeat and event acknowledgement             |
| `DashboardsService` | Retrieve dashboard answers and actions          |
| `ExportService`     | Retrieve published dossier exports              |
| `FileService`       | Presigned upload and download URIs              |
| `FileProxyService`  | File upload/download through the amaise backend |

All classes use static methods - no instantiation needed:

```typescript
import { LegalCaseService, SourceFileService, EventsService } from '../generated/sdk.gen';

await LegalCaseService.createLegalCase({ body: { ... } });
await SourceFileService.createSourceFile({ body: { ... } });
const { data } = await EventsService.heartbeat({ body: { ... } });
```
