# Build stage
# Pinned to a specific patch version (not "3.9-eclipse-temurin-21") for reproducible builds.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# -B (batch mode) + -ntp (no transfer progress): avoids interactive prompts, smaller/cleaner logs.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:resolve dependency:resolve-plugins
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

# Runtime stage
# JRE only (no JDK/Maven) -> smaller image, no build tools in production.
# Pinned patch version, same reasoning as the build stage.
FROM eclipse-temurin:21.0.5_11-jre
WORKDIR /app
# Non-root user: if the app is compromised, the attacker doesn't get root inside the container.
RUN addgroup --system spring && adduser --system --ingroup spring spring
# pom.xml sets <finalName>app</finalName>, so this is always exactly "app.jar" -
# avoids a wildcard (*.jar) matching both app.jar and original-app.jar (Spring Boot repackage artifact).
# --chown sets ownership during copy, avoiding a separate RUN chown layer.
COPY --from=build --chown=spring:spring /app/target/app.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]