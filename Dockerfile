FROM maven:3.9-eclipse-temurin-17@sha256:e8ef73dbd33b69fe497fd96b3bbbd85aff84ac4c564a5784ab02ad941b32c12b AS build

WORKDIR /app

COPY pom.xml .
COPY README.md .
COPY docs ./docs
COPY src ./src

RUN mvn -B clean verify

FROM eclipse-temurin:17-jre-alpine@sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57

WORKDIR /app

RUN apk add --no-cache --upgrade \
        libexpat=2.8.3-r0 \
        p11-kit=0.26.2-r0 \
        p11-kit-trust=0.26.2-r0 \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

COPY --from=build --chown=10001:10001 /app/target/user-management-gateway-1.0.0.jar app.jar
COPY --chown=10001:10001 scripts/container-healthcheck.sh /usr/local/bin/container-healthcheck

EXPOSE 8083

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD ["/usr/local/bin/container-healthcheck"]

ENTRYPOINT ["java", "-jar", "app.jar"]
