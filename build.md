
### AWS ECR

`aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin 365027538941.dkr.ecr.eu-west-2.amazonaws.com`

`mvn clean install -P dockerBuild,awsRelease`
