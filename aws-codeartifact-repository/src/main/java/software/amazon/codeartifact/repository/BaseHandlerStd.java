package software.amazon.codeartifact.repository;

import java.util.Set;

import com.amazonaws.util.CollectionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    public static final ObjectMapper MAPPER = new ObjectMapper();

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

  protected ProgressEvent<ResourceModel, CallbackContext> associateExternalConnections(
      final ProgressEvent<ResourceModel, CallbackContext> progress,
      final CallbackContext callbackContext,
      final ResourceHandlerRequest<ResourceModel> request,
      final ProxyClient<CodeartifactClient> proxyClient,
      final Set<String> externalConnectionsToAdd,
      final Logger logger
  ) {
      ResourceModel resourceModel = request.getDesiredResourceState();
      logger.log(String.format("Adding external connections: %s", externalConnectionsToAdd.toString()));
      if (CollectionUtils.isNullOrEmpty(externalConnectionsToAdd)) {
          logger.log("No external connections to add");
          // Nothing to add, continue
          return ProgressEvent.progress(resourceModel, callbackContext);
      }

      // Loop is currently not necessary because only 1 external connection is allowed, leaving this in for
      // when multiple are supported
      externalConnectionsToAdd.forEach(ec -> {
          try {
              AssociateExternalConnectionRequest associateExternalConnectionRequest
                  = Translator.translateAssociateExternalConnectionsRequest(resourceModel, ec);

              proxyClient.injectCredentialsAndInvokeV2(associateExternalConnectionRequest, proxyClient.client()::associateExternalConnection);
          } catch (final AwsServiceException e) {
              String repositoryName = progress.getResourceModel().getRepositoryName();
              Translator.throwCfnException(e, Constants.ASSOCIATE_EXTERNAL_CONNECTION, repositoryName);
          }
          logger.log(String.format("Successfully associated external connection: %s", ec));
      });

      return ProgressEvent.<ResourceModel, CallbackContext>builder()
          .resourceModel(resourceModel)
          .status(OperationStatus.IN_PROGRESS)
          .build();
  }

  protected ProgressEvent<ResourceModel, CallbackContext> putRepositoryPermissionsPolicy(
      final AmazonWebServicesClientProxy proxy,
      final ProgressEvent<ResourceModel, CallbackContext> progress,
      final CallbackContext callbackContext,
      final ResourceHandlerRequest<ResourceModel> request,
      final ProxyClient<CodeartifactClient> proxyClient,
      final Logger logger
  ) {
      final ResourceModel desiredModel = progress.getResourceModel();
      final ResourceModel previousModel = request.getPreviousResourceState();

      if (desiredModel.getPermissionsPolicyDocument() == null || policyIsUnchanged(desiredModel, previousModel)) {
          return ProgressEvent.progress(desiredModel, callbackContext);
      }
      return proxy.initiate(
          "CodeArtifact::PutRepositoryPermissionsPolicy", proxyClient, desiredModel, callbackContext)
          .translateToServiceRequest(Translator::translatePutPermissionsPolicyRequest)
          .makeServiceCall((awsRequest, client) -> {
              PutRepositoryPermissionsPolicyResponse awsResponse = null;
              try {
                  awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::putRepositoryPermissionsPolicy);
                  logger.log("Repository permission policy successfully added.");
              } catch (final AwsServiceException e) {
                  String domainName = desiredModel.getDomainName();
                  Translator.throwCfnException(e, Constants.PUT_REPOSITORY_POLICY, domainName);
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

}
