package software.amazon.codeartifact.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepositoryInfo;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
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
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenReturn(createRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_withUpstreams() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .upstreams(UPSTREAMS)
            .description(DESCRIPTION)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .name(REPO_NAME)
            .arn(REPO_ARN)
            .upstreams(UPSTREAMS)
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .upstreams(
                UpstreamRepositoryInfo.builder().repositoryName(UPSTREAM_0).build(),
                UpstreamRepositoryInfo.builder().repositoryName(UPSTREAM_1).build()
            )
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenReturn(createRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_withRepoPolicy() throws JsonProcessingException {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .description(DESCRIPTION)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenReturn(createRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));

        ArgumentCaptor<PutRepositoryPermissionsPolicyRequest> putRepoPermissionsPolicyRequestArgumentCaptor
            = ArgumentCaptor.forClass(PutRepositoryPermissionsPolicyRequest.class);

        verify(codeartifactClient).putRepositoryPermissionsPolicy(putRepoPermissionsPolicyRequestArgumentCaptor.capture());
        PutRepositoryPermissionsPolicyRequest capturedRequest = putRepoPermissionsPolicyRequestArgumentCaptor.getValue();

        assertThat(capturedRequest.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(capturedRequest.domainOwner()).isEqualTo(DOMAIN_OWNER);
        assertThat(capturedRequest.policyDocument()).isEqualTo(MAPPER.writeValueAsString(TEST_POLICY_DOC_0));

        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_withExternalConnections_happycase() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenReturn(createRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_withExternalConnections_moreThanOneExternalConnection() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Arrays.asList("public:npmjs", "public:pypi"))
            .description(DESCRIPTION)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenReturn(createRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, times(2)).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }


    @Test
    public void handleRequest_conflictException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(ConflictException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnAlreadyExistsException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }


    @Test
    public void handleRequest_serviceQuotaExceededException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(ServiceQuotaExceededException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnServiceLimitExceededException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_validationException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(ValidationException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnInvalidRequestException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }


    @Test
    public void handleRequest_internalServerException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(InternalServerException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnServiceInternalErrorException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_accessDeniedException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(AccessDeniedException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnAccessDeniedException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_notFoundException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnNotFoundException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_generalException() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().createRepository(any(CreateRepositoryRequest.class))).thenThrow(AwsServiceException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnGeneralServiceException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_invalidRequest_readOnlyProperties() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .administratorAccount("12345")
            .repositoryName(REPO_NAME)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        assertThrows(CfnInvalidRequestException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
    }
}
