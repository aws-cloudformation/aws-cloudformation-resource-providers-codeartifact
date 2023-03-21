package software.amazon.codeartifact.repository;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
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
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    private final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
        .name(REPO_NAME)
        .administratorAccount(ADMIN_ACCOUNT)
        .arn(REPO_ARN_WITH_DOMAIN_OWNER)
        .description(DESCRIPTION)
        .domainOwner(DOMAIN_OWNER)
        .domainName(DOMAIN_NAME)
        .build();

    private final ResourceModel model = ResourceModel.builder()
        .repositoryName(DOMAIN_NAME)
        .domainOwner(DOMAIN_OWNER)
        .domainName(DOMAIN_OWNER)
        .build();

    private final ResourceModel desiredOutputModel = ResourceModel.builder()
        .domainName(DOMAIN_NAME)
        .domainOwner(DOMAIN_OWNER)
        .arn(REPO_ARN_WITH_DOMAIN_OWNER)
        .repositoryName(REPO_NAME)
        .name(REPO_NAME)
        .description(DESCRIPTION)
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
        final ReadHandler handler = new ReadHandler();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
            .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_success_with_policy() throws JsonProcessingException {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .name(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .description(DESCRIPTION)
            .build();


        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
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
        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }


    @Test
    public void handleRequest_simpleSuccess_onlyArn() {
        final ReadHandler handler = new ReadHandler();

        ResourceModel model = ResourceModel.builder()
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(ResourceNotFoundException.class);
        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ArgumentCaptor<DescribeRepositoryRequest> argumentCaptor = ArgumentCaptor.forClass(DescribeRepositoryRequest.class);

        verify(codeartifactClient).describeRepository(argumentCaptor.capture());

        DescribeRepositoryRequest describeRepositoryRequest = argumentCaptor.getValue();

        assertThat(describeRepositoryRequest.repository()).isEqualTo(REPO_NAME);
        assertThat(describeRepositoryRequest.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(describeRepositoryRequest.domainOwner()).isEqualTo(DOMAIN_OWNER);

        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_success_with_tags() {
        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .tags(RESOURCE_MODEL_TAGS)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .tags(RESOURCE_MODEL_TAGS)
            .repositoryName(REPO_NAME)
            .name(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        final ReadHandler handler = new ReadHandler();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
            .tags(SERVICE_TAGS)
            .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

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

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_conflictException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenThrow(ConflictException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnAlreadyExistsException e) {
            //Expected

        }

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
    }


    @Test
    public void handleRequest_serviceQuotaExceededException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenThrow(
            ServiceQuotaExceededException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceLimitExceededException e) {
            //Expected

        }

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
    }

    @Test
    public void handleRequest_validationException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenThrow(ValidationException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnInvalidRequestException e) {
            //Expected

        }

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
    }


    @Test
    public void handleRequest_internalServerException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenThrow(
            InternalServerException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceInternalErrorException e) {
            //Expected

        }

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
    }

    @Test
    public void handleRequest_notFoundException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnNotFoundException e) {
            //Expected

        }

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
    }
}
