package software.amazon.codeartifact.packagegroup;

import com.google.common.annotations.VisibleForTesting;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.CreatePackageGroupResponse;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandlerStd {
    private static final int CALLBACK_DELAY_SECONDS = 10;

    @VisibleForTesting
    public ReadHandler readHandler = new ReadHandler();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel desiredModel = request.getDesiredResourceState();
        final ResourceModel prevModel;

        ProgressEvent<ResourceModel, CallbackContext> createPackageGroupEvent;
        // Skip Root package group creation because it has been created along with the domain
        if (isRootPackageGroup(desiredModel)) {
            prevModel = constructRootPackageGroupState(proxy, request, callbackContext, proxyClient, logger, desiredModel);
            createPackageGroupEvent = ProgressEvent.progress(desiredModel, callbackContext)
                .then(progress -> updatePackageGroup(proxy, desiredModel, prevModel, progress, callbackContext, proxyClient, logger))
                .then(progress -> updateTags(proxy, proxyClient, progress, desiredModel, request));
        } else {
            prevModel = request.getPreviousResourceState();
            createPackageGroupEvent = ProgressEvent.progress(desiredModel, callbackContext)
                .then(progress -> createPackageGroup(proxy, request, progress, callbackContext, proxyClient));
        }

        return createPackageGroupEvent
            .then(progress -> updatePackageGroupOriginConfiguration(proxy, desiredModel, prevModel, callbackContext, proxyClient, progress, logger))
            .then(progress -> readHandler.handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createPackageGroup(
        AmazonWebServicesClientProxy proxy,
        ResourceHandlerRequest<ResourceModel> request,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        CallbackContext callbackContext,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        if (callbackContext.isCreated()) {
            return ProgressEvent.progress(progress.getResourceModel(), callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-PackageGroup::Create", proxyClient, progress.getResourceModel(), callbackContext)
            .translateToServiceRequest((model) -> Translator.translateToCreateRequest(model, request.getDesiredResourceTags()))
            .makeServiceCall((awsRequest, client) -> {
                CreatePackageGroupResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::createPackageGroup);
                } catch (final AwsServiceException e) {
                    Translator.throwCfnException(e, Constants.CREATE_PACKAGE_GROUP, awsRequest.packageGroup());
                }
                logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
                callbackContext.setCreated(true);
                return awsResponse;
            })
            .stabilize((awsRequest, awsResponse, client, model, context) -> doesPackageGroupExist(model, client))
            .done((awsRequest, awsResponse, client, model, context) -> {
                model.setArn(awsResponse.packageGroup().arn());
                return ProgressEvent.defaultInProgressHandler(context, CALLBACK_DELAY_SECONDS, model);
            });
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> updatePackageGroupOriginConfiguration(
        final AmazonWebServicesClientProxy proxy,
        final ResourceModel desiredModel,
        final ResourceModel prevModel,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        Logger logger
    ) {
        if (ComparisonUtils.willNotUpdateOriginConfigurationOnCreation(desiredModel)) {
            return ProgressEvent.progress(desiredModel, callbackContext);
        }

        return super.updatePackageGroupOriginConfiguration(proxy, desiredModel, prevModel, callbackContext, proxyClient, progress, logger);
    }

    private ResourceModel constructRootPackageGroupState(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger,
        ResourceModel desiredModel
    ) {
        // Call read handler to construct the previous resource model and set ARN for root package group
        ResourceModel previousModel = readHandler.handleRequest(proxy, request, callbackContext, proxyClient, logger).getResourceModel();
        desiredModel.setArn(previousModel.getArn());
        request.setDesiredResourceState(desiredModel);
        request.setPreviousResourceTags(Translator.translateCfnModelToTags(previousModel.getTags()));
        request.setPreviousResourceState(previousModel);

        return previousModel;
    }
}
