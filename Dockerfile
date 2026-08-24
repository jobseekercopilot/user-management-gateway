FROM maven:3.9-eclipse-temurin-17@sha256:e8ef73dbd33b69fe497fd96b3bbbd85aff84ac4c564a5784ab02ad941b32c12b AS build

WORKDIR /app

COPY pom.xml .
COPY README.md .
COPY docs ./docs
COPY src ./src

RUN mvn -B clean verify

FROM eclipse-temurin:17-jre-alpine@sha256:90b7615cb81e3a75f69124fb480e48981c7d56dbc9f32c614d789d3a1c3e32fe

WORKDIR /app

RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

COPY --from=build --chown=10001:10001 /app/target/user-management-gateway-1.0.0.jar app.jar
COPY --chown=10001:10001 scripts/container-healthcheck.sh /usr/local/bin/container-healthcheck

EXPOSE 8083

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD ["/usr/local/bin/container-healthcheck"]

ENTRYPOINT ["java", "-jar", "app.jar"]
