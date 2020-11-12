package software.amazon.codeartifact.domain;

import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListDomainsRequest;
import software.amazon.awssdk.services.codeartifact.model.ListDomainsResponse;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
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

        // STEP 1 [Construct a body of a request]
        final ListDomainsRequest awsRequest = Translator.translateToListRequest(request.getNextToken());

        // STEP 2 [make an api call]
        ListDomainsResponse response = null;
        try {
            response = proxy
                .injectCredentialsAndInvokeV2(awsRequest, ClientBuilder.getClient()::listDomains);
        } catch (AccessDeniedException e) {
            throw new CfnAccessDeniedException(Constants.LIST_DOMAINS, e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        }

        // STEP 3 [get a token for the next page]
        String nextToken = response.nextToken();

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModels(Translator.translateFromListRequest(response, request))
            .nextToken(nextToken)
            .status(OperationStatus.SUCCESS)
            .build();
    }

}
