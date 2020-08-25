package software.amazon.codeartifact.repository;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.util.CollectionUtils;
import com.google.common.collect.Sets;

import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DisassociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel desiredModel = request.getDesiredResourceState();
        final ResourceModel prevModel = request.getPreviousResourceState();

        ProgressEvent<ResourceModel, CallbackContext> updateRepositoryConnectionsEvent;
        if (ComparisonUtils.willAddUpstreams(desiredModel, prevModel)) {
            // If adding upstreams, first remove externalConnections in updateExternalConnections(), then add upstreams
            // to avoid adding an upstream before removing an external connection
            updateRepositoryConnectionsEvent = ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> updateExternalConnections(request, callbackContext, proxyClient, logger))
                .then(progress -> updateRepository(proxy, desiredModel, prevModel, progress, callbackContext, proxyClient, logger));
        } else {
            // If removing upstreams, do it in updateRepository() before adding external connections in
            // updateExternalConnections(). This is in the case that we are replacing upstreams with external
            // connections to avoid adding an external connection before the upstream is removed
            updateRepositoryConnectionsEvent =  ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> updateRepository(proxy, desiredModel, prevModel, progress, callbackContext, proxyClient, logger))
                .then(progress -> updateExternalConnections(request, callbackContext, proxyClient, logger));
        }
        return updateRepositoryConnectionsEvent
            .then(progress -> updateRepositoryPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger))
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    protected ProgressEvent<ResourceModel, CallbackContext> updateExternalConnections(
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger
    ) {
        ResourceModel desiredModel = request.getDesiredResourceState();
        ResourceModel previousModel = request.getPreviousResourceState();

        Set<String> requestedExternalConnections = Translator.streamOfOrEmpty(desiredModel.getExternalConnections())
            .collect(Collectors.toSet());

        Set<String> currentExternalConnections = Translator.streamOfOrEmpty(previousModel.getExternalConnections())
            .collect(Collectors.toSet());

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> {
                Set<String> externalConnectionsToRemove = Sets.difference(currentExternalConnections, requestedExternalConnections);
                return disassociateExternalConnections(progress, callbackContext, request, proxyClient, externalConnectionsToRemove, logger);
            })
            .then(progress -> {
                Set<String> externalConnectionsToAdd = Sets.difference(requestedExternalConnections, currentExternalConnections);
                return associateExternalConnections(progress, callbackContext, request, proxyClient, externalConnectionsToAdd, logger);
            });
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateRepository(
        final AmazonWebServicesClientProxy proxy,
        final ResourceModel desiredModel,
        final ResourceModel previousModel,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        Logger logger
    ) {

        if (ComparisonUtils.upstreamsAreEqual(desiredModel, previousModel) &&
            ComparisonUtils.willNotUpdateDescription(desiredModel, previousModel)) {
            return ProgressEvent.progress(desiredModel, callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-Repository::Update", proxyClient,progress.getResourceModel(), callbackContext)
            .translateToServiceRequest((model) -> Translator.translateToUpdateRepository(model, previousModel))
            .makeServiceCall((awsRequest, client) -> {
                AwsResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::updateRepository);
                } catch (final AwsServiceException e) {
                    String repositoryName = progress.getResourceModel().getRepositoryName();
                    Translator.throwCfnException(e, Constants.UPDATE_REPOSITORY, repositoryName);
                }
                logger.log(String.format("%s successfully updated.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            .progress();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> updateRepositoryPermissionsPolicy(
        final AmazonWebServicesClientProxy proxy,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ResourceHandlerRequest<ResourceModel> request,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger
    ) {
        final ResourceModel desiredModel = progress.getResourceModel();
        if (desiredModel.getPermissionsPolicyDocument() != null) {
            return putRepositoryPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger);
        }
        return deleteRepositoryPermissionsPolicy(proxy, progress, callbackContext, request, proxyClient, logger);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> deleteRepositoryPermissionsPolicy(
        final AmazonWebServicesClientProxy proxy,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ResourceHandlerRequest<ResourceModel> request,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger
    ) {
        final ResourceModel desiredModel = request.getDesiredResourceState();
        final ResourceModel previousModel = request.getPreviousResourceState();

        if (desiredModel.getPermissionsPolicyDocument() != null || previousModel.getPermissionsPolicyDocument() == null) {
            return ProgressEvent.progress(desiredModel, callbackContext);
        }

        return proxy.initiate("AWS-CodeArtifact-Repository::Update::DeleteRepositoryPermissionsPolicy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateDeletePermissionsPolicyRequest)
            .makeServiceCall((awsRequest, client) -> {
                DeleteRepositoryPermissionsPolicyResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, client.client()::deleteRepositoryPermissionsPolicy);
                    logger.log("Repository permission policy successfully deleted.");
                } catch (final AwsServiceException e) {
                    String domainName = desiredModel.getDomainName();
                    Translator.throwCfnException(e, Constants.DELETE_REPOSITORY_POLICY, domainName);
                }
                return awsResponse;
            })
            .progress();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> disassociateExternalConnections(
        ProgressEvent<ResourceModel, CallbackContext> progress,
        final CallbackContext callbackContext,
        final ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient,
        Set<String> externalConnectionsToRemove,
        Logger logger
    ) {
        ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Removing external connections: %s", externalConnectionsToRemove.toString()));

        if (CollectionUtils.isNullOrEmpty(externalConnectionsToRemove)) {
            logger.log("No external connections to remove.");
            // Nothing to remove, continue
            return ProgressEvent.progress(model, callbackContext);
        }

        // Loop is currently not necessary because only 1 external connection is allowed, leaving this in for
        // when multiple are supported
        externalConnectionsToRemove.forEach(ec -> {
            try {
                DisassociateExternalConnectionRequest disassociateExternalConnectionRequest = Translator.translateDisassociateExternalConnectionsRequest(model, ec);
                proxyClient.injectCredentialsAndInvokeV2(disassociateExternalConnectionRequest, proxyClient.client()::disassociateExternalConnection);
            } catch (final ResourceNotFoundException e) {
                // External Connection has already been removed or doesn't exist
            } catch (final AwsServiceException e) {
                String repositoryName = progress.getResourceModel().getRepositoryName();
                Translator.throwCfnException(e, Constants.DISASSOCIATE_EXTERNAL_CONNECTION, repositoryName);
            }
            logger.log(String.format("Successfully disassociated external connection: %s", ec));
        });

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModel(model)
            .status(OperationStatus.IN_PROGRESS)
            .build();
    }

}
