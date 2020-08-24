package software.amazon.codeartifact.repository;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.ListRepositoriesRequest;
import software.amazon.awssdk.services.codeartifact.model.ListRepositoriesResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ListRepositoriesRequest awsRequest = Translator.translateToListRequest(request.getNextToken());

        ListRepositoriesResponse response = null;
        try {
            response = proxy.injectCredentialsAndInvokeV2(awsRequest, ClientBuilder.getClient()::listRepositories);
        } catch (AwsServiceException e) {
            Translator.throwCfnException(e, Constants.LIST_REPOSITORIES, null);
        }
        String nextToken = response.nextToken();

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModels(Translator.translateFromListRequest(response))
            .nextToken(nextToken)
            .status(OperationStatus.SUCCESS)
            .build();
    }
}
