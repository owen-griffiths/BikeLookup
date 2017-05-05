# Base Alpine Linux based image with OpenJDK JRE only
FROM openjdk:8-jre-alpine
# copy application WAR (with libraries inside)
COPY target/*.jar /
COPY target/dependency-jars/*.jar /
# specify default command
# java -cp "target\dependency-jars\*;target\hello-maven-1.0-SNAPSHOT.jar" App
CMD ["/usr/bin/java", "-cp", "/*", "App"]