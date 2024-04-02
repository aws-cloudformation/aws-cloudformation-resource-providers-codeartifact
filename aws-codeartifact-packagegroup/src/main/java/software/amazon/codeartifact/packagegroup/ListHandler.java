package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.ListPackageGroupsRequest;
import software.amazon.awssdk.services.codeartifact.model.ListPackageGroupsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ListPackageGroupsRequest awsRequest =
            Translator.translateToListRequest(request.getNextToken(), request.getDesiredResourceState());

        ListPackageGroupsResponse response = null;
        try {
            response = proxy.injectCredentialsAndInvokeV2(awsRequest, ClientBuilder.getClient()::listPackageGroups);
        } catch (AwsServiceException e) {
            Translator.throwCfnException(e, Constants.LIST_PACKAGE_GROUPS, null);
        }
        String nextToken = response.nextToken();

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModels(Translator.translateFromListResponse(response, request))
            .nextToken(nextToken)
            .status(OperationStatus.SUCCESS)
            .build();
    }
}
