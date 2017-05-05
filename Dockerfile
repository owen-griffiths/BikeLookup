# Base Alpine Linux based image with OpenJDK JRE only
FROM openjdk:8-jre-alpine
# copy application WAR (with libraries inside)
COPY target/*.jar /
COPY target/dependency-jars/*.jar /

ENTRYPOINT ["/usr/bin/java", "-cp", "/*", "com.omgcodes.bikelookup.App"]
#CMD ["/bin/sh"]