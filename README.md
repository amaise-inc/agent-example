# amaise Agent Examples

## Overview

- The amaise Example Agents are sample applications showcasing how to build a customer-specific agent.
- The amaise SDK ensures solid two-way communication between the customers' environment and the amaise cloud.
- The latest version is available on amaise's GitHub repository (https://github.com/amaise-inc/agent-example).

# No-Code Quickstart

**Prerequisites**

- Access to amaise's GitHub repository at [https://github.com/amaise-inc/agent-example](https://github.com/amaise-inc/agent-example)
- Agent credentials for your workspace, available through the app in the workspace settings under "Integration"
- Recent versions of Docker and Docker Compose
- Internet access to `*.amaise.com`

**Steps:**

1. Set up amaise cloud credentials in `quickstart/agent.env`
2. In the `quickstart` directory, run `docker compose build`.
3. Run `docker compose up agent_spring` or `docker compose up agent_quarkus`
4. The agent runs and starts exchanging data
5. On the app, check the agent status in Workspace Settings / Integration.
6. In case you need monitoring or proxy support, you can start the corresponding services:
   - Monitoring: `docker compose up prometheus grafana`
     - Access Grafana at [http://localhost:3000/](http://localhost:3000/) (admin/adminex)
     - Add Prometheus data source pointing to http://prometheus:9090/
   - HTTP Proxy: `docker compose up squid` and adapt `agent.env` to use the proxy

## Node.js (REST API) Example

For integrations using direct REST API calls (without the Java SDK), see the `agent-example-nodejs/` directory.
It demonstrates all core flows using plain HTTP requests from Node.js.

## Development

See the READMEs in the framework-specific subdirectories for details.

### Credentials

Make sure to set the secrets correctly via environment variables or a properties file:

```
LEGALI_API_URL=https://{subdomain}.agents.{region}.amaise.com/agents/v1
LEGALI_AUTH_URL=https://auth.{region}.amaise.com/
LEGALI_CLIENT_ID=<from workspace>
LEGALI_CLIENT_SECRET=<from workspace>
```

## Agent Authorization

- The amaise SDK is authorized via amaise's IDP `https://auth.{region}.amaise.com` using the OAuth 2.0 Client Credentials Grant.
- Client credentials can be rotated by workspace admins in the amaise app.

&nbsp;

## Protocols and Firewall

- The amaise SDK communicates via HTTPS/TLS 1.3 to REST endpoints (port 443).
- No inbound access is required.
- Outbound access is required to the following endpoints:

| Service            | Endpoint                                           | Method                    |
| ------------------ | -------------------------------------------------- | ------------------------- |
| Authentication     | `https://auth.{region}.amaise.com/oauth/token`     | POST                      |
| Agent API          | `https://{subdomain}.agents.{region}.amaise.com/*` | GET/POST/PUT/PATCH/DELETE |
| File Upload        | `https://upload.{region}.amaise.com/*`             | PUT                       |
| File Download (DE) | `https://data.{region}.amaise.com/*`               | GET                       |
| File Download (CH) | `https://data-ch.{region}.amaise.com/*`            | GET                       |
| File Download (US) | `https://data-us.{region}.amaise.com/*`            | GET                       |

Where `{region}` is `eu`, `us`, or `ch`, and `{subdomain}` is your subdomain (shown in Integration Settings).

- For OpenAPI3 specs refer to `https://agents.{region}.amaise.com/doc/swagger-ui/index.html`

### File Transfer API

The SDK transfers files directly from and to AWS CloudFront using presigned URLs. The file transfer endpoints must be
allowed outbound on your firewall.

**Complete endpoint list per environment:**

| Environment | Upload URL                      | Data URL (DE)                 | Data URL (CH)                    | Data URL (US)                    |
| ----------- | ------------------------------- | ----------------------------- | -------------------------------- | -------------------------------- |
| **EU**      | `https://upload.eu.amaise.com`  | `https://data.eu.amaise.com`  | `https://data-ch.eu.amaise.com`  | `https://data-us.eu.amaise.com`  |
| **US**      | `https://upload.us.amaise.com`  | `https://data.us.amaise.com`  | `https://data-ch.us.amaise.com`  | `https://data-us.us.amaise.com`  |
| **CH**      | `https://upload.ch.amaise.com`  | `https://data.ch.amaise.com`  | `https://data-ch.ch.amaise.com`  | `https://data-us.ch.amaise.com`  |
| **Dev**     | `https://upload.dev.amaise.com` | `https://data.dev.amaise.com` | `https://data-ch.dev.amaise.com` | `https://data-us.dev.amaise.com` |

Your workspace's data region determines which Data URL is used. The exact URLs are shown in the amaise workspace
settings under **Integration > File Transfer**. You only need to allowlist the Upload URL and the Data URL(s) matching
your workspace's data region(s).

```bash
# Example: EU environment, CH data region
PUT https://upload.eu.amaise.com/{any}
GET https://data-ch.eu.amaise.com/{any}
```

```
# Enabled
legali.fileservice = CLOUDFRONT
```

Note: The `LEGALI` file service is deprecated and only used for development.
For details, refer to `README-FILES.md`.

## Entities, Events and Mapping

For detailed information about Entities and Events refer to Swagger (https://agents.eu.amaise.com/doc/swagger-ui/index.html)

### Entity Metadata

The `LegalCase` and `SourceFile` entities contain a metadata field to add integration-specific key-value pairs, with
type `string` / `string`. Defaults can be set in the application config or via environment variables.

Those properties are used to store arbitrary data, e.g. internal IDs. Further, this metadata is also used to override amaise's processing pipeline the given source file. Empty strings in properties are considered as not set.

### Override amaise's processing pipeline

A customer might have predefined document types that should not be processed by amaise's extraction and classification pipeline. In this case, a mapping key is passed upon creation of the sourcefile.

```
legali.mapping.key = "a5bf"
```

amaise checks the mapping configuration for a corresponding entry. If such entry has been configured in the amaise app, the defined rules for the document's folder, label, whether it is split and which issue date is chosen are applied.

Further, the following properties can be used to override processing. If those properties are not set or empty, the data extracted by amaise is used.

```
# sets a receipt date on the document
legali.metadata.receiptdate = "2020-01-01" (NOTE: YYYY-MM-DD)

# sets the alternative title on the document (NOTE: updatable metadata field)
legali.metadata.alttitle = "alternative title"

# sets the pagination number on the document (NOTE: updatable metadata field)
legali.metadata.pagination.number = "123"

# sets the pagination id on the document (NOTE: updatable metadata field)
legali.metadata.pagination.id = "Wf0ZoNA5"

# For debugging: the pipeline can be disabled entirely
legali.pipeline.disabled = "true"
```

### Events

The agent must subscribe to the events it wants to receive.
A list of all events can be found on swagger https://agents.eu.amaise.com/doc/swagger-ui/index.html

**Delivery contract:** each event is delivered up to **5 times**. Unacked events are redelivered
**5 minutes** after each heartbeat that returned them. After 5 unacknowledged deliveries the event
is permanently deleted. Events also expire after a hard **3-day TTL** from publication. Always ack
every event you receive — including ones your integration ignores.
