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
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.RepositoryExternalConnectionInfo;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
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
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_callBackDelayInProgress() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
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

        CallbackContext context = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_SimpleSuccess_callBackDelayInProgress_withTags() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .tags(RESOURCE_MODEL_TAGS)
            .repositoryName(REPO_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .tags(RESOURCE_MODEL_TAGS)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
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

        CallbackContext context = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();


        ArgumentCaptor<CreateRepositoryRequest> createRepositoryRequestArgumentCaptor =
            ArgumentCaptor.forClass(CreateRepositoryRequest.class);

        verify(codeartifactClient).createRepository(createRepositoryRequestArgumentCaptor.capture());
        CreateRepositoryRequest createRepositoryRequestValue = createRepositoryRequestArgumentCaptor.getValue();

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, never()).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));

        assertThat(createRepositoryRequestValue.tags().equals(SERVICE_TAGS));
    }

    @Test
    public void handleRequest_SimpleSuccess_withoutDomainOwner() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);
        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
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
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .upstreams(UPSTREAMS)
            .description(DESCRIPTION)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
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
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        CreateRepositoryResponse createRepositoryResponse = CreateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        GetRepositoryPermissionsPolicyResponse getRepositoryPermissionsPolicyResponse = GetRepositoryPermissionsPolicyResponse.builder()
            .policy(
                ResourcePolicy.builder()
                    .document(MAPPER.writeValueAsString(TEST_POLICY_DOC_0))
                    .build()
            )
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenReturn(getRepositoryPermissionsPolicyResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));

        ArgumentCaptor<PutRepositoryPermissionsPolicyRequest> putRepoPermissionsPolicyRequestArgumentCaptor
            = ArgumentCaptor.forClass(PutRepositoryPermissionsPolicyRequest.class);

        verify(codeartifactClient).putRepositoryPermissionsPolicy(putRepoPermissionsPolicyRequestArgumentCaptor.capture());
        PutRepositoryPermissionsPolicyRequest capturedRequest = putRepoPermissionsPolicyRequestArgumentCaptor.getValue();

        assertThat(capturedRequest.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(capturedRequest.domainOwner()).isEqualTo(DOMAIN_OWNER);
        assertThat(capturedRequest.policyDocument()).isEqualTo(MAPPER.writeValueAsString(TEST_POLICY_DOC_0));

        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
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
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .externalConnections(
                Collections.singletonList(
                    RepositoryExternalConnectionInfo.builder()
                    .externalConnectionName(NPM_EC)
                    .build()
                )
            )
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_withExternalConnections_moreThanOneExternalConnection() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Arrays.asList(NPM_EC, PYPI_EC))
            .description(DESCRIPTION)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);

        verify(codeartifactClient, times(1)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, times(2)).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
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
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .region(REGION)
            .awsPartition(PARTITION)
            .awsAccountId(DOMAIN_OWNER)
            .build();

        assertThrows(CfnInvalidRequestException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
    }
}
