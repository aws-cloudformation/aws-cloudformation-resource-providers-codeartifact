package software.amazon.codeartifact.domain;

import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
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
        ResourceModel model = request.getDesiredResourceState();
        // STEP 1.0 [initialize a proxy context]
        return proxy.initiate("AWS-CodeArtifact-Domain::Delete", proxyClient, model, callbackContext)
            // STEP 1.1 [construct a body of a request]
            .translateToServiceRequest(Translator::translateToDeleteRequest)
            // STEP 1.2 [make an api call]
            .makeServiceCall((awsRequest, client) -> {

                // if domain does not exist, deleteDomain does not throw an exception, so we must do this
                // to be under the ResourceHandler Contract
                if (!doesDomainExist(model, proxyClient)) {
                    throw new CfnNotFoundException(model.getDomainName(), ResourceModel.TYPE_NAME);
                }

                AwsResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::deleteDomain);
                } catch (final ConflictException e) {
                    // this happens if the domain has repositories in the account
                    throw new CfnResourceConflictException(ResourceModel.TYPE_NAME, model.getDomainName(), e.getMessage(), e);
                } catch (final AwsServiceException e) {
                    String domainName = model.getDomainName();
                    Translator.throwCfnException(e, Constants.DELETE_DOMAIN, domainName);
                }

                logger.log(String.format("%s successfully deleted.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            // STEP 2.3 [Stabilize to check if the resource got deleted]
            .stabilize((deleteDomainRequest, deleteDomainResponse, proxyInvocation, resourceModel, context) -> !doesDomainExist(model, proxyClient))
            // according to the ResourceHandler contract we must not return the model in the response
            .done((awsRequest, response, client, resourceModel, context) -> ProgressEvent.success(null, context));
    }

}
