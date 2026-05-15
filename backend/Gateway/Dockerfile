FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -pl Gateway -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r appuser && useradd -r -g appuser appuser
COPY --from=build /workspace/Gateway/target/*.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8887
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s CMD curl -f http://localhost:8887/actuator/health || exit 1
USER appuser
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
