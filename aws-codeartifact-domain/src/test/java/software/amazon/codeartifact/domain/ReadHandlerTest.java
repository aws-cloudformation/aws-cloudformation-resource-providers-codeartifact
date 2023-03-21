package software.amazon.codeartifact.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DomainDescription;
import software.amazon.awssdk.services.codeartifact.model.GetDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.GetDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    private final ResourceModel model = ResourceModel.builder()
        .domainName(DOMAIN_NAME)
        .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        codeartifactClient = mock(CodeartifactClient.class);
        proxyClient = MOCK_PROXY(proxy, codeartifactClient);
    }

    @AfterEach
    public void tear_down() {
        verify(codeartifactClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(codeartifactClient);
    }

    @Test
    public void handleRequest_simpleSuccess() {
        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .build();

        final ReadHandler handler = new ReadHandler();

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(
                DomainDescription.builder()
                    .name(DOMAIN_NAME)
                    .owner(DOMAIN_OWNER)
                    .arn(DOMAIN_ARN)
                    .repositoryCount(REPO_COUNT)
                    .assetSizeBytes((long) ASSET_SIZE)
                    .status(STATUS)
                    .createdTime(NOW)
                    .encryptionKey(ENCRYPTION_KEY_ARN)
                    .build()
            )
            .build();

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
            .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .owner(DOMAIN_OWNER)
            .name(DOMAIN_NAME)
            .arn(DOMAIN_ARN)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient).getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_success_with_policy() throws JsonProcessingException {
        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .build();

        final ReadHandler handler = new ReadHandler();

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(
                DomainDescription.builder()
                    .name(DOMAIN_NAME)
                    .owner(DOMAIN_OWNER)
                    .arn(DOMAIN_ARN)
                    .repositoryCount(REPO_COUNT)
                    .assetSizeBytes((long) ASSET_SIZE)
                    .status(STATUS)
                    .createdTime(NOW)
                    .encryptionKey(ENCRYPTION_KEY_ARN)
                    .build()
            )
            .build();

        GetDomainPermissionsPolicyResponse getDomainPermissionsPolicyResponse = GetDomainPermissionsPolicyResponse.builder()
            .policy(
                ResourcePolicy.builder()
                .document(MAPPER.writeValueAsString(TEST_POLICY_DOC))
                .build()
            )
            .build();

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
            .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class))).thenReturn(getDomainPermissionsPolicyResponse);
        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .owner(DOMAIN_OWNER)
            .name(DOMAIN_NAME)
            .arn(DOMAIN_ARN)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient).getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_success_with_tags(){
        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .tags(RESOURCE_MODEL_TAGS)
            .build();

        final ReadHandler handler = new ReadHandler();

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(
                DomainDescription.builder()
                    .name(DOMAIN_NAME)
                    .owner(DOMAIN_OWNER)
                    .arn(DOMAIN_ARN)
                    .repositoryCount(REPO_COUNT)
                    .assetSizeBytes((long) ASSET_SIZE)
                    .status(STATUS)
                    .createdTime(NOW)
                    .encryptionKey(ENCRYPTION_KEY_ARN)
                    .build()
            )
            .build();

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
            .tags(SERVICE_TAGS)
            .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .owner(DOMAIN_OWNER)
            .name(DOMAIN_NAME)
            .arn(DOMAIN_ARN)
            .tags(RESOURCE_MODEL_TAGS)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient).getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }


    @Test
    public void handleRequest_withOnlyArn() {
        final ReadHandler handler = new ReadHandler();

        ResourceModel model = ResourceModel.builder()
            .arn(DOMAIN_ARN)
            .build();

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(
                DomainDescription.builder()
                    .name(DOMAIN_NAME)
                    .owner(DOMAIN_OWNER)
                    .arn(DOMAIN_ARN)
                    .repositoryCount(REPO_COUNT)
                    .assetSizeBytes((long) ASSET_SIZE)
                    .status(STATUS)
                    .createdTime(NOW)
                    .encryptionKey(ENCRYPTION_KEY_ARN)
                    .build()
            )
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
            .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .name(DOMAIN_NAME)
            .owner(DOMAIN_OWNER)
            .arn(DOMAIN_ARN)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ArgumentCaptor<DescribeDomainRequest> argumentCaptor = ArgumentCaptor.forClass(DescribeDomainRequest.class);
        verify(codeartifactClient).describeDomain(argumentCaptor.capture());

        DescribeDomainRequest describeDomainRequest = argumentCaptor.getValue();
        assertThat(describeDomainRequest.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(describeDomainRequest.domainOwner()).isEqualTo(DOMAIN_OWNER);

        verify(codeartifactClient).getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    }

    @Test
    public void handleRequest_accessDeniedException() {
        final ReadHandler handler = new ReadHandler();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenThrow(AccessDeniedException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected ConflictException");
        } catch (CfnAccessDeniedException e) {
            //Expected

        }

        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_validationException() {
        final ReadHandler handler = new ReadHandler();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenThrow(ServiceQuotaExceededException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected ConflictException");
        } catch (CfnServiceLimitExceededException e) {
            //Expected

        }
        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
    }


    @Test
    public void handleRequest_internalServerException() {
        final ReadHandler handler = new ReadHandler();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenThrow(InternalServerException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected ConflictException");
        } catch (CfnServiceInternalErrorException e) {
            //Expected

        }
        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_resourceNotFound() {
        final ReadHandler handler = new ReadHandler();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenThrow(ResourceNotFoundException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected ConflictException");
        } catch (CfnNotFoundException e) {
            //Expected

        }
        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
    }

}
