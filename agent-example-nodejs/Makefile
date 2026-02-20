## help: print this help message
help:
	@echo 'Usage:'
	@sed -n 's/^##//p' ${MAKEFILE_LIST} | column -t -s ':' | sed -e 's/^/ /'

## install: install dependencies
install:
	npm install

OPENAPI_URL ?= https://api.eu.amaise.com/doc/api-documentation/AGENT-SDK

## generate: fetch OpenAPI spec and generate TypeScript client
generate:
	wget -q -O openapi-agent.json "$(OPENAPI_URL)"
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

## dockerize: build Docker image
dockerize:
	docker build --tag amaise-agent-example-nodejs .

## validate: run CI smoke test
validate:
	npm run validate
