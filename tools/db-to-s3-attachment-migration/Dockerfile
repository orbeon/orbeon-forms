FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="Orbeon Forms DB-to-S3 Attachment Migration" \
      org.opencontainers.image.vendor="Orbeon, Inc." \
      org.opencontainers.image.url="https://www.orbeon.com"

COPY target/scala-2.13/db-to-s3-attachment-migration-*-shrunk.jar /opt/migration.jar

# Mount external JDBC driver JARs (e.g. DB2, MySQL, Oracle, SQL Server) in /opt/drivers/
RUN mkdir -p /opt/drivers

ENTRYPOINT ["java", "-cp", "/opt/migration.jar:/opt/drivers/*", "org.orbeon.tools.s3migration.Main"]
