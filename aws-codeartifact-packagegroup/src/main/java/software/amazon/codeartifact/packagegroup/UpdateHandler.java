package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Objects;

public class UpdateHandler extends BaseHandlerStd {

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        final ResourceModel desiredModel = request.getDesiredResourceState();
        final ResourceModel previousModel = request.getPreviousResourceState();

        if (willUpdateCreateOnlyProperty(desiredModel, previousModel)) {
            throw new CfnNotUpdatableException(ResourceModel.TYPE_NAME, desiredModel.getArn());
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> updatePackageGroup(proxy, desiredModel, previousModel, progress, callbackContext, proxyClient, logger))
            .then(progress -> updatePackageGroupOriginConfiguration(proxy, desiredModel, previousModel, callbackContext, proxyClient, progress, logger))
            .then(progress -> updateTags(proxy, proxyClient, progress, desiredModel, request))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private boolean willUpdateCreateOnlyProperty(final ResourceModel desiredModel, final ResourceModel prevModel) {
        return !Objects.equals(desiredModel.getPattern(), prevModel.getPattern()) ||
                !Objects.equals(desiredModel.getDomainName(), prevModel.getDomainName());
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
        if (!ComparisonUtils.willUpdateOriginConfiguration(desiredModel, prevModel)) {
            return ProgressEvent.progress(desiredModel, callbackContext);
        }

        return super.updatePackageGroupOriginConfiguration(proxy, desiredModel, prevModel, callbackContext, proxyClient, progress, logger);
    }
}
