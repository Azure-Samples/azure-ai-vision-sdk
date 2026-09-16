# Runtime image for the Spring Boot sample. The jar is built beforehand
# (`mvn -f ../pom.xml -DskipTests package`) so the attestation library and app
# are built together from the local checkout, not from a published artifact.
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/face-liveness-attestation-backend-sample.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
