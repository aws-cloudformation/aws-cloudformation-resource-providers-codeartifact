package software.amazon.codeartifact.packagegroup;

import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.CreatePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.CreatePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupDescription;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.TagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupOriginConfigurationRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupRequest;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

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
            .originConfiguration(PACKAGE_GROUP_ORIGIN_CONFIGURATION)
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

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .region(REGION)
                .awsPartition(PARTITION)
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .build();

        CreatePackageGroupResponse createPackageGroupResponse = CreatePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().createPackageGroup(any(CreatePackageGroupRequest.class))).thenReturn(createPackageGroupResponse);

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertInProgress(response, desiredOutputModel);

        verify(codeartifactClient).createPackageGroup(any(CreatePackageGroupRequest.class));
        verify(codeartifactClient, times(1)).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_StabilizedOnSecondAttempt() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .region(REGION)
                .awsPartition(PARTITION)
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .build();

        CreatePackageGroupResponse createPackageGroupResponse = CreatePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().createPackageGroup(any(CreatePackageGroupRequest.class))).thenReturn(createPackageGroupResponse);

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().build())
                .thenReturn(describePackageGroupResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertInProgress(response, desiredOutputModel);

        verify(codeartifactClient).createPackageGroup(any(CreatePackageGroupRequest.class));
        verify(codeartifactClient, times(2)).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_withOwnerAndMetadata() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .region(REGION)
                .awsPartition(PARTITION)
                .awsAccountId(DOMAIN_OWNER)
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

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_withRootPackageGroup() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel prevModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(ROOT_PACKAGE_GROUP)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .build();

        final ResourceModel inputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(ROOT_PACKAGE_GROUP)
                .contactInfo(UPDATED_CONTACT_INFO)
                .description(UPDATED_DESCRIPTION)
                .tags(RESOURCE_MODEL_TAGS)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .desiredResourceTags(DESIRED_TAGS_MAP)
                .region(REGION)
                .awsPartition(PARTITION)
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(ROOT_PACKAGE_GROUP)
                .arn(ROOT_PACKAGE_GROUP_ARN)
                .description(UPDATED_DESCRIPTION)
                .contactInfo(UPDATED_CONTACT_INFO)
                .originConfiguration(RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        CallbackContext callbackcontext = new CallbackContext();

        ReadHandler readHandler = new ReadHandler();
        ReadHandler spyReadHandler = spy(readHandler);
        handler.readHandler = spyReadHandler;
        doReturn(
                ProgressEvent.success(prevModel, callbackcontext),
                ProgressEvent.success(desiredOutputModel, callbackcontext)
        ).when(spyReadHandler).handleRequest(proxy, request, callbackcontext, proxyClient, logger);

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, callbackcontext, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(0)).createPackageGroup(any(CreatePackageGroupRequest.class));
        verify(codeartifactClient, times(1)).updatePackageGroup(any(UpdatePackageGroupRequest.class));
        verify(codeartifactClient, times(1)).tagResource(any(TagResourceRequest.class));
        verify(codeartifactClient, times(0)).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_withOriginConfiguration() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .region(REGION)
                .awsPartition(PARTITION)
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .description(DESCRIPTION)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .contactInfo(CONTACT_INFO)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_PACKAGE_GROUP_ORIGIN_CONFIGURATION)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .description(DESCRIPTION)
                .contactInfo(CONTACT_INFO)
                .originConfiguration(UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(UPDATED_ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse);

        CallbackContext context = new CallbackContext();
        context.setCreated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(1)).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, times(1)).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_withTags() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .tags(RESOURCE_MODEL_TAGS)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .tags(RESOURCE_MODEL_TAGS)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .description(DESCRIPTION)
                .contactInfo(CONTACT_INFO)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .region(REGION)
                .awsPartition(PARTITION)
                .awsAccountId(DOMAIN_OWNER)
                .build();

        CreatePackageGroupResponse createPackageGroupResponse = CreatePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().createPackageGroup(any(CreatePackageGroupRequest.class))).thenReturn(createPackageGroupResponse);

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(codeartifactClient, times(1)).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
        verify(codeartifactClient, atLeastOnce()).serviceName();

        ArgumentCaptor<CreatePackageGroupRequest> createPackageGroupRequestArgumentCaptor =
                ArgumentCaptor.forClass(CreatePackageGroupRequest.class);
        verify(codeartifactClient).createPackageGroup(createPackageGroupRequestArgumentCaptor.capture());
        CreatePackageGroupRequest createPackageGroupRequestValue = createPackageGroupRequestArgumentCaptor.getValue();

        assertThat(createPackageGroupRequestValue.tags().equals(SERVICE_TAGS));
    }
}
