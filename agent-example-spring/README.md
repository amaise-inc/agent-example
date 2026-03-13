# amaise Agent Example (Spring Boot)

Example demonstrating the use of the agent SDK from a Spring Boot application.

## Development

In the `ExampleThread.java` and `ExampleEventService.java`, you see examples of how to call different APIs and subscribe to events. JavaDoc can be downloaded for the SDK module for further explanation and thrown exceptions.

### Application Logic

- After the Spring Boot application is initialized, the agent connects to the amaise cloud.
- If the connection can be established, a `StartConnectorEvent` Event is published.
- This has the following effects:
  - Upon receiving this event, the `ExampleService` runs `ExampleThread` that creates legal cases and adds source files on the amaise workspace.
  - The `ExampleEventService` starts listening to events triggered on the API.
    - As an example, it requests a `pong`-Event from the API.
    - This pong will be sent by the API asynchronously and be visible in the EventHandler
- All SDK entities and methods contain JavaDoc annotations.

### Build, run, and monitor

See Makefile for a reference of build targets.

The following endpoints are provided in the Example Agent.

```
# Liveness (agent is up)
http://localhost:8085/actuator/health/liveness

# Readiness (agent can connect to the amaise cloud)
http://localhost:8085/actuator/health/readiness

# Prometheus metrics; we recommend setting up alerts on the heartbeat_* counters.
http://localhost:8085/actuator/prometheus

```

&nbsp;
&nbsp;

---

&nbsp;
&nbsp;

## Dashboard Answering

The `ExampleDashboardProcessingThread` demonstrates how to retrieve and process dashboard answers after a case is ready. Enable it with `legali.example.enable-dashboard-processing-thread=true` and set `legali.example.dashboard-id`.

Dashboard answers are polymorphic — each answer has a `type` discriminator:

| Type           | Java Type                             | Key Fields                                        |
| -------------- | ------------------------------------- | ------------------------------------------------- |
| `answer`       | `AgentDashboardAnswerDTO`             | `answer()`                                        |
| `trafficLight` | `AgentDashboardTrafficLightAnswerDTO` | `answer()`, `trafficLight()`                      |
| `list`         | `AgentDashboardListAnswerDTO`         | `answer()`, `headers()` (field labels), `items()` |

### List answers

List answers include field labels via `headers()` and structured `items()`:

- **`headers()`**: List of `AgentDashboardListHeaderDTO` field label definitions with `key()` (matches item map keys), `label()` (locale map), and `type()` (data type).
- **`items()`**: List of `Map<String, Object>` where each key corresponds to a field label `key()`.

### Actions

Dashboards may include `actions()` — named operations with parameters (e.g. `send_rejection_email` with `from_email` / `to_email`). Dispatch them based on `action.name()` and read `action.params()`.

## References

### Configuration and Deployment

All configurations can be set as environment variables by Spring Boot configuration convention.

```
# Example Agent Config
# Iterations and parallel threads
legali.example.iterations=1
spring.task.execution.pool.max-size=1
spring.task.execution.pool.core-size=1

# Run cleanup round to delete test legal cases
legali.example.cleanup=true

# Threads for processing examples
# Enable examples thread that listens for ready sourcefiles and prints their metadata
legali.example.enable-sourcefile-processing-thread=false
# Enable examples thread that listens for ready legal cases, fetches their dashboards and prints their answers
legali.example.enable-dashboard-processing-thread=false

# Disable processing pipeline for development (do not use in production)
legali.default-metadata.legali.pipeline.disabled=true
legali.default-metadata.legali.uploader=example-agent

# Connection to the amaise Cloud
legali.auth-url=https://auth.eu.amaise.com
legali.api-url=https://agents.eu.amaise.com/agents/v1
legali.client-id=<>
legali.client-secret=<>

# Set departments and tenant ids
legali.example.tenants.department-1=ef99fd60-e06e-4e2a-99b5-01bc37f710ae
legali.example.tenants.department-2=526602b4-0e96-4c90-bc28-ce720c9c6521

# Dashboard processing
legali.example.dashboard-id=353d217b-987d-4880-95c4-57eea033075a

# FileService: Use CLOUDFRONT to upload files directly to AWS.
# If there are network restrictions, you can use LEGALI to proxy via amaise API. This is not recommended.
legali.fileservice=CLOUDFRONT

#legali.request-connection-timeout-seconds=30
#legali.max-connection-retries=5
#legali.request-read-timeout-seconds=90
#legali.max-failed-heartbeats=5

# Proxy setup
#legali.http-proxy-host=localhost
#legali.http-proxy-port=3128

# Logging and Debugging
logging.level.root=INFO
logging.level.ch.legali.sdk.example=INFO
logging.level.ch.legali.sdk.services=INFO
logging.level.ch.legali.sdk.internal=INFO

# Debug HTTP connection
#logging.level.feign=DEBUG
#legali.feign-log-level=FULL

# Monitoring
server.port=8085
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
management.endpoint.health.group.readiness.include=readinessState,agent
```
