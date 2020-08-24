package software.amazon.codeartifact.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
    final ResourceHandlerRequest<ResourceModel> request,
    final CallbackContext callbackContext,
    final Logger logger) {
    return handleRequest(
      proxy,
      request,
      callbackContext != null ? callbackContext : new CallbackContext(),
      proxy.newProxy(ClientBuilder::getClient),
      logger
    );
  }

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
    final ResourceHandlerRequest<ResourceModel> request,
    final CallbackContext callbackContext,
    final ProxyClient<CodeartifactClient> proxyClient,
    final Logger logger);

  protected ProgressEvent<ResourceModel, CallbackContext> putDomainPermissionsPolicy(
      final AmazonWebServicesClientProxy proxy,
      final ProgressEvent<ResourceModel, CallbackContext> progress,
      final CallbackContext callbackContext,
      ResourceHandlerRequest<ResourceModel> request,
      final ProxyClient<CodeartifactClient> proxyClient,
      final Logger logger
  ) {

      final ResourceModel desiredModel = progress.getResourceModel();
      final ResourceModel previousModel = request.getPreviousResourceState();

      if (desiredModel.getPermissionsPolicyDocument() == null || policyIsUnchanged(desiredModel, previousModel)) {
          return ProgressEvent.progress(desiredModel, callbackContext);
      }
      return proxy.initiate("AWS-CodeArtifact-Domain::Update::PutDomainPermissionsPolicy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
          .translateToServiceRequest(Translator::translatePutDomainPolicyRequest)
          .makeServiceCall((awsRequest, client) -> {
              PutDomainPermissionsPolicyResponse awsResponse = null;
              try {
                  awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::putDomainPermissionsPolicy);
                  logger.log("Domain permission policy successfully added.");
              } catch (final AwsServiceException e) {
                  String domainName = desiredModel.getDomainName();
                  Translator.throwCfnException(e, Constants.PUT_DOMAIN_POLICY, domainName);
              }
              return awsResponse;
          })
          .progress();
  }

    boolean policyIsUnchanged(final ResourceModel desiredModel, final ResourceModel previousModel) {
        if (previousModel == null) {
            return false;
        }

        if (desiredModel.getPermissionsPolicyDocument() == null || previousModel.getPermissionsPolicyDocument() == null) {
            return false;
        }
        JsonNode desiredPolicy = MAPPER.valueToTree(desiredModel.getPermissionsPolicyDocument());
        JsonNode currentPolicy = MAPPER.valueToTree(previousModel.getPermissionsPolicyDocument());
        return desiredPolicy.equals(currentPolicy);
    }

    protected boolean doesDomainExist(
        final ResourceModel model,
        final ProxyClient<CodeartifactClient> proxyClient,
        ResourceHandlerRequest<ResourceModel> request
    ) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(
                Translator.translateToReadRequest(model, request), proxyClient.client()::describeDomain);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

}
