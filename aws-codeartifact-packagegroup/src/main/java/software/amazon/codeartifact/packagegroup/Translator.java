package software.amazon.codeartifact.packagegroup;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.collect.Sets;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreatePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DeletePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListPackageGroupsRequest;
import software.amazon.awssdk.services.codeartifact.model.ListPackageGroupsResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupAllowedRepository;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupDescription;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionMode;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionType;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.TagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UntagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupOriginConfigurationRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.codeartifact.packagegroup.ResourceModel.ResourceModelBuilder;
import software.amazon.awssdk.services.codeartifact.model.Tag;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_ALLOWED_REPO_UPDATES_IN_ONE_REQUEST = 100;

    /**
     * Request to create a resource
     * @param model resource model
     * @return awsRequest the aws service request to create a resource
     */
    static CreatePackageGroupRequest translateToCreateRequest(final ResourceModel model, final Map<String, String> tags) {
        return CreatePackageGroupRequest.builder()
            .domain(model.getDomainName())
            .domainOwner(model.getDomainOwner())
            .packageGroup(model.getPattern())
            .contactInfo(model.getContactInfo())
            .description(model.getDescription())
            .tags(translateTagsToSdk(tags))
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

    static Map<String, String> translateCfnModelToTags(final List<software.amazon.codeartifact.packagegroup.Tag> tags) {
        if (CollectionUtils.isNullOrEmpty(tags)) {
            return Collections.emptyMap();
        }

        return tags.stream().collect(Collectors.toMap(
            software.amazon.codeartifact.packagegroup.Tag::getKey,
            software.amazon.codeartifact.packagegroup.Tag::getValue
        ));
    }

    /**
     * Request to read a resource
     * @param model resource model
     * @return awsRequest the aws service request to describe a resource
     */
    static DescribePackageGroupRequest translateToReadRequest(final ResourceModel model) {
        String domainName = model.getDomainName();
        String domainOwner = model.getDomainOwner();
        String packageGroupPattern = model.getPattern();

        if (model.getArn() != null && domainName == null && domainOwner == null && packageGroupPattern == null) {
            // This case happens when ReadHandler is called using *only* the primaryIdentifier (Arn)
            PackageGroupArn packageGroupArn = PackageGroupArn.fromArn(model.getArn());

            domainName = packageGroupArn.domainName();
            domainOwner = packageGroupArn.owner();
            packageGroupPattern = packageGroupArn.packageGroupName();
        }

        return DescribePackageGroupRequest.builder()
            .domain(domainName)
            .domainOwner(domainOwner)
            .packageGroup(packageGroupPattern)
            .build();
    }

    public static List<software.amazon.codeartifact.packagegroup.Tag> fromListTagsResponse(final List<Tag> tags) {
        if (CollectionUtils.isNullOrEmpty(tags)) {
            return null;
        }

        return tags.stream()
            .map(codeArtifactTag -> software.amazon.codeartifact.packagegroup.Tag.builder()
                .key(codeArtifactTag.key())
                .value(codeArtifactTag.value())
                .build()
            ).collect(Collectors.toList());
    }

    /**
     * Translates resource object from sdk into a resource model
     * @param awsResponse the aws service describe resource response
     */
    static ResourceModel translateFromReadResponse(
        final DescribePackageGroupResponse awsResponse
    ) {
        PackageGroupDescription packageGroupDescription = awsResponse.packageGroup();
        ResourceModelBuilder resourceModelBuilder = ResourceModel.builder()
            .domainName(packageGroupDescription.domainName())
            .domainOwner(packageGroupDescription.domainOwner())
            .arn(packageGroupDescription.arn())
            .description(packageGroupDescription.description())
            .contactInfo(packageGroupDescription.contactInfo())
            .pattern(packageGroupDescription.pattern());

        if(!CollectionUtils.isNullOrEmpty(packageGroupDescription.originConfiguration().restrictions())) {
            resourceModelBuilder.originConfiguration(translateToOriginControlFromResponse(packageGroupDescription));
        }

        return resourceModelBuilder.build();
    }

    static ListAllowedRepositoriesForGroupRequest translateToListAllowedReposRequest(
        final String nextToken,
        final PackageGroupOriginRestrictionType type,
        final ResourceModel model
    ) {
        return ListAllowedRepositoriesForGroupRequest.builder()
            .domain(model.getDomainName())
            .packageGroup(model.getPattern())
            .originRestrictionType(type)
            .maxResults(Constants.MAX_ITEMS)
            .nextToken(nextToken)
            .build();
    }

    static ResourceModel translateFromListAllowedReposResponse(
        ListAllowedRepositoriesForGroupResponse response,
        PackageGroupOriginRestrictionType type,
        ResourceModel model
    ) {
        List<String> repoNames = response.allowedRepositories();
        switch (type) {
            case PUBLISH:
                if(isAllowSpecificRepoMode(model.getOriginConfiguration().getRestrictions().getPublish()))
                    model.getOriginConfiguration().getRestrictions().getPublish().setRepositories(repoNames);
                return model;
            case EXTERNAL_UPSTREAM:
                if(isAllowSpecificRepoMode(model.getOriginConfiguration().getRestrictions().getExternalUpstream()))
                    model.getOriginConfiguration().getRestrictions().getExternalUpstream().setRepositories(repoNames);
                return model;
            case INTERNAL_UPSTREAM:
                if(isAllowSpecificRepoMode(model.getOriginConfiguration().getRestrictions().getInternalUpstream()))
                    model.getOriginConfiguration().getRestrictions().getInternalUpstream().setRepositories(repoNames);
                return model;
            default:
                throw new CfnServiceInternalErrorException("Unknown restriction type " + type.name());
        }
    }

    private static boolean isAllowSpecificRepoMode(RestrictionType type) {
        return Objects.equals(
            type.getRestrictionMode(),
            PackageGroupOriginRestrictionMode.ALLOW_SPECIFIC_REPOSITORIES.toString());
    }

    static OriginConfiguration translateToOriginControlFromResponse(PackageGroupDescription description) {
        String publishMode = description.originConfiguration().restrictions()
                .get(PackageGroupOriginRestrictionType.PUBLISH).modeAsString();
        String externalMode = description.originConfiguration().restrictions()
                .get(PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM).modeAsString();
        String internalMode = description.originConfiguration().restrictions()
                .get(PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM).modeAsString();

        return OriginConfiguration.builder()
            .restrictions(Restrictions.builder()
                .publish(RestrictionType.builder()
                    .restrictionMode(publishMode)
                    .build())
                .externalUpstream(RestrictionType.builder()
                    .restrictionMode(externalMode)
                    .build())
                .internalUpstream(RestrictionType.builder()
                    .restrictionMode(internalMode)
                    .build())
                .build()
            ).build();
    }

    /**
     * Request to update PackageGroup metadata
     * @param desiredModel desired resource model
     * @return awsRequest the aws service request to modify a resource
     */
    static UpdatePackageGroupRequest translateToUpdatePackageGroupRequest(
            final ResourceModel desiredModel
    ) {
        return UpdatePackageGroupRequest.builder()
            .domain(desiredModel.getDomainName())
            .domainOwner(desiredModel.getDomainOwner())
            .packageGroup(desiredModel.getPattern())
            .description(desiredModel.getDescription())
            .contactInfo(desiredModel.getContactInfo())
            .build();
    }

    /**
     * Request to update PackageGroup origin configuration
     * @param desiredModel desired resource model
     * @param prevModel previous resource model
     * @return awsRequest the aws service request to modify a resource
     */
    static List<UpdatePackageGroupOriginConfigurationRequest> translateToUpdatePackageGroupOriginControlRequests(
        final ResourceModel desiredModel,
        final ResourceModel prevModel
    ) {
        Set<PackageGroupAllowedRepository> desiredAllowedRepos = translateToAllowedRepositoriesRequest(desiredModel);
        Set<PackageGroupAllowedRepository> previousAllowedRepos = translateToAllowedRepositoriesRequest(prevModel, desiredModel);

        Set<PackageGroupAllowedRepository> reposToRemove = Sets.difference(previousAllowedRepos, desiredAllowedRepos);
        Set<PackageGroupAllowedRepository> reposToAdd = Sets.difference(desiredAllowedRepos, previousAllowedRepos);

        List<UpdatePackageGroupOriginConfigurationRequest> totalRequests = new ArrayList<>();
        UpdatePackageGroupOriginConfigurationRequest.Builder firstUpdateRequestBuilder =
            UpdatePackageGroupOriginConfigurationRequest.builder()
                .domain(desiredModel.getDomainName())
                .domainOwner(desiredModel.getDomainOwner())
                .packageGroup(desiredModel.getPattern())
                .restrictions(translateToRestrictionsRequest(desiredModel));

        if (reposToAdd.size() + reposToRemove.size() <= MAX_ALLOWED_REPO_UPDATES_IN_ONE_REQUEST) {
            return List.of(
                firstUpdateRequestBuilder
                    .addAllowedRepositories(reposToAdd)
                    .removeAllowedRepositories(reposToRemove).build());
        }

        UpdatePackageGroupOriginConfigurationRequest.Builder currentBuilder = firstUpdateRequestBuilder;
        List<PackageGroupAllowedRepository> reposToAddInCurrentRequest = new ArrayList<>();
        int reposInCurrentRequestCounter = 0;

        for (PackageGroupAllowedRepository repo : reposToAdd) {
            reposToAddInCurrentRequest.add(repo);
            reposInCurrentRequestCounter++;

            if (reposInCurrentRequestCounter == MAX_ALLOWED_REPO_UPDATES_IN_ONE_REQUEST) {
                totalRequests.add(currentBuilder.addAllowedRepositories(reposToAddInCurrentRequest).build());
                reposToAddInCurrentRequest.clear();
                reposInCurrentRequestCounter = 0;
                currentBuilder = UpdatePackageGroupOriginConfigurationRequest.builder()
                    .domain(desiredModel.getDomainName())
                    .domainOwner(desiredModel.getDomainOwner())
                    .packageGroup(desiredModel.getPattern());
            }
        }

        // Add remaining repos to add to the current request builder to combine with repos to remove
        if (!reposToAddInCurrentRequest.isEmpty()) {
            currentBuilder.addAllowedRepositories(reposToAddInCurrentRequest);
        }

        List<PackageGroupAllowedRepository> reposToRemoveInCurrentRequest = new ArrayList<>();
        for (PackageGroupAllowedRepository repo : reposToRemove) {
            reposToRemoveInCurrentRequest.add(repo);
            reposInCurrentRequestCounter++;

            if (reposInCurrentRequestCounter == MAX_ALLOWED_REPO_UPDATES_IN_ONE_REQUEST) {
                totalRequests.add(currentBuilder.removeAllowedRepositories(reposToRemoveInCurrentRequest).build());
                reposToAddInCurrentRequest.clear();
                reposToRemoveInCurrentRequest.clear();
                reposInCurrentRequestCounter = 0;
                currentBuilder = UpdatePackageGroupOriginConfigurationRequest.builder()
                    .domain(desiredModel.getDomainName())
                    .domainOwner(desiredModel.getDomainOwner())
                    .packageGroup(desiredModel.getPattern());
            }
        }

        if (!reposToRemoveInCurrentRequest.isEmpty()) {
            // If there are remaining repos to remove, add them to the builder and build the request
            totalRequests.add(currentBuilder.removeAllowedRepositories(reposToRemoveInCurrentRequest).build());
        } else if (!reposToAddInCurrentRequest.isEmpty()) {
            // Current request builder could still have remaining repos to add without any repos to remove
            totalRequests.add(currentBuilder.build());
        }

        return totalRequests;
    }

    static Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestrictionMode> translateToRestrictionsRequest(
        final ResourceModel model
    ) {
        Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestrictionMode> restrictions = new HashMap<>();
        PackageGroupOriginRestrictionMode defaultFallbackMode = model.getPattern().equals(BaseHandlerStd.ROOT_PATTERN) ?
            PackageGroupOriginRestrictionMode.ALLOW : PackageGroupOriginRestrictionMode.INHERIT;

        Optional.ofNullable(model.getOriginConfiguration())
            .map(OriginConfiguration::getRestrictions)
            .map(Restrictions::getPublish)
            .map(RestrictionType::getRestrictionMode)
            .ifPresentOrElse(
                mode ->
                    restrictions.put(
                        PackageGroupOriginRestrictionType.PUBLISH,
                        PackageGroupOriginRestrictionMode.fromValue(mode)),
                () ->
                    restrictions.put(
                        PackageGroupOriginRestrictionType.PUBLISH,
                        defaultFallbackMode));

        Optional.ofNullable(model.getOriginConfiguration())
            .map(OriginConfiguration::getRestrictions)
            .map(Restrictions::getExternalUpstream)
            .map(RestrictionType::getRestrictionMode)
            .ifPresentOrElse(
                mode ->
                    restrictions.put(
                        PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM,
                        PackageGroupOriginRestrictionMode.fromValue(mode)),
                () ->
                    restrictions.put(
                        PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM,
                        defaultFallbackMode));

        Optional.ofNullable(model.getOriginConfiguration())
            .map(OriginConfiguration::getRestrictions)
            .map(Restrictions::getInternalUpstream)
            .map(RestrictionType::getRestrictionMode)
            .ifPresentOrElse(
                mode ->
                    restrictions.put(
                        PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM,
                        PackageGroupOriginRestrictionMode.fromValue(mode)),
                () ->
                    restrictions.put(
                        PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM,
                        defaultFallbackMode));

        return restrictions;
    }

    static Set<PackageGroupAllowedRepository> translateToAllowedRepositoriesRequest(final ResourceModel desiredModel) {
        return translateToAllowedRepositoriesRequest(desiredModel, desiredModel);
    }

    static Set<PackageGroupAllowedRepository> translateToAllowedRepositoriesRequest(
        final ResourceModel modelToTranslate,
        final ResourceModel desiredModel
    ) {
        Set<PackageGroupAllowedRepository> allowedRepos = new HashSet<>();
        if (modelToTranslate == null) return allowedRepos;
        boolean isAddingRepos = modelToTranslate.equals(desiredModel);

        if (isAddingRepos || (getOptionalPublishType(desiredModel).isPresent() &&
                isAllowSpecificRepoMode(desiredModel.getOriginConfiguration().getRestrictions().getPublish()))
        ) {
            getOptionalPublishType(modelToTranslate)
                .map(RestrictionType::getRepositories)
                .ifPresent(repos ->
                    allowedRepos.addAll(
                        streamOfOrEmpty(repos).map(repoName ->
                            PackageGroupAllowedRepository.builder()
                                .repositoryName(repoName)
                                .originRestrictionType(PackageGroupOriginRestrictionType.PUBLISH)
                                .build()).collect(Collectors.toSet())));
        }

        if (isAddingRepos || (getOptionalExternalUpstreamType(desiredModel).isPresent() &&
                isAllowSpecificRepoMode(desiredModel.getOriginConfiguration().getRestrictions().getExternalUpstream()))
        ) {
            getOptionalExternalUpstreamType(modelToTranslate)
                .map(RestrictionType::getRepositories)
                .ifPresent(repos ->
                    allowedRepos.addAll(
                        streamOfOrEmpty(repos).map(repoName ->
                            PackageGroupAllowedRepository.builder()
                                .repositoryName(repoName)
                                .originRestrictionType(PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM)
                                .build()).collect(Collectors.toSet())));
        }

        if (isAddingRepos || (getOptionalInternalUpstreamType(desiredModel).isPresent() &&
                isAllowSpecificRepoMode(desiredModel.getOriginConfiguration().getRestrictions().getInternalUpstream()))
        ) {
           getOptionalInternalUpstreamType(modelToTranslate)
                .map(RestrictionType::getRepositories)
                .ifPresent(repos ->
                    allowedRepos.addAll(
                        streamOfOrEmpty(repos).map(repoName ->
                            PackageGroupAllowedRepository.builder()
                                .repositoryName(repoName)
                                .originRestrictionType(PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM)
                                .build()).collect(Collectors.toSet())));
        }

        return allowedRepos;
    }

    private static Optional<RestrictionType> getOptionalPublishType(ResourceModel model) {
        return Optional.ofNullable(model.getOriginConfiguration())
                .map(OriginConfiguration::getRestrictions)
                .map(Restrictions::getPublish);
    }

    private static Optional<RestrictionType> getOptionalExternalUpstreamType(ResourceModel model) {
        return Optional.ofNullable(model.getOriginConfiguration())
                .map(OriginConfiguration::getRestrictions)
                .map(Restrictions::getExternalUpstream);
    }

    private static Optional<RestrictionType> getOptionalInternalUpstreamType(ResourceModel model) {
        return Optional.ofNullable(model.getOriginConfiguration())
                .map(OriginConfiguration::getRestrictions)
                .map(Restrictions::getInternalUpstream);
    }

    /**
     * Request to delete a resource
     * @param model resource model
     * @return awsRequest the aws service request to delete a resource
     */
    static DeletePackageGroupRequest translateToDeleteRequest(final ResourceModel model) {

        String domainName = model.getDomainName();
        String pattern = model.getPattern();

        if (model.getArn() != null && domainName == null) {
            PackageGroupArn packageGroupArn = PackageGroupArn.fromArn(model.getArn());
            domainName = packageGroupArn.domainName();
            pattern = packageGroupArn.packageGroupName();
        }

        return DeletePackageGroupRequest.builder()
            .domain(domainName)
            .packageGroup(pattern)
            .build();
    }

    /**
     * Request to list resources
     * @param nextToken token passed to the aws service list resources request
     * @return awsRequest the aws service request to list resources within aws account
     */
    static ListPackageGroupsRequest translateToListRequest(final String nextToken, ResourceModel model) {
        return ListPackageGroupsRequest.builder()
            .domain(model.getDomainName())
            .maxResults(Constants.MAX_ITEMS)
            .nextToken(nextToken)
            .build();
    }

    /**
     * Translates resource objects from sdk into a resource model (primary identifier only)
     * @param awsResponse the aws service describe resource response
     * @return list of resource models
     */
    static List<ResourceModel> translateFromListResponse(
        final ListPackageGroupsResponse awsResponse,
        final ResourceHandlerRequest<ResourceModel> request
    ) {
        return streamOfOrEmpty(awsResponse.packageGroups())
            .map(packageGroup -> ResourceModel.builder()
                .arn(packageGroup.arn())
                .build())
            .collect(Collectors.toList());
    }


    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
            .map(Collection::stream)
            .orElseGet(Stream::empty);
    }

    static void throwCfnException(final AwsServiceException exception, String operation, String domainName) {
        if (exception instanceof AccessDeniedException) {
            throw new CfnAccessDeniedException(exception);
        }
        if (exception instanceof ConflictException) {
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, domainName, exception);
        }
        if (exception instanceof ResourceNotFoundException) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, domainName, exception);
        }
        if (exception instanceof ServiceQuotaExceededException) {
            throw new CfnServiceLimitExceededException(ResourceModel.TYPE_NAME, exception.getMessage(), exception);
        }
        if (exception instanceof ValidationException) {
            throw new CfnInvalidRequestException(exception);
        }
        if (exception instanceof InternalServerException) {
            throw new CfnServiceInternalErrorException(operation, exception);
        }
        throw new CfnGeneralServiceException(exception);
    }

    static UntagResourceRequest untagResourceRequest(
        ResourceHandlerRequest<ResourceModel> request,
        List<Tag> tagsToRemove
    ) {
        String arn = request.getDesiredResourceState().getArn();

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
        List<Tag> tagsToAdd
    ) {
        String arn = request.getDesiredResourceState().getArn();

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
            .resourceArn(model.getArn())
            .build();
    }
}
