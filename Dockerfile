FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474
RUN apk upgrade --no-cache \
 && addgroup -S -g 10001 fraud \
 && adduser -S -u 10001 -G fraud -H fraud
WORKDIR /app
COPY --from=build /build/target/fraudguard-*.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=120s \
  CMD ["wget", "-q", "-O", "/dev/null", "http://localhost:8081/actuator/health"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
