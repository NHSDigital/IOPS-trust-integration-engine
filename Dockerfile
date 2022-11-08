FROM openjdk:11.0.8

VOLUME /tmp

ENV JAVA_OPTS="-Xms128m -Xmx1024m"

ADD target/fhir-tie.jar fhir-tie.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/fhir-tie.jar"]
