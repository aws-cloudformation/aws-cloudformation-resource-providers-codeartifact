package software.amazon.codeartifact.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DeleteDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DomainDescription;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    private final DomainDescription domainDescription = DomainDescription.builder()
        .name(DOMAIN_NAME)
        .owner(DOMAIN_OWNER)
        .arn(DOMAIN_ARN)
        .repositoryCount(REPO_COUNT)
        .assetSizeBytes((long) ASSET_SIZE)
        .status(STATUS)
        .createdTime(NOW)
        .encryptionKey(ENCRYPTION_KEY_ARN)
        .build();

    private final ResourceModel desiredOutputModel = ResourceModel.builder()
        .owner(DOMAIN_OWNER)
        .name(DOMAIN_NAME)
        .arn(DOMAIN_ARN)
        .encryptionKey(ENCRYPTION_KEY_ARN)
        .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        codeartifactClient = mock(CodeartifactClient.class);
        proxyClient = MOCK_PROXY(proxy, codeartifactClient);
    }

    @AfterEach
    public void tear_down() {
        verifyNoMoreInteractions(codeartifactClient);
    }

    @Test
    public void handleRequest_simpleSuccess() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .build();

        final ResourceModel previousModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .build();

        PutDomainPermissionsPolicyResponse putDomainPermissionsPolicyResponse = PutDomainPermissionsPolicyResponse.builder()
            .policy(
                ResourcePolicy.builder()
                    .document(MAPPER.writeValueAsString(TEST_POLICY_DOC))
                    .build()
            )
            .build();

        when(proxyClient.client().putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class))).thenReturn(putDomainPermissionsPolicyResponse);

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient).putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class));

        verify(codeartifactClient, atLeastOnce()).serviceName();
    }


    @Test
    public void handleRequest_deleteDomainPermissionPolicy() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .build();

        final ResourceModel previousModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .build();

        DeleteDomainPermissionsPolicyResponse deleteDomainPermissionsPolicyResponse = DeleteDomainPermissionsPolicyResponse.builder()
            .policy(
                ResourcePolicy.builder()
                    .document(MAPPER.writeValueAsString(TEST_POLICY_DOC))
                    .build()
            )
            .build();

        when(proxyClient.client().deleteDomainPermissionsPolicy(any(DeleteDomainPermissionsPolicyRequest.class)))
            .thenReturn(deleteDomainPermissionsPolicyResponse);

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).deleteDomainPermissionsPolicy(any(DeleteDomainPermissionsPolicyRequest.class));

        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_throwsCfnNotUpdatableException() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceModel previousModel = ResourceModel.builder()
            .domainName("different-domain-name")
            .domainOwner(DOMAIN_OWNER)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

        assertThrows(CfnNotUpdatableException.class,() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));
    }
}
