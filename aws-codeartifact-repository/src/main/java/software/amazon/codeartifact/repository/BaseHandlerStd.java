package software.amazon.codeartifact.repository;

import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.util.CollectionUtils;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.RepositoryExternalConnectionInfo;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

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

      if (CollectionUtils.isNullOrEmpty(externalConnectionsToAdd)) {
          // Nothing to add, continue
          return ProgressEvent.progress(resourceModel, callbackContext);
      }

      if (externalConnectionsToAdd.size() > 1) {
          return ProgressEvent.failed(resourceModel, callbackContext, HandlerErrorCode.InvalidRequest,
              "ExternalConnections for repositories are currently limited to 1.");
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
      final ProxyClient<CodeartifactClient> proxyClient,
      final Logger logger
  ) {
      final ResourceModel desiredModel = progress.getResourceModel();
      if (desiredModel.getPolicyDocument() == null) {
          return ProgressEvent.progress(desiredModel, callbackContext);
      }
      return proxy.initiate("AWS-CodeArtifact-Repository::Update::PutRepositoryPermissionsPolicy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
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
          .stabilize((awsRequest, awsResponse, client, model, context) -> policyIsStabilized(model, client))
          .progress();
  }

  private boolean policyIsStabilized(final ResourceModel model, final ProxyClient<CodeartifactClient> proxyClient) {
      try {
          proxyClient.injectCredentialsAndInvokeV2(Translator.translateToGetRepositoryPermissionsPolicy(model), proxyClient.client()::getRepositoryPermissionsPolicy);
          return true;
      } catch (ResourceNotFoundException e) {
          return false;
      }
  }

}
