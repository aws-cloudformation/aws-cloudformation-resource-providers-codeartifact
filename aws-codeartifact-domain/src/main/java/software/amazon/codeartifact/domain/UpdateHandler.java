package software.amazon.codeartifact.domain;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
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
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            // If PolicyDocument is in the model putDomainPermissions policy
            .then(progress -> putDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger))
            // Else then deleteDomainPermission policy
            .then(progress -> deleteDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteDomainPermissionsPolicy(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        CallbackContext callbackContext,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        final ResourceModel desiredModel = progress.getResourceModel();

        if (desiredModel.getPolicyDocument() != null) {
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
            .stabilize((awsRequest, awsResponse, client, model, context) -> domainPolicyIsDeleted(model, client, request))
            .progress();
    }

    private boolean domainPolicyIsDeleted(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient,
        ResourceHandlerRequest<ResourceModel> request
    ) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(
                Translator.translateToGetDomainPermissionPolicy(model, request), proxyClient.client()::getDomainPermissionsPolicy);
            return false;
        } catch (ResourceNotFoundException e) {
            return true;
        }
    }



}
