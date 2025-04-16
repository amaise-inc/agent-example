# amaise Agent Example (Quarkus)

Example demonstrating the use of the agent SDK from a Quarkus application.

## Development

In the `ExampleService.java` and `ExampleEventService.java`, you see examples of how to call different APIs and subscribe to events. JavaDoc can be downloaded for the SDK module for further explanation and thrown exceptions.

### Application Logic

- After the Quarkus application is initialized, the agent connects to the amaise cloud.
- If the connection can be established, an Event is published to the `started` Vert.x Event Bus Address.
- This has the following effects:
	- Upon receiving this event, the `ExampleService` creates legal cases and adds source files on the amaise workspace.
	- The `ExampleEventService` starts listening to events triggered on the API.
		- As an example, he requests a `pong`-Event from the API.
		- This pong will be sent by the API asynchronously and be visible in the EventHandler
- All SDK entities and methods contain JavaDoc annotations.

### Build, run, and monitor
See Makefile for a reference of build targets.

The following endpoints are provided in the Example Agent.
```
# Liveness (agent is up)
http://localhost:8080/q/health/live

# Readiness (agent can connect to the amaise cloud)
http://localhost:8080/q/health/ready

# Prometheus metrics; we recommend setting up alerts on the hearbeat_* counters.
http://localhost:8080/q/metrics

```

&nbsp;
&nbsp;

---

&nbsp;
&nbsp;

## References
### Configuration and Deployment

All configurations can be set as environment variables by Quarkus configuration convention.
For a reference, see `application.properties`.