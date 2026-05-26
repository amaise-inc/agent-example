## help: print this help message
help:
	@echo 'Usage:'
	@sed -n 's/^##//p' ${MAKEFILE_LIST} | column -t -s ':' | sed -e 's/^/ /'

## install: install dependencies
install:
	npm install

OPENAPI_URL ?= https://api.eu.amaise.com/doc/api-documentation/AGENT-SDK

## generate: fetch OpenAPI spec from live API and generate TypeScript client (customer workflow)
generate:
	curl -fsSL -o openapi-agent.json $(OPENAPI_URL)
	npm run generate

## generate-local: generate from local monorepo spec (internal/CI workflow)
generate-local:
	@test -f ../../../core/shared/openapi/openapi-agent.json || (echo "Error: local spec not found. Run 'make generate-openapi-json' in core/case-processor first." && exit 1)
	cp ../../../core/shared/openapi/openapi-agent.json openapi-agent.json
	npm run generate

## test: run unit tests
test:
	npm test

## format: format code with prettier
format:
	npm run format

## lint: type-check and lint code
lint:
	npm run lint

## run-legalcase-lifecycle: run legalcase-lifecycle example
run-legalcase-lifecycle:
	npm run legalcase-lifecycle

## run-sourcefile-processing: run sourcefile-processing example
run-sourcefile-processing:
	npm run sourcefile-processing

## run-dashboard-processing: run dashboard-processing example
run-dashboard-processing:
	npm run dashboard-processing

## dep-check: audit production dependencies for vulnerabilities
dep-check:
	npm audit --omit=dev

## verify: run linters and tests
verify:
	npm run verify

## dockerize: build Docker image
dockerize:
	docker build --tag amaise-agent-example-nodejs .

## validate: run CI smoke test
validate:
	npm run validate
