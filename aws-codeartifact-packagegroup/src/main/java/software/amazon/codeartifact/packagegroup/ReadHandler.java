package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionMode;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionType;
import software.amazon.awssdk.services.codeartifact.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;

public class ReadHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        logger.log(String.format("%s read handler is being invoked", ResourceModel.TYPE_NAME));
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> describePackageGroup(proxy, progress, request, proxyClient))
            .then(progress -> listAllowedRepositoriesForGroup(proxy, progress, request, proxyClient, PackageGroupOriginRestrictionType.PUBLISH))
            .then(progress -> listAllowedRepositoriesForGroup(proxy, progress, request, proxyClient, PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM))
            .then(progress -> listAllowedRepositoriesForGroup(proxy, progress, request, proxyClient, PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM))
            .then(progress -> listTags(proxy, progress, request, proxyClient))
            .then(progress -> {
                final ResourceModel model = progress.getResourceModel();
                return ProgressEvent.defaultSuccessHandler(model);
            });

    }

    private ProgressEvent<ResourceModel, CallbackContext> listTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-PackageGroup::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToListTagsRequest)
            .makeServiceCall((awsRequest, client) -> {
                logger.log(String.format("%s ListTags is being invoked", ResourceModel.TYPE_NAME));
                ListTagsForResourceResponse listTagsResponse = null;
                try {
                    listTagsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::listTagsForResource);
                } catch (final AwsServiceException e) {
                    String domainName = request.getDesiredResourceState().getDomainName();
                    Translator.throwCfnException(e, Constants.LIST_TAGS_FOR_RESOURCE, domainName);
                }
                logger.log(String.format("Tags of %s has successfully been read.", ResourceModel.TYPE_NAME));
                return listTagsResponse;})
            .done((ListTagsForResourceRequest, listTagsResponse, proxyInvocation, resourceModel, context) -> {
                if (listTagsResponse != null) {
                    List<Tag> tags = listTagsResponse.tags();
                    resourceModel.setTags(Translator.fromListTagsResponse(tags));
                }
                return ProgressEvent.progress(resourceModel, context);});
    }

    private ProgressEvent<ResourceModel, CallbackContext> describePackageGroup(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-PackageGroup::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToReadRequest)
            .makeServiceCall((awsRequest, client) -> {
                DescribePackageGroupResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::describePackageGroup);
                } catch (final AwsServiceException e) {
                    Translator.throwCfnException(e, Constants.DESCRIBE_PACKAGE_GROUP, awsRequest.packageGroup());
                }
                logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            .done((describePackageGroupRequest, describePackageGroupResponse, proxyInvocation, resourceModel, context) ->
                ProgressEvent.progress(Translator.translateFromReadResponse(describePackageGroupResponse), context));
    }

    private ProgressEvent<ResourceModel, CallbackContext> listAllowedRepositoriesForGroup(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient,
        PackageGroupOriginRestrictionType restrictionType
    ) {
        if (!canContainAllowedRepositories(progress, restrictionType)) {
            return progress;
        }

        final ListAllowedRepositoriesForGroupRequest awsRequest =
            Translator.translateToListAllowedReposRequest(request.getNextToken(), restrictionType, progress.getResourceModel());

        ListAllowedRepositoriesForGroupResponse response = null;
        try {
            response = proxy.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::listAllowedRepositoriesForGroup);
        } catch (AwsServiceException e) {
            Translator.throwCfnException(e, Constants.LIST_ALLOW_REPOS_FOR_GROUPS, null);
        }
        String nextToken = response.nextToken();

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModel(Translator.translateFromListAllowedReposResponse(response, restrictionType, progress.getResourceModel()))
            .callbackContext(progress.getCallbackContext())
            .nextToken(nextToken)
            .status(OperationStatus.IN_PROGRESS)
            .build();
    }

    private boolean canContainAllowedRepositories(
        ProgressEvent<ResourceModel, CallbackContext> progress,
        PackageGroupOriginRestrictionType type
    ) {
        switch (type) {
            case PUBLISH:
                return progress.getResourceModel().getOriginConfiguration().getRestrictions().getPublish().getRestrictionMode()
                        .equals(PackageGroupOriginRestrictionMode.ALLOW_SPECIFIC_REPOSITORIES.toString());
            case EXTERNAL_UPSTREAM:
                return progress.getResourceModel().getOriginConfiguration().getRestrictions().getExternalUpstream().getRestrictionMode()
                        .equals(PackageGroupOriginRestrictionMode.ALLOW_SPECIFIC_REPOSITORIES.toString());
            case INTERNAL_UPSTREAM:
                return progress.getResourceModel().getOriginConfiguration().getRestrictions().getInternalUpstream().getRestrictionMode()
                        .equals(PackageGroupOriginRestrictionMode.ALLOW_SPECIFIC_REPOSITORIES.toString());
            default:
                return false;
        }
    }
}
