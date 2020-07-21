package software.amazon.codeartifact.domain;

import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)

            // STEP 1.0 [delete/stabilize progress chain - required for resource deletion]
            .then(progress ->
                // STEP 1.0 [initialize a proxy context]
                proxy.initiate("AWS-CodeArtifact-Domain::Delete", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                    // STEP 1.1 [construct a body of a request]
                    .translateToServiceRequest(Translator::translateToDeleteRequest)
                    // STEP 1.2 [make an api call]
                    .makeServiceCall((awsRequest, client) -> {
                        AwsResponse awsResponse = null;
                        try {
                            awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::deleteDomain);
                        } catch (final AwsServiceException e) {
                            String domainName = progress.getResourceModel().getDomainName();
                            Translator.throwCfnException(e, Constants.DELETE_DOMAIN, domainName);
                        }

                        logger.log(String.format("%s successfully deleted.", ResourceModel.TYPE_NAME));
                        return awsResponse;
                    })
                    // STEP 2.3 [Stabilize to check if the resource got deleted]
                    .stabilize((deleteDomainRequest, deleteDomainResponse, proxyInvocation, model, context) -> isDeleted(model, proxyInvocation, request))
                    .success());
    }

    protected boolean isDeleted(final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient,
        ResourceHandlerRequest<ResourceModel> request
    ) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(
                Translator.translateToReadRequest(model, request), proxyClient.client()::describeDomain);
            return false;
        } catch (ResourceNotFoundException e) {
            return true;
        }
    }
}
