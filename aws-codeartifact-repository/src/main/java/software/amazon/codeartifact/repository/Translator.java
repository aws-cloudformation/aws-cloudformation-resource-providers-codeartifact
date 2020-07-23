package software.amazon.codeartifact.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
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

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateRepositoryRequest translateToCreateRequest(final ResourceModel model) {
    return CreateRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .repository(model.getRepositoryName())
        .description(model.getDescription())
        .build();
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
        // TODO add external connections and upstreams to read response
        .arn(repositoryDescription.arn())
        .description(repositoryDescription.description())
        .administratorAccount(repositoryDescription.administratorAccount())
        .domainName(repositoryDescription.domainName())
        .domainOwner(repositoryDescription.domainOwner())
        .description(repositoryDescription.description())
        .repositoryName(repositoryDescription.name())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteRepositoryRequest translateToDeleteRequest(final ResourceModel model) {
    return  DeleteRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .repository(model.getRepositoryName())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static AwsRequest translateToFirstUpdateRequest(final ResourceModel model) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L45-L50
    return awsRequest;
  }

  /**
   * Request to update some other properties that could not be provisioned through first update request
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static AwsRequest translateToSecondUpdateRequest(final ResourceModel model) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    return awsRequest;
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

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
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
