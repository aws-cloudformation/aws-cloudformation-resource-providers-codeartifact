package software.amazon.codeartifact.repository;

import java.time.Duration;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.CreateRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DomainDescription;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    private final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
        .name(REPO_NAME)
        .administratorAccount(ADMIN_ACCOUNT)
        .arn(REPO_ARN)
        .description(DESCRIPTION)
        .domainOwner(DOMAIN_OWNER)
        .domainName(DOMAIN_NAME)
        .build();

    private final ResourceModel desiredOutputModel = ResourceModel.builder()
        .domainName(DOMAIN_NAME)
        .domainOwner(DOMAIN_OWNER)
        .repositoryName(REPO_NAME)
        .arn(REPO_ARN)
        .description(DESCRIPTION)
        .administratorAccount(ADMIN_ACCOUNT)
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
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
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
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
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

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnAlreadyExistsException e) {
            //Expected

        }

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
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

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceLimitExceededException e) {
            //Expected

        }

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
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

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnInvalidRequestException e) {
            //Expected

        }

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
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

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceInternalErrorException e) {
            //Expected

        }

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
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

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnNotFoundException e) {
            //Expected

        }

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
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

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnGeneralServiceException e) {
            //Expected

        }

        verify(codeartifactClient).createRepository(any(CreateRepositoryRequest.class));
        verify(codeartifactClient, never()).describeRepository(any(DescribeRepositoryRequest.class));
    }
}
