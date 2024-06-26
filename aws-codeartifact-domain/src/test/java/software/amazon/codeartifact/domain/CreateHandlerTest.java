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
import software.amazon.awssdk.services.codeartifact.model.GetDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.GetDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.PutDomainPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
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
        .name(DOMAIN_NAME)
        .owner(DOMAIN_OWNER)
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

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .arn(DOMAIN_ARN)
            .build();

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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient, never()).putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class));

    }

    @Test
    public void handleRequest_simpleSuccess_with_tags() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .arn(DOMAIN_ARN)
            .tags(RESOURCE_MODEL_TAGS)
            .build();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .tags(RESOURCE_MODEL_TAGS)
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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();


        ArgumentCaptor<CreateDomainRequest> createDomainRequestArgumentCaptor = ArgumentCaptor.forClass(CreateDomainRequest.class);

        verify(codeartifactClient).createDomain(createDomainRequestArgumentCaptor.capture());
        CreateDomainRequest createDomainRequestValue = createDomainRequestArgumentCaptor.getValue();

        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient, never()).putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class));

        assertThat(createDomainRequestValue.tags().equals(SERVICE_TAGS));

    }

    @Test
    public void handleRequest_simpleSuccess_retried() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .build();

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        when(proxyClient.client().getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
            .build();

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient, never()).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient).getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient, never()).putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    }

    @Test
    public void handleRequest_simpleSuccess_withEncryptionKey() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .arn(DOMAIN_ARN)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ArgumentCaptor<CreateDomainRequest> createDomainRequestArgumentCaptor = ArgumentCaptor.forClass(CreateDomainRequest.class);

        verify(codeartifactClient).createDomain(createDomainRequestArgumentCaptor.capture());
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
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

        when(proxyClient.client().putDomainPermissionsPolicy(any(PutDomainPermissionsPolicyRequest.class))).thenReturn(
            PutDomainPermissionsPolicyResponse.builder().build()
        );

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();


        GetDomainPermissionsPolicyResponse getDomainPermissionsPolicyResponse = GetDomainPermissionsPolicyResponse.builder()
            .policy(
                ResourcePolicy.builder()
                    .document(MAPPER.writeValueAsString(TEST_POLICY_DOC))
                    .build()
            )
            .build();

        when(proxyClient.client().getDomainPermissionsPolicy(any(GetDomainPermissionsPolicyRequest.class))).thenReturn(getDomainPermissionsPolicyResponse);
        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenReturn(describeDomainResponse);

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setCreated(true);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .name(DOMAIN_NAME)
            .owner(DOMAIN_OWNER)
            .permissionsPolicyDocument(TEST_POLICY_DOC)
            .arn(DOMAIN_ARN)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
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
            .awsPartition("aws")
            .region("region")
            .awsAccountId("accountId")
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceInternalErrorException e) {
            //Expected

        }

        verify(codeartifactClient).createDomain(any(CreateDomainRequest.class));
        verify(codeartifactClient, never()).describeDomain(any(DescribeDomainRequest.class));
    }

}
