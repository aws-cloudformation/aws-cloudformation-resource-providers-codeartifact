package software.amazon.codeartifact.repository;

import java.util.Set;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
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
        ResourceModel model = request.getDesiredResourceState();
        final Set<String> externalConnectionsToAdd = Translator.translateExternalConnectionFromDesiredResource(model);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> createRepository(proxy, progress, proxyClient))
            .then(progress -> putRepositoryPermissionsPolicy(proxy, progress, callbackContext, proxyClient, logger))
            .then(progress -> associateExternalConnections(progress, callbackContext, request, proxyClient, externalConnectionsToAdd, logger))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createRepository(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Repository::Create", proxyClient,progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToCreateRequest)
            .makeServiceCall((awsRequest, client) -> {
                AwsResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::createRepository);
                } catch (final AwsServiceException e) {
                    String repositoryName = progress.getResourceModel().getRepositoryName();
                    Translator.throwCfnException(e, Constants.CREATE_REPOSITORY, repositoryName);
                }
                logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            .stabilize((awsRequest, awsResponse, client, model, context) -> isStabilized(model, client))
            .progress();
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
