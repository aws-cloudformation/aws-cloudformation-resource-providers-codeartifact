package software.amazon.codeartifact.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DisassociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.RepositoryExternalConnectionInfo;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.UpdateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepository;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepositoryInfo;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {
  public static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateRepositoryRequest translateToCreateRequest(final ResourceModel model) {
    return CreateRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .upstreams(translateToUpstreamList(model))
        .repository(model.getRepositoryName())
        .description(model.getDescription())
        .build();
  }

  /**
   * Translates ResourceModel upstream array into UpstreamRepository List
   * @param model resource model
   * @return list of UpstreamRepository
   */
  static List<UpstreamRepository> translateToUpstreamList(final ResourceModel model) {
    if (model.getUpstreams() == null) {
      return null;
    }

    return model.getUpstreams()
        .stream()
        .map(upstream -> UpstreamRepository.builder()
            .repositoryName(upstream)
            .build()
        )
        .collect(Collectors.toList());
  }

  /**
   * Translates repositoryDescription to list of strings
   * @param repositoryDescription repo description
   * @return list of repository names
   */
  static List<String> translateToUpstreamsFromRepoDescription(
      final RepositoryDescription repositoryDescription
  ) {
    return streamOfOrEmpty(repositoryDescription.upstreams())
        .map(UpstreamRepositoryInfo::repositoryName)
        .collect(Collectors.toList());
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeRepositoryRequest translateToReadRequest(final ResourceModel model) {
    return DescribeRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .repository(model.getRepositoryName())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param describeRepositoryResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeRepositoryResponse describeRepositoryResponse) {
    RepositoryDescription repositoryDescription = describeRepositoryResponse.repository();
    return ResourceModel.builder()
        // TODO add external connections and policy document
        // https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-codeartifact/issues/18
        .arn(repositoryDescription.arn())
        .description(repositoryDescription.description())
        .administratorAccount(repositoryDescription.administratorAccount())
        .domainName(repositoryDescription.domainName())
        .upstreams(translateToUpstreamsFromRepoDescription(describeRepositoryResponse.repository()))
        .domainOwner(repositoryDescription.domainOwner())
        .description(repositoryDescription.description())
        .repositoryName(repositoryDescription.name())
        .build();
  }

  /**
   * Translates resource model into PutRepositoryPermissionsPolicyRequest
   * @param resourceModel resource model
   * @return PutRepositoryPermissionsPolicyRequest
   */
  public static PutRepositoryPermissionsPolicyRequest translatePutPermissionsPolicyRequest(
      ResourceModel resourceModel
  ) {
    try {
      return PutRepositoryPermissionsPolicyRequest.builder()
          .domain(resourceModel.getDomainName())
          .repository(resourceModel.getRepositoryName())
          .domainOwner(resourceModel.getDomainOwner())
          .policyDocument(MAPPER.writeValueAsString(resourceModel.getPolicyDocument()))
          .build();
    } catch (final JsonProcessingException e) {
        throw new CfnInvalidRequestException(e);
      }
  }

  /**
   * Translates resource model into DeleteRepositoryPermissionsPolicyRequest
   * @param resourceModel resource model
   * @return DeleteRepositoryPermissionsPolicyRequest
   */
  public static DeleteRepositoryPermissionsPolicyRequest translateDeletePermissionsPolicyRequest(
      ResourceModel resourceModel
  ) {
      return DeleteRepositoryPermissionsPolicyRequest.builder()
          .domain(resourceModel.getDomainName())
          .domainOwner(resourceModel.getDomainOwner())
          .repository(resourceModel.getRepositoryName())
          .build();
  }

  /**
   * Request to describe repository permissions policy
   * @param model resource model
   * @return awsRequest the aws service request to describe a policy
   */
  static GetRepositoryPermissionsPolicyRequest translateToGetRepositoryPermissionsPolicy(final ResourceModel model) {
    return GetRepositoryPermissionsPolicyRequest.builder()
        .domain(model.getDomainName())
        .repository(model.getRepositoryName())
        .domainOwner(model.getDomainOwner())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteRepositoryRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .repository(model.getRepositoryName())
        .build();
  }

  /**
   * Request to associate External Connection
   * @param model resource model
   * @param  externalConnectionToAdd external connection to associate
   * @return awsRequest the aws service request to modify a resource
   */
  static AssociateExternalConnectionRequest translateAssociateExternalConnectionsRequest(
      final ResourceModel model,
      final String externalConnectionToAdd
  ) {
    return AssociateExternalConnectionRequest.builder()
        .domain(model.getDomainName())
        .repository(model.getRepositoryName())
        .domainOwner(model.getDomainOwner())
        .externalConnection(externalConnectionToAdd)
        .build();
  }

  static Set<String> translateExternalConnectionFromDesiredResource(final ResourceModel model) {
    return streamOfOrEmpty(model.getExternalConnections())
        .collect(Collectors.toSet());
  }

  /**
   * Request to remove External Connection from Repository
   * @param model resource model
   * @param externalConnectionToRemove External Connections to remove
   * @return awsRequest the aws service request to modify a resource
   */
  static DisassociateExternalConnectionRequest translateDisassociateExternalConnectionsRequest(
      final ResourceModel model,
      final String externalConnectionToRemove
  ) {
    return DisassociateExternalConnectionRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .repository(model.getRepositoryName())
        .externalConnection(externalConnectionToRemove)
        .build();
  }


  public static Set<String> translateExternalConnectionFromRepoDescription(RepositoryDescription repository) {
    return streamOfOrEmpty(repository.externalConnections())
        .map(RepositoryExternalConnectionInfo::externalConnectionName)
        .collect(Collectors.toSet());
  }

  /**
   * Request to update Repository
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateRepositoryRequest translateToUpdateRepository(final ResourceModel model) {
    return UpdateRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .repository(model.getRepositoryName())
        .description(model.getDescription())
        .upstreams(translateToUpstreamList(model))
        .build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static AwsRequest translateToListRequest(final String nextToken) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L26-L31
    return awsRequest;
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param awsResponse the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final AwsResponse awsResponse) {
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L75-L82
    // TODO: construct models
    return streamOfOrEmpty(Lists.newArrayList())
        .map(resource -> ResourceModel.builder()
            // include only primary identifier
            .build())
        .collect(Collectors.toList());
  }

  public static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static void throwCfnException(final AwsServiceException exception, String operation, String repositoryName) {
    if (exception instanceof AccessDeniedException) {
      throw new CfnAccessDeniedException(operation, exception);
    }
    if (exception instanceof ConflictException) {
      throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, repositoryName);
    }
    if (exception instanceof ResourceNotFoundException) {
      throw new CfnNotFoundException(exception);
    }
    if (exception instanceof ServiceQuotaExceededException) {
      throw new CfnServiceLimitExceededException(ResourceModel.TYPE_NAME, repositoryName, exception);
    }
    if (exception instanceof ValidationException) {
      throw new CfnInvalidRequestException(exception);
    }
    if (exception instanceof InternalServerException) {
      throw new CfnServiceInternalErrorException(operation, exception);
    }
    throw new CfnGeneralServiceException(exception);
  }

}
