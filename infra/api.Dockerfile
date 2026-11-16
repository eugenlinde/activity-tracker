FROM eclipse-temurin:26-jre-alpine

LABEL org.opencontainers.image.source=https://github.com/eugenlinde/activity-tracker

RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /data/media \
    && chown app:app /data/media

WORKDIR /app
COPY build/libs/*.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
