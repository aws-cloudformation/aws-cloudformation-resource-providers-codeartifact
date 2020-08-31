# CloudFormation Resource Provider Package for AWS CodeArtifact

This is the CloudFormation Resource Provider Package for AWS CodeArtifact. These providers allow for CloudFormation stacks to provision AWS CodeArtifact domains and repositories.

## Resource providers

\> These resource providers are currently in open beta and are subject to changes

* CodeArtifact domain ([What's a domain?](https://docs.aws.amazon.com/codeartifact/latest/ug/codeartifact-concepts.html#welcome-concepts-domain))
* CodeArtifact repository ([What's a repository?](https://docs.aws.amazon.com/codeartifact/latest/ug/codeartifact-concepts.html#welcome-concepts-repository))

These resource providers can be tested by running the following commands through the AWS CLI for each of the Domain and Repository resources. The commands use a link to the Beta resource zip file stored in S3 to add these resources as private resource types to your AWS account.

\> As these resource providers are not part of the public AWS namespace, standard CloudFormation pricing applies after the 1,000 Free Tier handler operations each month. For more information on CloudFormation pricing, see https://aws.amazon.com/cloudformation/pricing/.

\> `us-east-1` is used as the region in the examples below, but any region supported by CodeArtifact can be used.

## Register a CodeArtifact domain resource provider with the AWS CLI

ADD EXAMPLE CLI COMMAND for domain
```
aws cloudformation register-type \
     --region us-east-1 \
     --type RESOURCE \
     --type-name "AWSdevToolsBeta::CodeArtifact::Domain" \
     --schema-handler-package "s3://codeartifact-cfn-beta/awsdevtoolsbeta-codeartifact-domain-1.0.zip"
```

## Register a CodeArtifact repository resource provider with the AWS CLI

ADD EXAMPLE CLI COMMAND for repository
```
aws cloudformation register-type \
     --region us-east-1 \
     --type RESOURCE \
     --type-name "AWSdevToolsBeta::CodeArtifact::Repository" \
     --schema-handler-package "s3://codeartifact-cfn-beta/awsdevtoolsbeta-codeartifact-repository-1.0.zip" 
```

## Sample CloudFormation templates

Sample templates can be found in the Sample-templates folder:
https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-codeartifact/tree/main/sample-templates

## Reference

### Domain reference

The domain resource allows the following parameters:

* DomainName - String
* EncryptionKey (Optional) - String
* PermissionsPolicyDocument (Optional)  - JSON

### Repository reference

The repository resource allows the following parameters:

* RepositoryName - String
* DomainName - String
* DomainOwner (Optional) - String
* Upstreams (Optional) - Array of Strings
* ExternalConnections (Optional)  - Array of Strings
* PermissionsPolicyDocument (Optional)  - JSON

## Feedback

To provide feedback please submit a GitHub issue ticket.

## Beta Releases:

### v1.0 Beta - 8/31

S3 Links:

* Domain: s3://codeartifact-cfn-beta/awsdevtoolsbeta-codeartifact-domain-1.0.zip
* Repository: s3://codeartifact-cfn-beta/awsdevtoolsbeta-codeartifact-repository-1.0.zip

Changes: Initial beta release of Domain and Repository resources.

## License

This project is licensed under the Apache-2.0 License.
