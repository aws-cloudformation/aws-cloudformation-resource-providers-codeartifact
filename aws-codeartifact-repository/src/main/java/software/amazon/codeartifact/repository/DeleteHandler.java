package software.amazon.codeartifact.repository;

import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
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
            .then(progress ->
                proxy.initiate("AWS-CodeArtifact-Repository::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                    // STEP 2.1 [construct a body of a request]
                    .translateToServiceRequest(Translator::translateToDeleteRequest)
                    // STEP 2.2 [make an api call]
                    .makeServiceCall((awsRequest, client) -> deleteRepository(progress, client, awsRequest))
                    // STEP 2.3 [stabilize]
                    .stabilize((awsRequest, awsResponse, client, model, context) -> repositoryIsDeleted(model, proxyClient))
                    .success());
    }

    private AwsResponse deleteRepository(
        ProgressEvent<ResourceModel, CallbackContext>  progress,
        ProxyClient<CodeartifactClient> client,
        DeleteRepositoryRequest awsRequest
    ) {
        AwsResponse awsResponse = null;
        try {
            awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deleteRepository);
        } catch (final AwsServiceException e) {
            String repositoryName = progress.getResourceModel().getRepositoryName();
            Translator.throwCfnException(e, Constants.DELETE_REPOSITORY, repositoryName);
        }

        return awsResponse;
    }

    private boolean repositoryIsDeleted(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient
    ) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(
                    Translator.translateToReadRequest(model), proxyClient.client()::describeRepository);
            logger.log(String.format("%s successfully stabilized after deletion.", ResourceModel.TYPE_NAME));
            return false;

        } catch (ResourceNotFoundException e) {
            return true;
        }
    }
}
