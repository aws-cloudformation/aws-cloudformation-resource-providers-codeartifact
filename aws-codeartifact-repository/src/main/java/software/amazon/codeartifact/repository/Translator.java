package software.amazon.codeartifact.repository;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import software.amazon.awssdk.services.codeartifact.model.ListRepositoriesRequest;
import software.amazon.awssdk.services.codeartifact.model.ListRepositoriesResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.RepositoryExternalConnectionInfo;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.Tag;
import software.amazon.awssdk.services.codeartifact.model.TagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UntagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepository;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepositoryInfo;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.codeartifact.repository.ResourceModel.ResourceModelBuilder;
import software.amazon.awssdk.services.codeartifact.model.Tag;
import software.amazon.awssdk.utils.CollectionUtils;

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
  static CreateRepositoryRequest translateToCreateRequest(final ResourceModel model, final Map<String, String> tags) {
    return CreateRepositoryRequest.builder()
        .domain(model.getDomainName())
        .domainOwner(model.getDomainOwner())
        .tags(translateTagsToSdk(tags))
        .upstreams(translateToUpstreamList(model))
        .repository(model.getRepositoryName())
        .description(model.getDescription())
        .build();
  }

  static Set<Tag> translateTagsToSdk(final Map<String, String> tags) {
    if (tags == null) {
      return Collections.emptySet();
    }

    return tags.entrySet()
        .stream()
        .map(tag ->
            Tag.builder()
                .key(tag.getKey())
                .value(tag.getValue())
                .build()
        )
        .collect(Collectors.toSet());
  }

  static UntagResourceRequest untagResourceRequest(
      ResourceHandlerRequest<ResourceModel> request,
      List<Tag> tagsToRemove,
      String repositoryName,
      String domainName,
      String domainOwner
  ) {

    String arn = ArnUtils.repoArn(
        request.getAwsPartition(),
        request.getRegion(),
        domainOwner,
        domainName,
        repositoryName)
        .arn();

    return UntagResourceRequest.builder()
        .resourceArn(arn)
        .tagKeys(
            tagsToRemove.stream()
                .map(Tag::key)
                .collect(Collectors.toList())
        )
        .build();
  }

  static TagResourceRequest tagResourceRequest(
      ResourceHandlerRequest<ResourceModel> request,
      List<Tag> tagsToAdd,
      String repositoryName,
      String domainName,
      String domainOwner
  ) {

    String arn = ArnUtils.repoArn(
        request.getAwsPartition(),
        request.getRegion(),
        domainOwner,
        domainName,
        repositoryName)
        .arn();

    return TagResourceRequest.builder()
        .resourceArn(arn)
        .tags(tagsToAdd)
        .build();
  }

  static ListTagsForResourceRequest translateToListTagsRequest(
      final ResourceModel model
  ) {
    return ListTagsForResourceRequest
        .builder()
        // arn has been populated by describeRepositoryCall
        .resourceArn(model.getArn())
        .build();
  }

  public static List<software.amazon.codeartifact.repository.Tag> fromListTagsResponse(final List<Tag> tags) {
    if (CollectionUtils.isNullOrEmpty(tags)) {
      return null;
    }

    return tags.stream()
        .map(codeArtifactTag -> software.amazon.codeartifact.repository.Tag.builder()
            .key(codeArtifactTag.key())
            .value(codeArtifactTag.value())
            .build())
        .collect(Collectors.toList());
  }

  /**
   * Translates ResourceModel upstream array into UpstreamRepository List
   * @param model resource model
   * @return list of UpstreamRepository
   */
  static List<UpstreamRepository> translateToUpstreamList(final ResourceModel model) {
    if (model.getUpstreams() == null) {
      // this will remove upstreams
      return Collections.emptyList();
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
   * Translates repositoryDescription to list of strings
   * @param repositoryDescription repo description
   * @return list of externalConnectionName strings
   */
  static List<String> translateToExternalConnectionsFromRepositoryExternalConnectionInfo(
      final RepositoryDescription repositoryDescription
  ) {
    return streamOfOrEmpty(repositoryDescription.externalConnections())
        .map(RepositoryExternalConnectionInfo::externalConnectionName)
        .collect(Collectors.toList());
  }


  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeRepositoryRequest translateToReadRequest(final ResourceModel model) {
    String domainName = model.getDomainName();
    String domainOwner = model.getDomainOwner();
    String repositoryName = model.getRepositoryName();

    if (model.getArn() != null && domainName == null && domainOwner == null && repositoryName == null) {
        // This case happens when ReadHandler is called using *only* the primaryIdentifier (Arn)
        RepositoryArn repositoryArn = ArnUtils.fromArn(model.getArn());

        domainName = repositoryArn.domainName();
        domainOwner = repositoryArn.owner();
        repositoryName = repositoryArn.repoName();
    }

    return DescribeRepositoryRequest.builder()
        .domain(domainName)
        .domainOwner(domainOwner)
        .repository(repositoryName)
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param describeRepositoryResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(
      final DescribeRepositoryResponse describeRepositoryResponse
  ) {

    RepositoryDescription repositoryDescription = describeRepositoryResponse.repository();
    ResourceModelBuilder resourceModelBuilder = ResourceModel.builder()
        .arn(repositoryDescription.arn())
        .description(repositoryDescription.description())
        .domainName(repositoryDescription.domainName())
        .domainOwner(repositoryDescription.domainOwner())
        .description(repositoryDescription.description())
        .repositoryName(repositoryDescription.name())
        .name(repositoryDescription.name());

    if (!CollectionUtils.isNullOrEmpty(repositoryDescription.upstreams())) {
      resourceModelBuilder.upstreams(translateToUpstreamsFromRepoDescription(repositoryDescription));
    }

    if (!CollectionUtils.isNullOrEmpty(repositoryDescription.externalConnections())) {
      resourceModelBuilder.externalConnections(
          translateToExternalConnectionsFromRepositoryExternalConnectionInfo(repositoryDescription)
      );
    }

    return resourceModelBuilder.build();
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
          .policyDocument(MAPPER.writeValueAsString(resourceModel.getPermissionsPolicyDocument()))
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

    String domainName = model.getDomainName();
    String domainOwner = model.getDomainOwner();
    String repositoryName = model.getRepositoryName();

    if (model.getArn() != null && domainName == null && domainOwner == null && repositoryName == null) {
      // This case happens when ReadHandler is called using *only* the primaryIdentifier (Arn)
      RepositoryArn repositoryArn = ArnUtils.fromArn(model.getArn());

      domainName = repositoryArn.domainName();
      domainOwner = repositoryArn.owner();
      repositoryName = repositoryArn.repoName();
    }

    return GetRepositoryPermissionsPolicyRequest.builder()
        .domain(domainName)
        .repository(repositoryName)
        .domainOwner(domainOwner)
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteRepositoryRequest translateToDeleteRequest(final ResourceModel model) {
    String domainName = model.getDomainName();
    String domainOwner = model.getDomainOwner();
    String repositoryName = model.getRepositoryName();

    if (model.getArn() != null && domainName == null && domainOwner == null && repositoryName == null) {
      // this happens when Ref or GetAtt are called
      RepositoryArn repositoryArn = ArnUtils.fromArn(model.getArn());

      domainName = repositoryArn.domainName();
      domainOwner = repositoryArn.owner();
      repositoryName = repositoryArn.repoName();
    }

    return DeleteRepositoryRequest.builder()
        .domain(domainName)
        .domainOwner(domainOwner)
        .repository(repositoryName)
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

  /**
   * Request to update Repository
   * @param desiredModel desired resource model
   * @param prevModel previous resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateRepositoryRequest translateToUpdateRepository(
      final ResourceModel desiredModel,
      final ResourceModel prevModel
  ) {
    UpdateRepositoryRequest.Builder builder = UpdateRepositoryRequest.builder()
        .domain(desiredModel.getDomainName())
        .domainOwner(desiredModel.getDomainOwner())
        .repository(desiredModel.getRepositoryName())
        .description(desiredModel.getDescription());

    if (!ComparisonUtils.upstreamsAreEqual(desiredModel, prevModel)) {
      // There is either upstreams to remove or upstreams to delete. This is here because adding an external connection
      // and trying to call .upstreams(emptyList) will cause a ValidationException with a repository with external
      // connections, so we skip the call if there is nothing to update.
      builder.upstreams(translateToUpstreamList(desiredModel));
    }
    return builder.build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListRepositoriesRequest translateToListRequest(final String nextToken) {
    return ListRepositoriesRequest.builder()
        .nextToken(nextToken)
        .maxResults(Constants.MAX_ITEMS)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param awsResponse the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final ListRepositoriesResponse awsResponse) {
    return streamOfOrEmpty(awsResponse.repositories())
        .map(repo -> ResourceModel.builder()
            .arn(repo.arn())
            .build())
        .collect(Collectors.toList());
  }

  public static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  public static Map<String, Object> deserializePolicy(final String policy) {
    if (StringUtils.isNullOrEmpty(policy)) {
      return null;
    }
    try {
      return MAPPER.readValue(policy, new TypeReference<HashMap<String,Object>>() {});
    } catch (final IOException e) {
      throw new CfnInternalFailureException(e);
    }
  }

  static void throwCfnException(final AwsServiceException exception, String operation, String repositoryName) {
    if (exception instanceof AccessDeniedException) {
      throw new CfnAccessDeniedException(exception);
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
