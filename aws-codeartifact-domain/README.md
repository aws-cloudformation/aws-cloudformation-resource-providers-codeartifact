# AWS::CodeArtifact::Domain

This package contains the handlers used to provision CodeArtifact Domains
as CloudFormation Resources.

#### CreateHandler.java
This has the code necessary when calling `CREATE` resource upon stack creation.
#### ReadHandler.java
This has the code necessary when calling `READ` resource. This is called when calling
`GetAtt` intrinsic function.
#### UpdateHandler.java
This has the code necessary when calling `UPDATE` resource. This is called during an update
stack operation. Updating a AWS::CodeArtifact::Domain would update ResourcePolicies on the domain.
#### ListHandler.java
This has the code necessary when calling `LIST` resources.
