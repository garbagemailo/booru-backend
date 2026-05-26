FROM gradle:8.11.1-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/install/booru-backend /app/booru-backend
EXPOSE 8080
CMD ["/app/booru-backend/bin/booru-backend"]
