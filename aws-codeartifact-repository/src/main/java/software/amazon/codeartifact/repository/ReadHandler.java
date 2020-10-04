package software.amazon.codeartifact.repository;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        ResourceModel model = request.getDesiredResourceState();
        // STEP 1 [initialize a proxy context]
        return proxy.initiate("AWS-CodeArtifact-Domain::Read", proxyClient, request.getDesiredResourceState(), callbackContext)
            // STEP 2 [construct a body of a request]
            .translateToServiceRequest(Translator::translateToReadRequest)
            // STEP 3 [make an api call]
            .makeServiceCall((awsRequest, client) -> {
                DescribeRepositoryResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::describeRepository);
                } catch (final AwsServiceException e) {
                    String repositoryName = request.getDesiredResourceState().getRepositoryName();
                    Translator.throwCfnException(e, Constants.DESCRIBE_REPOSITORY, repositoryName);
                }
                logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            // STEP 4 [gather all properties of the resource]
            .done(awsResponse ->
                ProgressEvent.defaultSuccessHandler(Translator.translateFromReadResponse(awsResponse, model))
            );
    }
}
