package software.amazon.codeartifact.domain;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.GetDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import java.util.List;

public class ReadHandler extends BaseHandlerStd {

    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<CodeartifactClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        logger.log(String.format("%s read handler is being invoked", ResourceModel.TYPE_NAME));
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> describeDomain(proxy, progress, request, proxyClient))
            .then(progress -> getDomainPolicy(proxy, progress, request, proxyClient))
            .then(progress -> listTags(proxy, progress, request, proxyClient))
            .then(progress -> {
                final ResourceModel model = progress.getResourceModel();
                return ProgressEvent.defaultSuccessHandler(model);
            });
    }

    private ProgressEvent<ResourceModel, CallbackContext> listTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Domain::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToListTagsRequest)
            .makeServiceCall((awsRequest, client) -> {
                logger.log(String.format("%s ListTags is being invoked", ResourceModel.TYPE_NAME));

                ListTagsForResourceResponse listTagsResponse = null;
                try {
                    listTagsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::listTagsForResource);
                } catch (final AwsServiceException e) {
                    String domainName = request.getDesiredResourceState().getDomainName();
                    Translator.throwCfnException(e, Constants.LIST_TAGS_FOR_RESOURCE, domainName);
                }
                logger.log(String.format("Tags of %s has successfully been read.", ResourceModel.TYPE_NAME));
                return listTagsResponse;
            })
            .done((listTagsForResourceRequest, listTagsResponse, proxyInvocation, resourceModel, context) -> {
                if (listTagsResponse != null) {
                    List<Tag> tags = listTagsResponse.tags();
                    resourceModel.setTags(Translator.fromListTagsResponse(tags));
                }
                return ProgressEvent.progress(resourceModel, context);
            });

    }

    private ProgressEvent<ResourceModel, CallbackContext> getDomainPolicy(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Domain::GetDomainPolicy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateGetDomainPermissionsPolicyRequest)
            .makeServiceCall((awsRequest, client) -> {
                logger.log(String.format("%s getDomainPolicy is being invoked", ResourceModel.TYPE_NAME));

                GetDomainPermissionsPolicyResponse getDomainPermissionsPolicyResponse = null;
                try {
                    getDomainPermissionsPolicyResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::getDomainPermissionsPolicy);
                } catch (final ResourceNotFoundException e) {
                    // Do nothing since there is no policy
                } catch (final AwsServiceException e) {
                    String domainName = request.getDesiredResourceState().getDomainName();
                    Translator.throwCfnException(e, Constants.GET_DOMAIN_PERMISSION_POLICY, domainName);
                }
                logger.log(String.format("Domain policy of %s has successfully been read.", ResourceModel.TYPE_NAME));
                return getDomainPermissionsPolicyResponse;
            })
            .done((getDomainPermissionsPolicyRequest, getDomainPermissionsPolicyResponse, proxyInvocation, resourceModel, context) -> {
                    if (getDomainPermissionsPolicyResponse != null) {
                        String domainPolicy = getDomainPermissionsPolicyResponse.policy().document();
                        resourceModel.setPermissionsPolicyDocument(Translator.deserializePolicy(domainPolicy));
                    }
                    return ProgressEvent.progress(resourceModel, context);
                });
    }

    private ProgressEvent<ResourceModel, CallbackContext> describeDomain(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<CodeartifactClient> proxyClient
    ) {
        return proxy.initiate("AWS-CodeArtifact-Domain::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .translateToServiceRequest(Translator::translateToReadRequest)
            .makeServiceCall((awsRequest, client) -> {
                logger.log(String.format("%s describeDomain is being invoked", ResourceModel.TYPE_NAME));
                DescribeDomainResponse awsResponse = null;
                try {
                    awsResponse = client.injectCredentialsAndInvokeV2(awsRequest, proxyClient.client()::describeDomain);
                } catch (final AwsServiceException e) {
                    String domainName = request.getDesiredResourceState().getDomainName();
                    Translator.throwCfnException(e, Constants.DESCRIBE_DOMAIN, domainName);
                }
                logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                return awsResponse;
            })
            .done((describeDomainRequest, describeDomainResponse, proxyInvocation, resourceModel, context) ->
                ProgressEvent.progress(Translator.translateFromReadResponse(describeDomainResponse), context));
    }
}
