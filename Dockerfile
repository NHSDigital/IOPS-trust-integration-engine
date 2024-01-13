FROM openjdk:23

VOLUME /tmp

ENV JAVA_OPTS="-Xms128m -Xmx2048m"

ADD target/fhir-tie.jar fhir-tie.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/fhir-tie.jar"]
