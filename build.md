
mvn clean install

docker build -t fhir-tie .

docker tag fhir-tie:latest 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:latest
docker tag fhir-tie:latest 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:1.3.12

docker push 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:latest

docker push 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:1.3.12
