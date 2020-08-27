package software.amazon.codeartifact.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DomainDescription;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

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
        .domainName(DOMAIN_NAME)
        .domainOwner(DOMAIN_OWNER)
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
        verify(codeartifactClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(codeartifactClient);
    }

    @Test
    public void handleRequest_simpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .build();

        CreateDomainResponse createDomainResponse = CreateDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenReturn(createDomainResponse);

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, times(2)).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient, never()).putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class));

    }

    @Test
    public void handleRequest_simpleSuccess_withEncryptionKey() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        CreateDomainResponse createDomainResponse = CreateDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenReturn(createDomainResponse);

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ArgumentCaptor<CreateDomainRequest> createDomainRequestArgumentCaptor = ArgumentCaptor.forClass(CreateDomainRequest.class);

        verify(codeartifactClient).createDomain(createDomainRequestArgumentCaptor.capture());
        verify(codeartifactClient, times(2)).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient, never()).putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class));

        CreateDomainRequest createDomainRequestValue = createDomainRequestArgumentCaptor.getValue();

        assertThat(createDomainRequestValue.encryptionKey()).isEqualTo(ENCRYPTION_KEY_ARN);
        assertThat(createDomainRequestValue.domain()).isEqualTo(DOMAIN_NAME);
    }

    @Test
    public void handleRequest_withDomainPolicy() throws JsonProcessingException {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .build();

        CreateDomainResponse createDomainResponse = CreateDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenReturn(createDomainResponse);
        when(proxyClient.client().putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class))).thenReturn(
            PutDomainPermissionsPolicyResponse.builder().build()
        );

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, times(2)).describeDomain(any(DescribeDomainRequest.class));

        ArgumentCaptor<PutDomainPermissionsPolicyRequest> putDomainPermissionsPolicyRequestArgumentCaptor
            = ArgumentCaptor.forClass(PutDomainPermissionsPolicyRequest.class);

        verify(codeartifactClient).putDomainPermissionsPolicy(putDomainPermissionsPolicyRequestArgumentCaptor.capture());
        PutDomainPermissionsPolicyRequest capturedRequest = putDomainPermissionsPolicyRequestArgumentCaptor.getValue();

        assertThat(capturedRequest.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(capturedRequest.policyDocument()).isEqualTo(MAPPER.writeValueAsString(TEST_POLICY_DOC));
    }


    @Test
    public void handleRequest_domainAlreadyExists() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenThrow(ConflictException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnAlreadyExistsException e) {
            //Expected

        }

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, never()).describeDomain(any(DescribeDomainRequest.class));
    }


    @Test
    public void handleRequest_accessDeniedException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenThrow(AccessDeniedException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnAccessDeniedException e) {
            //Expected

        }

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, never()).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_serviceQuotaExceededException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenThrow(ServiceQuotaExceededException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceLimitExceededException e) {
            //Expected

        }

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, never()).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_validationException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenThrow(ValidationException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnInvalidRequestException e) {
            //Expected

        }

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, never()).describeDomain(any(DescribeDomainRequest.class));
    }


    @Test
    public void handleRequest_internalServerException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().createDomain(any(CreateDomainRequest.class))).thenThrow(InternalServerException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnGeneralServiceException e) {
            //Expected

        }

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, never()).describeDomain(any(DescribeDomainRequest.class));
    }

}
