package software.amazon.codeartifact.packagegroup;

import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DeletePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DeletePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupDescription;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest extends AbstractTestBase{

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    private final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
            .arn(PGC_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .contactInfo(CONTACT_INFO)
            .pattern(PACKAGE_GROUP_PATTERN)
            .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        codeartifactClient = mock(CodeartifactClient.class);
        proxyClient = MOCK_PROXY(proxy, codeartifactClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .build();

        DeletePackageGroupResponse deletePackageGroupResponse = DeletePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().deletePackageGroup(any(DeletePackageGroupRequest.class))).thenReturn(deletePackageGroupResponse);

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        // first, when checking if package group exists to be deleted
        // second, to check if package group has been deleted
        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class)))
                .thenReturn(describePackageGroupResponse)
                .thenThrow(ResourceNotFoundException.class);


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).deletePackageGroup(any(DeletePackageGroupRequest.class));
        verify(codeartifactClient, times(2)).describePackageGroup(any(DescribePackageGroupRequest.class));
    }

    @Test
    public void handleRequest_keepRootPackageGroup() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(ROOT_PACKAGE_GROUP)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient, times(0)).deletePackageGroup(any(DeletePackageGroupRequest.class));
        verify(codeartifactClient, times(0)).describePackageGroup(any(DescribePackageGroupRequest.class));
    }

    @Test
    public void handleRequest_onlyArn() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .build();

        DeletePackageGroupResponse deletePackageGroupResponse = DeletePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().deletePackageGroup(any(DeletePackageGroupRequest.class))).thenReturn(deletePackageGroupResponse);

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class)))
                .thenReturn(describePackageGroupResponse)
                .thenThrow(ResourceNotFoundException.class);


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).deletePackageGroup(any(DeletePackageGroupRequest.class));
        verify(codeartifactClient, times(2)).describePackageGroup(any(DescribePackageGroupRequest.class));

        ArgumentCaptor<DeletePackageGroupRequest> deletePGRequestArgumentCaptor =
                ArgumentCaptor.forClass(DeletePackageGroupRequest.class);
        verify(codeartifactClient, times(1)).deletePackageGroup(deletePGRequestArgumentCaptor.capture());
        DeletePackageGroupRequest deletePGRequestArgumentCaptorValue = deletePGRequestArgumentCaptor.getValue();

        assertThat(deletePGRequestArgumentCaptorValue.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(deletePGRequestArgumentCaptorValue.packageGroup()).isEqualTo(PACKAGE_GROUP_PATTERN);
    }

    @Test
    public void handleRequest_notFound() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class)))
                .thenThrow(ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThrows(CfnNotFoundException.class, () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));

        verify(codeartifactClient, never()).deletePackageGroup(any(DeletePackageGroupRequest.class));
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
    }
}
