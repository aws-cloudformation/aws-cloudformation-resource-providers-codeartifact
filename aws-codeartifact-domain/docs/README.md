# AWSdevToolsBeta::CodeArtifact::Domain

The resource schema to create a CodeArtifact domain.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWSdevToolsBeta::CodeArtifact::Domain",
    "Properties" : {
        "<a href="#domainname" title="DomainName">DomainName</a>" : <i>String</i>,
        "<a href="#encryptionkey" title="EncryptionKey">EncryptionKey</a>" : <i>String</i>,
        "<a href="#status" title="Status">Status</a>" : <i>String</i>,
        "<a href="#permissionspolicydocument" title="PermissionsPolicyDocument">PermissionsPolicyDocument</a>" : <i>Map</i>,
    }
}
</pre>

### YAML

<pre>
Type: AWSdevToolsBeta::CodeArtifact::Domain
Properties:
    <a href="#domainname" title="DomainName">DomainName</a>: <i>String</i>
    <a href="#encryptionkey" title="EncryptionKey">EncryptionKey</a>: <i>String</i>
    <a href="#status" title="Status">Status</a>: <i>String</i>
    <a href="#permissionspolicydocument" title="PermissionsPolicyDocument">PermissionsPolicyDocument</a>: <i>Map</i>
</pre>

## Properties

#### DomainName

The name of the domain.

_Required_: Yes

_Type_: String

_Minimum_: <code>2</code>

_Maximum_: <code>50</code>

_Pattern_: <code>^([a-z][a-z0-9\-]{0,48}[a-z0-9])$</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### EncryptionKey

The ARN of an AWS Key Management Service (AWS KMS) key associated with a domain.

_Required_: No

_Type_: String

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Status

The current status of a domain.

_Required_: No

_Type_: String

_Allowed Values_: <code>Active</code> | <code>Deleted</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### PermissionsPolicyDocument

The access control resource policy on the provided domain.

_Required_: No

_Type_: Map

_Minimum_: <code>2</code>

_Maximum_: <code>5120</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the Arn.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### Arn

The ARN of the domain.

#### CreatedTime

Timestamp of when the domain was created.

#### RepositoryCount

The number of repositories in the domain.

#### AssetSizeBytes

The total size of all assets in the domain.

#### DomainOwner

The 12-digit account ID of the AWS account that owns the domain.

