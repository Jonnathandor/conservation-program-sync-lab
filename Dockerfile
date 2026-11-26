FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/index.html frontend/tsconfig.json frontend/vite.config.ts ./
COPY frontend/src/ ./src/
RUN npm run build

FROM eclipse-temurin:17-jdk AS backend
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ ./gradle/
COPY src/main/ ./src/main/
COPY --from=frontend /build/frontend/dist/ ./frontend/dist/
RUN ./gradlew --no-daemon bootJar --console=plain \
    && set -- build/libs/*.jar \
    && test "$#" -eq 1 \
    && cp "$1" /build/app.jar \
    && test -s /build/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /build/app.jar ./app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
