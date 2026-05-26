# Security Policy

## Supported Versions

The amaise Agent Examples and Agent SDK follow a rolling release model. Security patches are applied to the latest release only.

| Version        | Supported          |
| -------------- | ------------------ |
| Latest         | :white_check_mark: |
| Older releases | :x:                |

We recommend always using the latest version. See [releases](https://github.com/amaise-inc/agent-example/releases) for the current version.

---

## Reporting a Vulnerability

We take the security of the amaise platform and its integrations seriously. Our platform processes sensitive legal and insurance documents under strict regulatory requirements including Swiss nDSG, EU GDPR, and professional secrecy obligations (Art. 321 StGB).

**Please do not report security vulnerabilities via public GitHub issues.**

### How to Report

Send your report to: **security@amaise.com**

Include the following:

- Clear description of the vulnerability and its potential impact
- Steps to reproduce (proof of concept, screenshots, or logs)
- Affected component, endpoint, or version
- Suggested severity (Critical / High / Medium / Low)

You may encrypt your report using our PGP key (available on request).

### Response Timeline

| Milestone           | Timeframe          |
| ------------------- | ------------------ |
| Acknowledgement     | 48 hours           |
| Triage & assessment | 5 business days    |
| Status update       | 10 business days   |
| Critical patch      | 72 hours           |
| High severity patch | 14 days            |
| Medium patch        | 90 days            |
| Low patch           | Next release cycle |

### Scope

**In scope:**

- amaise Agent SDK (`agent-sdk`)
- Agent example applications (Spring Boot, Quarkus, Node.js/TypeScript)
- OAuth 2.0 authentication flows and credential handling
- Agent API endpoints (`/agents/v1/*`)
- File transfer via presigned URLs
- Event subscription and processing

**Out of scope:**

- The amaise SaaS platform itself (report via the same email, handled separately)
- Third-party dependencies (report directly to the respective maintainers)
- Third-party services (AWS, Auth0, Azure OpenAI) — report to the respective vendor
- Denial-of-service attacks
- Social engineering
- Findings from automated scanners without demonstrated exploitability

### Safe Harbor

- We will not pursue legal action against researchers acting in good faith
- We will credit researchers in our release notes (unless you prefer anonymity)
- We will not share your personal information without consent

We accept reports in **English** and **German**.

---

## Security Best Practices for Integrators

When building integrations with the amaise Agent SDK or REST API, follow these guidelines:

### Credential Management

- **Never** commit client secrets or API keys to source control
- Store credentials in a secrets manager or encrypted environment variables
- Rotate client credentials periodically via the amaise workspace UI (Settings > Integration)
- Use separate credentials per environment (development, staging, production)

### Authentication

- Use **OAuth 2.0 Client Credentials** grant exclusively — never store user passwords
- Access tokens are short-lived; always use the token refresh flow
- Validate TLS certificates on all connections to amaise endpoints

### Network Security

- All communication with amaise APIs uses **TLS 1.2+** (TLS 1.3 preferred)
- Allowlist the required amaise domains in your firewall:
  - API: `https://*.agents.{region}.amaise.com`
  - Auth: `https://auth.{region}.amaise.com`
  - File transfer: `https://data.{region}.amaise.com`, `https://upload.{region}.amaise.com`
- Presigned URLs for file transfer expire after **5 minutes** — do not cache or log them

### Data Handling

- Treat all document content and case metadata as **confidential**
- Do not log document content, presigned URLs, or access tokens
- Implement appropriate data retention policies in your integration
- Delete local copies of documents when no longer needed

### Event Processing

- Events are retained for **3 days** — ensure your integration processes them promptly
- Implement idempotent event handlers (events may be delivered more than once)
- Acknowledge events after successful processing

---

## Platform Security Overview

The amaise cloud platform that this SDK connects to maintains the following security posture:

- **Certifications:** ISO 27001:2022, SOC 2 Type II
- **Compliance:** Swiss nDSG, EU GDPR, NIST Cybersecurity Framework
- **Encryption at rest:** AES-256 with per-tenant customer-managed keys (AWS KMS)
- **Encryption in transit:** TLS 1.2+/1.3 enforced on all endpoints
- **Tenant isolation:** Row-level data isolation, per-tenant encryption keys, separate AWS accounts per region
- **Authentication:** OAuth 2.0/OIDC via Auth0 with enforced MFA
- **Infrastructure:** AWS WAFv2, GuardDuty, CloudTrail, 24/7 monitoring
- **Disaster recovery:** RPO ~5 minutes, RTO ~30 minutes
- **Penetration testing:** Annual third-party assessments (reports available under NDA)
- **AI data handling:** Customer data is never used for model training

For detailed platform security documentation, contact your amaise account representative or email security@amaise.com.

---

_amaise AG, Bern, Switzerland_
