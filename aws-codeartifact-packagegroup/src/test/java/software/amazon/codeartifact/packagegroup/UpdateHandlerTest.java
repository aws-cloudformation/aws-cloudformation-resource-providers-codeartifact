package software.amazon.codeartifact.packagegroup;

import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribePackageGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupRequest;
import software.amazon.awssdk.services.codeartifact.model.ListAllowedRepositoriesForGroupResponse;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupDescription;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupOriginConfigurationRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdatePackageGroupRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase{

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

    @Test
    public void handleRequest_SimpleSuccess_updatedContactAndDescription() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .contactInfo(UPDATED_CONTACT_INFO)
                .description(UPDATED_DESCRIPTION)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .contactInfo(UPDATED_CONTACT_INFO)
                .description(UPDATED_DESCRIPTION)
                .originConfiguration(RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .contactInfo(UPDATED_CONTACT_INFO)
                .description(UPDATED_DESCRIPTION)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(PACKAGE_GROUP_ORIGIN_CONFIGURATION)
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

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(resourceModel(DESCRIPTION, CONTACT_INFO))
                .awsPartition("aws")
                .region("us-west-2")
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient).updatePackageGroup(any(UpdatePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_updatedOriginConfigurationWithPublishTypeAndEmptyList() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(PUBLISH_ONLY_EMPTY_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .originConfiguration(PUBLISH_ONLY_EMPTY_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(PUBLISH_ONLY_EMPTY_LIST_PACKAGE_GROUP_ORIGIN_CONFIGURATION)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(Collections.emptyList())
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));
        verify(codeartifactClient, times(1)).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_updatedOriginConfigurationWithAllTypes() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .originConfiguration(UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .contactInfo(CONTACT_INFO)
                .description(DESCRIPTION)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_PACKAGE_GROUP_ORIGIN_CONFIGURATION)
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

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));
        verify(codeartifactClient, times(1)).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
    }

    @Test
    public void handleRequest_removeGeneralGroupOriginConfiguration_shouldBecomeInherit() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel inputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .originConfiguration(DEFAULT_GENERAL_GROUP_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(DEFAULT_GENERAL_GROUP_ORIGIN_CONFIGURATION)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));
        verify(codeartifactClient).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
    }

    @Test
    public void handleRequest_removeRootGroupOriginConfiguration_shouldBecomeAllow() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel inputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(ROOT_PACKAGE_GROUP)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .arn(ROOT_PACKAGE_GROUP_ARN)
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(ROOT_PACKAGE_GROUP)
                .originConfiguration(DEFAULT_ROOT_GROUP_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(ROOT_PACKAGE_GROUP_ARN)
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(ROOT_PACKAGE_GROUP)
                .originConfiguration(DEFAULT_ROOT_GROUP_ORIGIN_CONFIGURATION)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .previousResourceState(resourceModel(ROOT_PACKAGE_GROUP, UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));
        verify(codeartifactClient).updatePackageGroupOriginConfiguration(any(UpdatePackageGroupOriginConfigurationRequest.class));
    }

    @Test
    public void handleRequest_updatedOriginConfiguration_Add100Repos_Remove150Repos() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_1)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_1)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_ORIGIN_CONFIGURATION_1)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(LIST_OF_100_ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse, ListAllowedRepositoriesForGroupResponse.builder().build());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));

        ArgumentCaptor<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestArgumentCaptor =
                ArgumentCaptor.forClass(UpdatePackageGroupOriginConfigurationRequest.class);
        verify(codeartifactClient, times(3)).updatePackageGroupOriginConfiguration(updatePGOCRequestArgumentCaptor.capture());
        List<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestValues = updatePGOCRequestArgumentCaptor.getAllValues();

        // first request has restrictions and only repos to add since it reaches max repo count
        assertThat(updatePGOCRequestValues.get(0).hasRestrictions()).isTrue();
        assertThat(updatePGOCRequestValues.get(0).addAllowedRepositories().size()).isEqualTo(100);
        assertThat(updatePGOCRequestValues.get(0).hasRemoveAllowedRepositories()).isFalse();

        assertThat(updatePGOCRequestValues.get(1).hasRestrictions()).isFalse();
        assertThat(updatePGOCRequestValues.get(1).hasAddAllowedRepositories()).isFalse();
        assertThat(updatePGOCRequestValues.get(1).removeAllowedRepositories().size()).isEqualTo(100);

        assertThat(updatePGOCRequestValues.get(2).hasRestrictions()).isFalse();
        assertThat(updatePGOCRequestValues.get(2).hasAddAllowedRepositories()).isFalse();
        assertThat(updatePGOCRequestValues.get(2).removeAllowedRepositories().size()).isEqualTo(50);
    }

    @Test
    public void handleRequest_updatedOriginConfiguration_Add150Repos_Remove150Repos() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_2)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_2)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_ORIGIN_CONFIGURATION_2)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(LIST_OF_150_ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse, ListAllowedRepositoriesForGroupResponse.builder().build());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));

        ArgumentCaptor<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestArgumentCaptor =
                ArgumentCaptor.forClass(UpdatePackageGroupOriginConfigurationRequest.class);
        verify(codeartifactClient, times(3)).updatePackageGroupOriginConfiguration(updatePGOCRequestArgumentCaptor.capture());
        List<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestValues = updatePGOCRequestArgumentCaptor.getAllValues();

        // first request has restrictions and remainders from both repos to add/remove
        assertThat(updatePGOCRequestValues.get(0).hasRestrictions()).isTrue();
        assertThat(updatePGOCRequestValues.get(0).addAllowedRepositories().size()).isEqualTo(100);
        assertThat(updatePGOCRequestValues.get(0).hasRemoveAllowedRepositories()).isFalse();

        assertThat(updatePGOCRequestValues.get(1).hasRestrictions()).isFalse();
        assertThat(updatePGOCRequestValues.get(1).addAllowedRepositories().size()).isEqualTo(50);
        assertThat(updatePGOCRequestValues.get(1).removeAllowedRepositories().size()).isEqualTo(50);

        assertThat(updatePGOCRequestValues.get(2).hasRestrictions()).isFalse();
        assertThat(updatePGOCRequestValues.get(2).hasAddAllowedRepositories()).isFalse();
        assertThat(updatePGOCRequestValues.get(2).removeAllowedRepositories().size()).isEqualTo(100);
    }

    @Test
    public void handleRequest_updatedOriginConfiguration_Add0Repos_Remove150Repos() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(PUBLISH_ONLY_EMPTY_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .originConfiguration(PUBLISH_ONLY_EMPTY_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(PUBLISH_ONLY_EMPTY_LIST_PACKAGE_GROUP_ORIGIN_CONFIGURATION)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(Collections.emptyList())
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse, ListAllowedRepositoriesForGroupResponse.builder().build());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));

        ArgumentCaptor<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestArgumentCaptor =
                ArgumentCaptor.forClass(UpdatePackageGroupOriginConfigurationRequest.class);
        verify(codeartifactClient, times(2)).updatePackageGroupOriginConfiguration(updatePGOCRequestArgumentCaptor.capture());
        List<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestValues = updatePGOCRequestArgumentCaptor.getAllValues();

        // first request has restrictions and only repos to remove since there is no repos to add
        assertThat(updatePGOCRequestValues.get(0).hasRestrictions()).isTrue();
        assertThat(updatePGOCRequestValues.get(0).hasAddAllowedRepositories()).isFalse();
        assertThat(updatePGOCRequestValues.get(0).removeAllowedRepositories().size()).isEqualTo(100);

        assertThat(updatePGOCRequestValues.get(1).hasRestrictions()).isFalse();
        assertThat(updatePGOCRequestValues.get(1).hasAddAllowedRepositories()).isFalse();
        assertThat(updatePGOCRequestValues.get(1).removeAllowedRepositories().size()).isEqualTo(50);
    }

    @Test
    public void handleRequest_updatedOriginConfiguration_Add150Repos_Remove0Repos() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_2)
                .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_2)
                .build();

        final PackageGroupDescription packageGroupDescription = PackageGroupDescription.builder()
                .arn(PGC_ARN_WITH_DOMAIN_OWNER)
                .domainOwner(DOMAIN_OWNER)
                .domainName(DOMAIN_NAME)
                .pattern(PACKAGE_GROUP_PATTERN)
                .originConfiguration(UPDATED_LARGE_REPO_LIST_ORIGIN_CONFIGURATION_2)
                .build();

        DescribePackageGroupResponse describePackageGroupResponse = DescribePackageGroupResponse.builder()
                .packageGroup(packageGroupDescription)
                .build();

        when(proxyClient.client().describePackageGroup(any(DescribePackageGroupRequest.class))).thenReturn(describePackageGroupResponse);

        ListAllowedRepositoriesForGroupResponse listAllowedRepositoriesForGroupResponse = ListAllowedRepositoriesForGroupResponse.builder()
                .allowedRepositories(LIST_OF_150_ALLOWED_REPOS)
                .nextToken("fakeNextToken")
                .build();

        when(proxyClient.client().listAllowedRepositoriesForGroup(any(ListAllowedRepositoriesForGroupRequest.class)))
                .thenReturn(listAllowedRepositoriesForGroupResponse, ListAllowedRepositoriesForGroupResponse.builder().build());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(resourceModel(PACKAGE_GROUP_PATTERN, PUBLISH_ONLY_EMPTY_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION))
                .awsPartition("aws")
                .region("us-west-2")
                .awsAccountId(DOMAIN_OWNER)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describePackageGroup(any(DescribePackageGroupRequest.class));
        verify(codeartifactClient, never()).updatePackageGroup(any(UpdatePackageGroupRequest.class));

        ArgumentCaptor<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestArgumentCaptor =
                ArgumentCaptor.forClass(UpdatePackageGroupOriginConfigurationRequest.class);
        verify(codeartifactClient, times(2)).updatePackageGroupOriginConfiguration(updatePGOCRequestArgumentCaptor.capture());
        List<UpdatePackageGroupOriginConfigurationRequest> updatePGOCRequestValues = updatePGOCRequestArgumentCaptor.getAllValues();

        // first request has restrictions and only repos to remove since there is no repos to add
        assertThat(updatePGOCRequestValues.get(0).hasRestrictions()).isTrue();
        assertThat(updatePGOCRequestValues.get(0).addAllowedRepositories().size()).isEqualTo(100);
        assertThat(updatePGOCRequestValues.get(0).hasRemoveAllowedRepositories()).isFalse();

        assertThat(updatePGOCRequestValues.get(1).hasRestrictions()).isFalse();
        assertThat(updatePGOCRequestValues.get(1).addAllowedRepositories().size()).isEqualTo(50);
        assertThat(updatePGOCRequestValues.get(1).hasRemoveAllowedRepositories()).isFalse();
    }

    ResourceModel resourceModel(String pattern, OriginConfiguration originConfiguration) {
        return ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(pattern)
                .originConfiguration(originConfiguration)
                .build();
    }

    ResourceModel resourceModel(String description, String contactInfo) {
        return ResourceModel.builder()
                .domainName(DOMAIN_NAME)
                .domainOwner(DOMAIN_OWNER)
                .pattern(PACKAGE_GROUP_PATTERN)
                .description(description)
                .contactInfo(contactInfo)
                .build();
    }
}
