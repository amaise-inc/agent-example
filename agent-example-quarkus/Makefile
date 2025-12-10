SHELL:=/bin/bash

DOCKER_TAG=legali-agent-example-quarkus

ifeq ($(OS),Windows_NT)
    maven_cmd := .\mvnw.cmd
else
    maven_cmd := ./mvnw
endif

## help: print this help message
help:
	@echo 'Usage:'
	@sed -n 's/^##//p' ${MAKEFILE_LIST} | column -t -s ':' |  sed -e 's/^/ /'

## clean: cleans the build directory
clean:
	${maven_cmd} clean

## install: mvn install
install:
	${maven_cmd} install -DskipTests

## build: build the agent
build:
	@${maven_cmd} -B dependency:go-offline
	@${maven_cmd} package -DskipTests

## format: run spotless google java formatter
format:
	@${maven_cmd} spotless:apply

## lint: run verify and skip tests
lint:
	${maven_cmd} -B spotless:check
	${maven_cmd} -B spotbugs:check
	${maven_cmd} -B checkstyle:check
	${maven_cmd} -B org.owasp:dependency-check-maven:check
	${maven_cmd} -B license:add-third-party
	! cat target/generated-sources/license/THIRD-PARTY.txt | egrep "AGPL|GPL3|SSPL"

## test: run unit tests
test:
	@${maven_cmd} test

## verify: run unit-, integration tests and linters
verify:
	@${maven_cmd} verify

# show-updates: show dependency updates
show-updates:
	${maven_cmd} versions:display-dependency-updates
	${maven_cmd} versions:display-plugin-updates

# update-deps-patch: automatically update dependencies to next available patch versions
update-deps-patch:
	${maven_cmd} versions:use-next-patches

# update-deps-releases: automatically update dependencies to next available release versions
update-deps-releases:
	${maven_cmd} versions:use-next-releases

# dep-check: check dependencies
dep-check:
	${maven_cmd} org.owasp:dependency-check-maven:check

## dockerize: create agent docker image
dockerize:
	@docker build -f src/main/docker/Dockerfile.jvm -t "$(DOCKER_TAG)" .

## run: run docker image
run: dockerize
	@docker run -e LEGALI_API_URL -e LEGALI_CLIENT_SECRET -t "$(DOCKER_TAG)"
