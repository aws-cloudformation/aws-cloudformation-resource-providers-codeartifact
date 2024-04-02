package software.amazon.codeartifact.packagegroup;

import com.amazonaws.util.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.collect.Sets;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.Tag;
import software.amazon.awssdk.services.codeartifact.model.TagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UntagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupOriginConfigurationRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String ROOT_PATTERN = "/*";

    public Logger logger;

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        return handleRequest(
            proxy,
            request,
            callbackContext != null ? callbackContext : new CallbackContext(),
            proxy.newProxy(ClientBuilder::getClient),
            logger
        );
    }

    public boolean doesPackageGroupExist(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient
    ) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(
                    Translator.translateToReadRequest(model), proxyClient.client()::describePackageGroup);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }


    public boolean isRootPackageGroup(ResourceModel model) {
        if (model.getPattern() != null && model.getPattern().equals(ROOT_PATTERN))
            return true;
        else
            return (model.getArn() != null && PackageGroupArn.fromArn(model.getArn()).packageGroupName().equals(ROOT_PATTERN));
    }

    public ProgressEvent<ResourceModel, CallbackContext> updatePackageGroup(
        final AmazonWebServicesClientProxy proxy,
        final ResourceModel desiredModel,
        final ResourceModel previousModel,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        Logger logger
    ) {
        if (!ComparisonUtils.willUpdateContactInfo(desiredModel, previousModel) &&
                !ComparisonUtils.willUpdateDescription(desiredModel, previousModel)) {
            return ProgressEvent.progress(desiredModel, callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-PackageGroup::UpdatePackageGroup", proxyClient, progress.getResourceModel(), callbackContext)
                .translateToServiceRequest(Translator::translateToUpdatePackageGroupRequest)
                .makeServiceCall((awsRequest, client) -> {
                    AwsResponse awsResponse = null;
                    try {
                        awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::updatePackageGroup);
                    } catch (final AwsServiceException e) {
                        Translator.throwCfnException(e, Constants.UPDATE_PACKAGE_GROUP, awsRequest.packageGroup());
                    }
                    logger.log(String.format("%s metadata successfully updated.", ResourceModel.TYPE_NAME));
                    return awsResponse;
                })
                .progress();
    }

    public ProgressEvent<ResourceModel,CallbackContext> updateTags(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<CodeartifactClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceModel desiredModel,
        final ResourceHandlerRequest<ResourceModel> request
    ) {
        String packageGroupName = desiredModel.getPattern();
        Map<String, String> desiredResourceTags = request.getDesiredResourceTags();
        Map<String, String> previousResourceTags = request.getPreviousResourceTags();

        final Set<software.amazon.awssdk.services.codeartifact.model.Tag> desiredSdkTags = new HashSet<>(Translator.translateTagsToSdk(desiredResourceTags));
        final Set<software.amazon.awssdk.services.codeartifact.model.Tag> previousSdkTags = new HashSet<>(Translator.translateTagsToSdk(previousResourceTags));

        final Set<software.amazon.awssdk.services.codeartifact.model.Tag> setTagsToRemove = Sets.difference(previousSdkTags, desiredSdkTags);
        final Set<software.amazon.awssdk.services.codeartifact.model.Tag> setTagsToAdd = Sets.difference(desiredSdkTags, previousSdkTags);

        final List<software.amazon.awssdk.services.codeartifact.model.Tag> tagsToRemove = new ArrayList<>(setTagsToRemove);
        final List<Tag> tagsToAdd = new ArrayList<>(setTagsToAdd);

        try {
            // Delete tags only if tagsToRemove is not empty.
            if (!CollectionUtils.isNullOrEmpty(tagsToRemove)) {
                UntagResourceRequest untagRequest
                        = Translator.untagResourceRequest(request, tagsToRemove);
                proxy.injectCredentialsAndInvokeV2(untagRequest, proxyClient.client()::untagResource);
            }
        } catch (AwsServiceException e) {
            Translator.throwCfnException(e, Constants.UNTAG_RESOURCE, packageGroupName);
        }

        try {
            // Adds tags only if tagsToAdd is not empty.
            if (!CollectionUtils.isNullOrEmpty(tagsToAdd)) {
                TagResourceRequest tagRequest
                        = Translator.tagResourceRequest(request, tagsToAdd);
                proxy.injectCredentialsAndInvokeV2(tagRequest, proxyClient.client()::tagResource);
            }
        } catch (AwsServiceException e) {
            Translator.throwCfnException(e, Constants.TAG_RESOURCE, packageGroupName);
        }

        return ProgressEvent.progress(progress.getResourceModel(), progress.getCallbackContext());
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger
    );

    protected ProgressEvent<ResourceModel, CallbackContext> updatePackageGroupOriginConfiguration(
        final AmazonWebServicesClientProxy proxy,
        final ResourceModel desiredModel,
        final ResourceModel previousModel,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        Logger logger
    ) {
        List<UpdatePackageGroupOriginConfigurationRequest> totalRequests =
            Translator.translateToUpdatePackageGroupOriginControlRequests(desiredModel, previousModel);

        for (UpdatePackageGroupOriginConfigurationRequest request : totalRequests) {
            progress = progress.then(
                p -> updatePackageGroupOriginConfiguration(proxy, request, p, callbackContext, proxyClient, logger));
        }

        return progress;
    }

    private ProgressEvent<ResourceModel, CallbackContext> updatePackageGroupOriginConfiguration(
        final AmazonWebServicesClientProxy proxy,
        final UpdatePackageGroupOriginConfigurationRequest request,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        Logger logger
    ) {
        return proxy.initiate("AWS-CodeArtifact-PackageGroup::UpdateOriginConfiguration", proxyClient, progress.getResourceModel(), callbackContext)
            .translateToServiceRequest((model) -> request)
            .makeServiceCall((awsRequest, client) -> {
                AwsResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::updatePackageGroupOriginConfiguration);
                } catch (final AwsServiceException e) {
                    String packageGroupName = progress.getResourceModel().getPattern();
                    Translator.throwCfnException(e, Constants.UPDATE_PACKAGE_GROUP_ORIGIN_CONFIG, packageGroupName);
                }
                logger.log(String.format("%s origin configuration successfully updated.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            .progress();
    }
}
