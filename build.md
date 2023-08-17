
mvn clean install

docker build -t fhir-tie .

Use AWS SSO

aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin 365027538941.dkr.ecr.eu-west-2.amazonaws.com

docker tag fhir-tie:latest 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:latest
docker tag fhir-tie:latest 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:6.6.6

docker push 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:latest

docker push 365027538941.dkr.ecr.eu-west-2.amazonaws.com/fhir-tie:6.6.6
