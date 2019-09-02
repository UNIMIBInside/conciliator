FROM openjdk:8-jre-alpine

ENV JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError -Xmx512m -Xmx256m"

ADD *.jar /app.jar

RUN sh -c 'touch /app.jar'
CMD java -Djava.security.egd=file:/dev/./urandom -jar /app.jar