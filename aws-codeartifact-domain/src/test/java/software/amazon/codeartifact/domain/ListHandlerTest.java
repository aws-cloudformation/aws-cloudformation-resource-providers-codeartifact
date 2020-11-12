package software.amazon.codeartifact.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.model.AccessDeniedException;
import software.amazon.awssdk.services.codeartifact.model.DomainSummary;
import software.amazon.awssdk.services.codeartifact.model.InternalServerException;
import software.amazon.awssdk.services.codeartifact.model.ListDomainsRequest;
import software.amazon.awssdk.services.codeartifact.model.ListDomainsResponse;
import software.amazon.awssdk.services.codeartifact.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    private final DomainSummary domainSummary1 = DomainSummary.builder()
        .name(DOMAIN_NAME)
        .owner(DOMAIN_OWNER)
        .encryptionKey(ENCRYPTION_KEY_ARN)
        .createdTime(NOW)
        .arn(DOMAIN_ARN)
        .build();

    private final DomainSummary domainSummary2 = DomainSummary.builder()
        .name("domain2")
        .owner(DOMAIN_OWNER)
        .encryptionKey(ENCRYPTION_KEY_ARN)
        .createdTime(NOW)
        .arn("domainArn2")
        .build();

    @Test
    public void handleRequest_SimpleSuccess() {
        final ListHandler handler = new ListHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .awsPartition("aws")
            .region("us-west-2")
            .build();

        when(proxy.injectCredentialsAndInvokeV2(any(ListDomainsRequest.class), any())).thenReturn(
            ListDomainsResponse.builder()
            .domains(domainSummary1, domainSummary2)
            .nextToken("fakeNextToken")
            .build()
        );

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_accessDeniedException() {
        final ListHandler handler = new ListHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(proxy.injectCredentialsAndInvokeV2(any(ListDomainsRequest.class), any()))
            .thenThrow(AccessDeniedException.class);

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, null, logger);
            fail("Expected Exception.");
        } catch (CfnAccessDeniedException e) {
            // Expected
        }

    }

    @Test
    public void handleRequest_validationException() {
        final ListHandler handler = new ListHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(proxy.injectCredentialsAndInvokeV2(any(ListDomainsRequest.class), any()))
            .thenThrow(ValidationException.class);

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, null, logger);
            fail("Expected Exception.");
        } catch (CfnInvalidRequestException e) {
            // Expected
        }
    }

    @Test
    public void handleRequest_internalServerException() {
        final ListHandler handler = new ListHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        when(proxy.injectCredentialsAndInvokeV2(any(ListDomainsRequest.class), any()))
            .thenThrow(InternalServerException.class);

        try {
            final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, null, logger);
            fail("Expected Exception.");
        } catch (CfnServiceInternalErrorException e) {
            // Expected
        }

    }
}
