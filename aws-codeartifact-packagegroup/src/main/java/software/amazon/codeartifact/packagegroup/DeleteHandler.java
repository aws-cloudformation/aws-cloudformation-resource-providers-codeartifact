package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DeletePackageGroupRequest;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();

        // Root package group is retained because it should always exist in a domain
        if (isRootPackageGroup(model)) {
            return ProgressEvent.success(null, callbackContext);
        }

        return ProgressEvent.progress(model, callbackContext)
            .then(progress ->
                 proxy.initiate("AWS-CodeArtifact-PackageGroup::Delete", proxyClient, model, callbackContext)
                     .translateToServiceRequest(Translator::translateToDeleteRequest)
                     .makeServiceCall((awsRequest, client) -> {
                     if (!doesPackageGroupExist(model, proxyClient)) {
                         throw new CfnNotFoundException(model.getPattern(), ResourceModel.TYPE_NAME);
                     }
                     return deletePackageGroup(progress, client, awsRequest);
                 })
                 .stabilize((deletePackageGroupRequest, deletePackageGroupResponse, proxyInvocation, resourceModel, context) -> !doesPackageGroupExist(model, proxyClient))
                 .done((awsRequest, response, client, resourceModel, context) -> ProgressEvent.success(null, context)));
    }

    private AwsResponse deletePackageGroup(
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ProxyClient<CodeartifactClient> client,
        DeletePackageGroupRequest awsRequest
    ) {
        AwsResponse awsResponse = null;
        try {
            awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deletePackageGroup);
        } catch (final AwsServiceException e) {
            String packageGroupName = progress.getResourceModel().getPattern();
            Translator.throwCfnException(e, Constants.DELETE_PACKAGE_GROUP, packageGroupName);
        }

        return awsResponse;
    }
}
