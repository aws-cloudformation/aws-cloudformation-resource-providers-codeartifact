package software.amazon.codeartifact.packagegroup;

import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupDescription;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    private final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
            .arn(PGC_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .contactInfo(CONTACT_INFO)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .pattern(PACKAGE_GROUP_PATTERN)
            .originConfiguration(PACKAGE_GROUP_ORIGIN_CONFIGURATION)
            .build();

    private final ResourceModel model = ResourceModel.builder()
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .pattern(PACKAGE_GROUP_PATTERN)
            .arn(PGC_ARN_WITH_DOMAIN_OWNER)
            .build();

    private final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .pattern(PACKAGE_GROUP_PATTERN)
            .arn(PGC_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .contactInfo(CONTACT_INFO)
            .originConfiguration(RESOURCE_MODEL_ORIGIN_CONFIGURATION)
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
        final ReadHandler handler = new ReadHandler();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
                .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .logicalResourceIdentifier(PGC_ARN_WITH_DOMAIN_OWNER)
                .awsPartition("aws")
                .region("us-west-2")
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_onlyArn() {
        final ReadHandler handler = new ReadHandler();

        ResourceModel model = ResourceModel.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
                .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .logicalResourceIdentifier(PGC_ARN_WITH_DOMAIN_OWNER)
                .awsPartition("aws")
                .region("us-west-2")
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ArgumentCaptor<DescribePackageGroupRequest> argumentCaptor = ArgumentCaptor.forClass(DescribePackageGroupRequest.class);

        verify(codeartifactClient).describePackageGroup(argumentCaptor.capture());

        DescribePackageGroupRequest describePackageGroupRequest = argumentCaptor.getValue();

        assertThat(describePackageGroupRequest.packageGroup()).isEqualTo(PACKAGE_GROUP_PATTERN);
        assertThat(describePackageGroupRequest.domain()).isEqualTo(DOMAIN_NAME);
        assertThat(describePackageGroupRequest.domainOwner()).isEqualTo(DOMAIN_OWNER);

        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_withTags() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .tags(RESOURCE_MODEL_TAGS)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .description(DESCRIPTION)
                .contactInfo(CONTACT_INFO)
                .originConfiguration(RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .tags(RESOURCE_MODEL_TAGS)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = ListTagsForResourceResponse.builder()
                .tags(SERVICE_TAGS)
                .build();

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .awsPartition("aws")
                .region("us-west-2")
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }
}
