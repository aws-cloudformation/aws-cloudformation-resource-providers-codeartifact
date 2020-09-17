package software.amazon.codeartifact.domain;

import java.util.Objects;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger
    ) {

        this.logger = logger;

        ResourceModel desiredResourceState = request.getDesiredResourceState();
        ResourceModel previousResourceState = request.getPreviousResourceState();
        if (!Objects.equals(previousResourceState.getDomainName(), desiredResourceState.getDomainName()) ||
            !Objects.equals(previousResourceState.getEncryptionKey(), desiredResourceState.getEncryptionKey())
        ) {
            logger.log("Unupdatable exception here jonjar");
            // cannot update domainName/EncryptionKey because it's CreateOnly
            throw new CfnNotUpdatableException(ResourceModel.TYPE_NAME, desiredResourceState.getArn());
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> updateDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> updateDomainPermissionsPolicy(
        final AmazonWebServicesClientProxy proxy,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ResourceHandlerRequest<ResourceModel> request,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger
    ) {
        final ResourceModel desiredModel = request.getDesiredResourceState();
        if (desiredModel.getPermissionsPolicyDocument() != null) {
            return putDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger);
        }
        return deleteDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient);
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteDomainPermissionsPolicy(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        CallbackContext callbackContext,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        final ResourceModel desiredModel = request.getDesiredResourceState();
        final ResourceModel previousModel = request.getPreviousResourceState();

        if (desiredModel.getPermissionsPolicyDocument() != null || previousModel.getPermissionsPolicyDocument() == null) {
            return ProgressEvent.progress(desiredModel, callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-Domain::Update::DeleteDomainPermissionsPolicy", proxyClient,
            progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateDeleteDomainPolicyRequest)
            .makeServiceCall((awsRequest, client) -> {
                try {
                    client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deleteDomainPermissionsPolicy);
                    logger.log("Domain permission policy successfully deleted.");
                } catch (final AwsServiceException e) {
                    String domainName = desiredModel.getDomainName();
                    Translator.throwCfnException(e, Constants.DELETE_DOMAIN_PERMISSION_POLICY, domainName);
                }
                return awsRequest;
            })
            .progress();
    }

}
