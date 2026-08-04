# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile (multi-stage Maven -> JRE 25 slim)
#
# Stage 1: build the JAR with Temurin JDK 25 + Maven.
# Stage 2: copy ONLY the JAR onto a slim JRE image.
#
# The reactor has 6 child modules (common, domain, repository, service, api,
# recon-audit-starter) but only `api` and `recon-audit-starter` carry real
# source — `api` pulls in the actual application code from the sibling
# `src/` tree via build-helper-maven-plugin's <add-source>. Everything is
# built with `-pl api -am` so the runnable jar lands in api/target/.
#
# Windows fix: sed strips \r (CRLF) from mvnw before executing it.
# Without this, Windows git checkout corrupts the shebang line and
# Docker reports "./mvnw: not found" (exit 127) on Linux.
# ============================================================================

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY common/pom.xml common/pom.xml
COPY domain/pom.xml domain/pom.xml
COPY repository/pom.xml repository/pom.xml
COPY service/pom.xml service/pom.xml
COPY api/pom.xml api/pom.xml
COPY recon-audit-starter/pom.xml recon-audit-starter/pom.xml
COPY checkstyle.xml checkstyle.xml
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q -B dependency:go-offline || true
COPY src src
COPY api/src api/src
COPY recon-audit-starter/src recon-audit-starter/src
RUN ./mvnw -q -B -DskipTests package -pl api -am && \
    mv api/target/reconx-api-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S reconx && adduser -S reconx -G reconx
USER reconx
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
