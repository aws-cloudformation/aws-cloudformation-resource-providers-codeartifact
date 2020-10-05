package software.amazon.codeartifact.repository;

import java.util.Set;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class CreateHandler extends BaseHandlerStd {
    private static final int CALLBACK_DELAY_SECONDS = 10;
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

        // Make sure the user isn't trying to assign values to readOnly properties
        if (hasReadOnlyProperties(model)) {
            throw new CfnInvalidRequestException("Attempting to set a ReadOnly Property.");
        }

        // Setting primaryId first in case rollback occurs, we need the Id to be able to rollback
        setPrimaryIdentifier(request, model);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> createRepository(proxy, progress, proxyClient))
            .then(progress -> putRepositoryPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger))
            .then(progress -> associateExternalConnections(progress, callbackContext, request, proxyClient, externalConnectionsToAdd, logger))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private void setPrimaryIdentifier(ResourceHandlerRequest<ResourceModel> request, ResourceModel model) {
        String domainOwner = model.getDomainOwner() == null ? request.getAwsAccountId() : model.getDomainOwner();

        RepositoryArn repoArn = ArnUtils.repoArn(
            request.getAwsPartition(),
            request.getRegion(),
            domainOwner,
            model.getDomainName(),
            model.getRepositoryName()
        );

        model.setArn(repoArn.arn());
    }

    private ProgressEvent<ResourceModel, CallbackContext> createRepository(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        CallbackContext callbackContext = progress.getCallbackContext();

        if (callbackContext.isCreated()) {
            // This happens when handler gets called again during callback delay or the handler is retrying for
            // a Retriable exception after repository was created already. This will prevent 409s on retry.
            // https://code.amazon.com/packages/AWSCloudFormationRPDKJavaPlugin/blobs/mainline/--/src/main/java/software/amazon/cloudformation/proxy/HandlerErrorCode.java
            return ProgressEvent.progress(progress.getResourceModel(), callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-Repository::Create", proxyClient, progress.getResourceModel(), callbackContext)
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
                callbackContext.setCreated(true);
                return awsResponse;
            })
            .stabilize((awsRequest, awsResponse, client, model, context) -> isStabilized(model, client))
            // This Callback delay will return IN_PROGRESS and wait a certain amount of seconds and then retry
            // the whole CreateHandler chain. We are doing this to wait for eventual consistencies.
            // Since we are setting the isCreated flag in the callback context
            // the handler will not try to re-create the repository but will skip createRepository and continue down
            // the chain.
            .progress(CALLBACK_DELAY_SECONDS);
    }

    private boolean hasReadOnlyProperties(final ResourceModel model) {
        return model.getName() != null;
    }

    private boolean isStabilized(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient
    ) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(
                    Translator.translateToReadRequest(model), proxyClient.client()::describeRepository);
            logger.log(String.format("%s successfully stabilized.", ResourceModel.TYPE_NAME));
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }
}
