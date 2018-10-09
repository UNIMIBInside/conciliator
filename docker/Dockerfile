FROM openjdk:8-jre-alpine

ENV JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError -Xmx512m -Xmx256m"

ADD target/*.jar /app.jar

RUN sh -c 'touch /app.jar'
EXPOSE 8080
CMD java -Djava.security.egd=file:/dev/./urandom -jar /app.jar