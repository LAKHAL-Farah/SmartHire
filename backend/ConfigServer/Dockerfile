FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -pl ConfigServer -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r appuser && useradd -r -g appuser appuser
COPY --from=build /workspace/ConfigServer/target/*.jar /app/app.jar
COPY --from=build /workspace/ConfigServer/config-repo /app/config-repo
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8888
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s CMD curl -f http://localhost:8888/actuator/health || exit 1
USER appuser
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
