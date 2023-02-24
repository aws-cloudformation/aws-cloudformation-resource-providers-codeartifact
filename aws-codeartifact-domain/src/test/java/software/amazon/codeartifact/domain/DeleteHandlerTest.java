package software.amazon.codeartifact.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.ConflictException;
import software.amazon.awssdk.services.codeartifact.model.DeleteDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeDomainResponse;
import software.amazon.awssdk.services.codeartifact.model.DomainDescription;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ServiceQuotaExceededException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
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
public class DeleteHandlerTest extends AbstractTestBase {

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
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .build();

        DeleteDomainResponse deleteDomainResponse = DeleteDomainResponse.builder()
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenReturn(deleteDomainResponse);

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        // first, when checking if domain exists to be deleted
        // second, to check if domain has been deleted
        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class)))
            .thenReturn(describeDomainResponse)
            .thenThrow(ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(2)).describeDomain(any(DescribeDomainRequest.class));

    }


    @Test
    public void handleRequest_conflictException() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenThrow(ConflictException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        assertThrows(CfnInvalidRequestException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
    }


    @Test
    public void handleRequest_accessDeniedException() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenThrow(AccessDeniedException.class);
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

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_serviceQuotaExceededException() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenThrow(ServiceQuotaExceededException.class);
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

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_validationException() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenThrow(ServiceQuotaExceededException.class);
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

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_internalServerException() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenThrow(InternalServerException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnServiceInternalErrorException e) {
            //Expected

        }

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(1)).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_doesNotExist() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .encryptionKey(ENCRYPTION_KEY_ARN)
            .build();

        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class))).thenThrow(ResourceNotFoundException.class);
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
            fail("Expected Exception");
        } catch (CfnNotFoundException e) {
            //Expected

        }

        verify(codeartifactClient).describeDomain(any(DescribeDomainRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_onlyArn() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
            .arn(DOMAIN_ARN)
            .build();

        DeleteDomainResponse deleteDomainResponse = DeleteDomainResponse.builder()
            .build();

        when(proxyClient.client().deleteDomain(any(DeleteDomainRequest.class))).thenReturn(deleteDomainResponse);

        DescribeDomainResponse describeDomainResponse = DescribeDomainResponse.builder()
            .domain(domainDescription)
            .build();

        // first, when checking if domain exists to be deleted
        // second, to check if domain has been deleted
        when(proxyClient.client().describeDomain(any(DescribeDomainRequest.class)))
            .thenReturn(describeDomainResponse)
            .thenThrow(ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .logicalResourceIdentifier(DOMAIN_ARN)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).deleteDomain(any(DeleteDomainRequest.class));
        verify(codeartifactClient, times(2)).describeDomain(any(DescribeDomainRequest.class));

    }
}
