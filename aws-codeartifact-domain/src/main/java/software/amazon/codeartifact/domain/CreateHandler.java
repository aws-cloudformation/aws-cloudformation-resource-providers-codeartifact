package software.amazon.codeartifact.domain;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class CreateHandler extends BaseHandlerStd {
    private static final int CALLBACK_DELAY_SECONDS = 1;
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        ResourceModel model = request.getDesiredResourceState();

        // Make sure the user isn't trying to assign values to readOnly properties
        if (hasReadOnlyProperties(model)) {
            throw new CfnInvalidRequestException("Attempting to set a ReadOnly Property.");
        }

        this.logger = logger;
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> createDomain(proxy, progress, request, proxyClient))
            .then(progress -> putDomainPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> createDomain(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        CallbackContext callbackContext = progress.getCallbackContext();

        logger.log(String.format("isCreatedFlag: %s", callbackContext.isCreated()));
        if (callbackContext.isCreated()) {
            // This happens when handler gets called again during callback delay or the handler is retrying
            // after domain was created already. This will prevent 409s on retry.
            logger.log("Domain was already created, will not call CreateDomain again.");
            return ProgressEvent.progress(progress.getResourceModel(), callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-Domain::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest((model) -> Translator.translateToCreateRequest(model, request.getDesiredResourceTags()))
            .makeServiceCall((awsRequest, client) -> createDomainSdkCall(progress, client, callbackContext, awsRequest))
            .stabilize((awsRequest, awsResponse, client, model, context) -> isStabilized(model, client))
            .progress(CALLBACK_DELAY_SECONDS);
    }

    private CreateDomainResponse createDomainSdkCall(
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<CodeartifactClient> client,
        CallbackContext callbackContext,
        CreateDomainRequest awsRequest
    ) {
        CreateDomainResponse awsResponse = null;
        String domainName = progress.getResourceModel().getDomainName();
        try {
            awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::createDomain);
        } catch (final AwsServiceException e) {
            Translator.throwCfnException(e, Constants.CREATE_DOMAIN, domainName);
        }

        logger.log(String.format("%s [%s] Created Successfully", ResourceModel.TYPE_NAME, domainName));
        callbackContext.setCreated(true);
        return awsResponse;
    }

    private boolean hasReadOnlyProperties(final ResourceModel model) {
        return model.getName() != null || model.getOwner() != null;
    }

    private boolean isStabilized(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient
    ) {
        try {
            DescribeDomainResponse describeDomainResponse = proxyClient.injectCredentialsAndInvokeV2(
                Translator.translateToReadRequest(model), proxyClient.client()::describeDomain);

            String domainStatus = describeDomainResponse.domain()
                .status()
                .toString();

            model.setArn(describeDomainResponse.domain().arn());
            logger.log(String.format("Status of domain: %s", domainStatus));

            if (domainStatus.equals(Constants.ACTIVE_STATUS)) {
                logger.log(String.format("%s successfully stabilized.", ResourceModel.TYPE_NAME));
                return true;
            }

        } catch (ResourceNotFoundException ex) {
            // This exception means the resource has not stabilized
        }
        return false;
    }

}
