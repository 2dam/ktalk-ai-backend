FROM node:22-bookworm AS frontend-build
WORKDIR /app/ktalk-frontend
COPY ktalk-frontend/package*.json ./
RUN npm ci
COPY ktalk-frontend/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /app
COPY . .
COPY --from=frontend-build /app/ktalk-frontend/dist ./ktalk-frontend/dist
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test -PskipFrontendBuild=true

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend-build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
