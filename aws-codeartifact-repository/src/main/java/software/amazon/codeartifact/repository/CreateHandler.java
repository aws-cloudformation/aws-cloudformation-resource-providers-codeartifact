package software.amazon.codeartifact.repository;

import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            // STEP 1 [create/stabilize progress chain - required for resource creation]
            .then(progress ->
                proxy.initiate("AWS-CodeArtifact-Domain::Create", proxyClient,progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall((awsRequest, client) -> createRepository(progress, client, awsRequest))
                    .stabilize((awsRequest, awsResponse, client, model, context) -> isStabilized(model, client))
                    .progress()
            )
            // STEP 2 [describe call/chain to return the resource model]
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private AwsResponse createRepository(
        ProgressEvent<ResourceModel, CallbackContext>  progress,
        ProxyClient<CodeartifactClient> client,
        CreateRepositoryRequest awsRequest
    ) {
        AwsResponse awsResponse = null;
        String arn = progress.getResourceModel().getRepositoryName();
        try {
            awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::createRepository);
        } catch (final AwsServiceException e) {
            Translator.throwCfnException(e, Constants.CREATE_REPOSITORY, arn);
        }

        // TODO Add Policy, Upstream, ExternalConnections if provided
        logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
        return awsResponse;
    }

    private boolean isStabilized(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient
    ) {
        try {
            DescribeRepositoryResponse response = proxyClient.injectCredentialsAndInvokeV2(
                    Translator.translateToReadRequest(model), proxyClient.client()::describeRepository);
            model.setArn(response.repository().arn());
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }
}
