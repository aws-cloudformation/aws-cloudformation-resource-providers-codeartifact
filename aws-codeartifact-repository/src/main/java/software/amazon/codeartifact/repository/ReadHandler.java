package software.amazon.codeartifact.repository;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
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

        // STEP 1 [initialize a proxy context]
        logger.log(String.format("%s read handler is being invoked", ResourceModel.TYPE_NAME));
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> describeRepository(proxy, progress, request, proxyClient))
            .then(progress -> getRepositoryPolicy(proxy, progress, request, proxyClient))
            .then(progress -> {
                final ResourceModel model = progress.getResourceModel();
                return ProgressEvent.defaultSuccessHandler(model);
            });

    }

    private ProgressEvent<ResourceModel, CallbackContext> getRepositoryPolicy(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Repository::GetRepositoryPolicy", proxyClient,
            progress.getResourceModel(),
            progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToGetRepositoryPermissionsPolicy)
            .makeServiceCall((awsRequest, client) -> {
                logger.log(String.format("%s getRepositoryPolicy is being invoked", ResourceModel.TYPE_NAME));
                GetRepositoryPermissionsPolicyResponse getRepositoryPermissionsPolicyResponse = null;
                try {
                    getRepositoryPermissionsPolicyResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::getRepositoryPermissionsPolicy);
                } catch (final ResourceNotFoundException e) {
                    // Do nothing since there is no policy
                } catch (final AwsServiceException e) {
                    String repositoryName = request.getDesiredResourceState().getRepositoryName();
                    Translator.throwCfnException(e, Constants.GET_REPOSITORY_PERMISSION_POLICY, repositoryName);
                }
                logger.log(String.format("Repository policy of %s has successfully been read.", ResourceModel.TYPE_NAME));
                return getRepositoryPermissionsPolicyResponse;
            })
            .done((getRepositoryPermissionsPolicyRequest, getRepositoryPermissionsPolicyResponse, proxyInvocation, resourceModel, context) -> {
                if (getRepositoryPermissionsPolicyResponse != null) {
                    String repositoryPolicy = getRepositoryPermissionsPolicyResponse.policy().document();
                    resourceModel.setPermissionsPolicyDocument(Translator.deserializePolicy(repositoryPolicy));
                }
                return ProgressEvent.progress(resourceModel, context);
            });

    }

    private ProgressEvent<ResourceModel, CallbackContext> describeRepository(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Repository::Repository", proxyClient, progress.getResourceModel(),
            progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToReadRequest)
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
            .done((describeRepositoryRequest, describeRepositoryResponse, proxyInvocation, resourceModel, context) ->
                ProgressEvent.progress(Translator.translateFromReadResponse(describeRepositoryResponse), context));
    }
}
