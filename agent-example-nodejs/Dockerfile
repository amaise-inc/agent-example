# Stage 1: Build & type-check
FROM node:22-slim AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# Fetch OpenAPI spec during build (override with --build-arg OPENAPI_URL=...)
ARG OPENAPI_URL=https://api.eu.amaise.com/doc/api-documentation/AGENT-SDK
RUN node --input-type=module -e "import{writeFileSync}from'node:fs';const r=await fetch(process.env.OPENAPI_URL);if(!r.ok)throw new Error('HTTP '+r.status);writeFileSync('openapi-agent.json',await r.text())"

COPY openapi-ts.config.ts tsconfig.json vitest.config.ts ./
RUN npx openapi-ts
COPY lib/ lib/
COPY examples/ examples/
COPY ci/ ci/
RUN npm run typecheck
RUN npm test

# Stage 2: Runtime (production deps only)
FROM node:22-slim
RUN groupadd -r amaise && useradd --no-log-init -r -g amaise amaise
WORKDIR /app
COPY --from=builder /app/package.json /app/package-lock.json ./
RUN npm ci --omit=dev
COPY --from=builder /app/generated/ generated/
COPY --from=builder /app/lib/ lib/
COPY --from=builder /app/ci/ ci/
COPY assets/ assets/
USER amaise
ENTRYPOINT ["npx", "tsx", "ci/validate.ts"]
