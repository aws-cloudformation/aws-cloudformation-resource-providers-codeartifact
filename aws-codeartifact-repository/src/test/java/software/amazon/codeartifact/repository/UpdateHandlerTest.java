package software.amazon.codeartifact.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DisassociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.RepositoryExternalConnectionInfo;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
import software.amazon.awssdk.services.codeartifact.model.TagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UntagResourceRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdateRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepository;
import software.amazon.awssdk.services.codeartifact.model.UpstreamRepositoryInfo;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<CodeartifactClient> proxyClient;

    @Mock
    CodeartifactClient codeartifactClient;

    final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
        .name(REPO_NAME)
        .administratorAccount(ADMIN_ACCOUNT)
        .arn(REPO_ARN_WITH_DOMAIN_OWNER)
        .description(DESCRIPTION)
        .domainOwner(DOMAIN_OWNER)
        .domainName(DOMAIN_NAME)
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
    public void handleRequest_simpleSuccess_withPolicyDoc_withSamePolicyDoc() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
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
            .previousResourceState(resourceModel(TEST_POLICY_DOC_0))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient, never()).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_withPolicyDoc_withUpdatedPolicyDoc() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .permissionsPolicyDocument(TEST_POLICY_DOC_0)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
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
            .previousResourceState(resourceModel(TEST_POLICY_DOC_1))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_deletePolicyDoc() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .domainOwner(DOMAIN_OWNER)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(resourceModel(TEST_POLICY_DOC_0))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient).deleteRepositoryPermissionsPolicy(any(DeleteRepositoryPermissionsPolicyRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_withUpstreams() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .upstreams(UPSTREAMS)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .upstreams(
                UpstreamRepositoryInfo.builder().repositoryName(UPSTREAM_0).build(),
                UpstreamRepositoryInfo.builder().repositoryName(UPSTREAM_1).build()
            )
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
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

        UpdateRepositoryResponse updateRepositoryResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updateRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(resourceModel(null))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_removeUpstreams() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
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
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        UpdateRepositoryResponse updateRepositoryResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updateRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(resourceModelWithUpstreams())
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UpdateRepositoryRequest> updateRepositoryRequestArgumentCaptor =
            ArgumentCaptor.forClass(UpdateRepositoryRequest.class);


        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient).updateRepository(updateRepositoryRequestArgumentCaptor.capture());

        UpdateRepositoryRequest updateRepositoryRequest = updateRepositoryRequestArgumentCaptor.getValue();

        assertThat(updateRepositoryRequest.upstreams()).isEmpty();
        assertThat(updateRepositoryRequest.repository()).isEqualTo(REPO_NAME);
        assertThat(updateRepositoryRequest.domain()).isEqualTo(DOMAIN_NAME);
    }

    @Test
    public void handleRequest_simpleSuccess_withNewExternalConnections() {
        final UpdateHandler handler = new UpdateHandler();

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

        UpdateRepositoryResponse updateRepositoryResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updateRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(resourceModel(null))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));

        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_replaceUpstreamWithExternalConnection() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        final ResourceModel prevModelWithUpstreams = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .upstreams(UPSTREAMS)
            .description(DESCRIPTION)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .externalConnections(Collections.singletonList(NPM_EC))
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .externalConnections(
                Collections.singletonList(
                    RepositoryExternalConnectionInfo.builder()
                        .externalConnectionName(NPM_EC)
                        .build()
                )
            )
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        UpdateRepositoryResponse updateRepositoryResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updateRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(prevModelWithUpstreams)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        //Should first remove upstreams then add external connections
        InOrder inOrderVerifier = inOrder(codeartifactClient);


        ArgumentCaptor<UpdateRepositoryRequest> updateRepositoryRequestArgumentCaptor = ArgumentCaptor.forClass(UpdateRepositoryRequest.class);
        // assert we remove upstreams first
        inOrderVerifier.verify(codeartifactClient).updateRepository(updateRepositoryRequestArgumentCaptor.capture());

        UpdateRepositoryRequest updateRequest = updateRepositoryRequestArgumentCaptor.getValue();
        // assert deletion of upstreams
        assertThat(updateRequest.upstreams()).isEmpty();

        // assert we add ECs after
        inOrderVerifier.verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_replaceUpstreamWithExternalConnection_noUpdateRepoNeeded() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .build();

        final ResourceModel prevModelWithUpstreams = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .externalConnections(Collections.singletonList(NPM_EC))
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

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(prevModelWithUpstreams)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        //Should first remove upstreams then add external connections

        verify(codeartifactClient, never()).updateRepository(any(UpdateRepositoryRequest.class));

        // assert we add ECs after
        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_replaceUpstreamWithExternalConnection_updateRepositoryDesc_doNotUpdateUpstreams() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .description(DESCRIPTION)
            .externalConnections(Collections.singletonList(NPM_EC))
            .build();

        final ResourceModel prevModelWithUpstreams = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .externalConnections(Collections.singletonList(NPM_EC))
            .build();

        UpdateRepositoryResponse updateRepositoryResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updateRepositoryResponse);


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

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(prevModelWithUpstreams)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));
        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_replaceExternalConnectionWithUpstreams() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .upstreams(UPSTREAMS)
            .description(DESCRIPTION)
            .build();

        final ResourceModel prevModelWithEc = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .externalConnections(Collections.singletonList(NPM_EC))
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
            .upstreams(
                UpstreamRepositoryInfo.builder().repositoryName(UPSTREAM_0).build(),
                UpstreamRepositoryInfo.builder().repositoryName(UPSTREAM_1).build()
            )
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        UpdateRepositoryResponse updateRepositoryResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updateRepositoryResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(prevModelWithEc)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        InOrder inOrderVerifier = inOrder(codeartifactClient);

        // verify first remove externalConnections then add external connections
        inOrderVerifier.verify(codeartifactClient).disassociateExternalConnection(any(DisassociateExternalConnectionRequest.class));

        ArgumentCaptor<UpdateRepositoryRequest> updateRepositoryRequestArgumentCaptor = ArgumentCaptor.forClass(UpdateRepositoryRequest.class);
        // verify we add upstreams after removing
        inOrderVerifier.verify(codeartifactClient).updateRepository(updateRepositoryRequestArgumentCaptor.capture());

        // assert additions of upstreams
        UpdateRepositoryRequest updateRequest = updateRepositoryRequestArgumentCaptor.getValue();
        Set<String> upstreamsAdded = updateRequest.upstreams()
            .stream()
            .map(UpstreamRepository::repositoryName)
            .collect(Collectors.toSet());

        assertThat(upstreamsAdded).contains(UPSTREAM_0, UPSTREAM_1);
    }

    @Test
    public void handleRequest_simpleSuccess_withSameExternalConnections_doesNotCallUpdateRepo() {
        final UpdateHandler handler = new UpdateHandler();

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

        // describe repo response has no external connections, we need to add the one in the Resourcemodel
        final ResourceModel prevModelWithNpmEc = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))

            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);
        // this happens when permission policy is stabilized

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(prevModelWithNpmEc)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(codeartifactClient, never()).updateRepository(any(UpdateRepositoryRequest.class));
        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_withExistingExternalConnections_doesNotCallUpdateRepo() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel inputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        // describe repo response has one external connection, we need to remove and add new one
        final ResourceModel prevModelWithPypiEc = resourceModelWithPypiExternalConnection();

        final ResourceModel expectedOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
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

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();


        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);
        // this happens when permission policy is stabilized

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(inputModel)
            .previousResourceState(prevModelWithPypiEc)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, expectedOutputModel);
        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        verify(codeartifactClient, never()).updateRepository(any(UpdateRepositoryRequest.class));
        verify(codeartifactClient).disassociateExternalConnection(any(DisassociateExternalConnectionRequest.class));
        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_simpleSuccess_withMultipleExternalConnections() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Arrays.asList(NPM_EC, PYPI_EC))
            .build();

        // describe repo response has no external connections, we need to add the one in the ResourceModel
        final RepositoryDescription repositoryDescription = RepoInfoWithOutExternalConnections();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(resourceModel(null))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient, times(2)).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_addTags_withDomainOwnerInTemplate_adminAccountIsDifferent() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .tags(RESOURCE_MODEL_TAGS)
            .repositoryName(REPO_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        // expected Arn domainOwner should be with the passed in domainOwner from the template
        String expectedRepoArn = getExpectedRepoArn(REGION, DOMAIN_OWNER, DOMAIN_NAME, REPO_NAME);

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(expectedRepoArn)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceTags(DESIRED_TAGS_MAP)
            .awsPartition(PARTITION)
            .awsAccountId(ADMIN_ACCOUNT)
            .region(REGION)
            .desiredResourceState(model)
            .previousResourceState(resourceModel(null))
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        ArgumentCaptor<TagResourceRequest> tagResourceCaptor
            = ArgumentCaptor.forClass(TagResourceRequest.class);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient, never()).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).tagResource(tagResourceCaptor.capture());

        TagResourceRequest capturedRequest = tagResourceCaptor.getValue();

        assertThat(capturedRequest.resourceArn()).isEqualTo(expectedRepoArn);
    }

    @Test
    public void handleRequest_addTags_withOutDomainOwnerInTemplate() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .tags(RESOURCE_MODEL_TAGS)
            .repositoryName(REPO_NAME)
            .build();

        ResourceModel previousModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .build();

        // expected Arn domainOwner should be with the admin account id from the request
        String expectedRepoArn = getExpectedRepoArn(REGION, ADMIN_ACCOUNT, DOMAIN_NAME, REPO_NAME);

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(expectedRepoArn)
            .description(DESCRIPTION)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(expectedRepoArn)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceTags(DESIRED_TAGS_MAP)
            .awsPartition(PARTITION)
            .awsAccountId(ADMIN_ACCOUNT)
            .region(REGION)
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        ArgumentCaptor<TagResourceRequest> tagResourceCaptor
            = ArgumentCaptor.forClass(TagResourceRequest.class);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient, never()).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).tagResource(tagResourceCaptor.capture());

        TagResourceRequest capturedRequest = tagResourceCaptor.getValue();

        // expected Arn domainOwner should be with the admin account id from the request
        assertThat(capturedRequest.resourceArn()).isEqualTo(expectedRepoArn);
    }

    @Test
    public void handleRequest_removeTags_withDomainOwnerInTemplate_adminAccountIsDifferent() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .build();

        ResourceModel previousModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .domainOwner(DOMAIN_OWNER)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceTags(DESIRED_TAGS_MAP)
            .awsPartition(PARTITION)
            .awsAccountId(ADMIN_ACCOUNT)
            .region(REGION)
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient, never()).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));


        ArgumentCaptor<UntagResourceRequest> untagResourceCaptor
            = ArgumentCaptor.forClass(UntagResourceRequest.class);

        verify(codeartifactClient).untagResource(untagResourceCaptor.capture());

        UntagResourceRequest capturedRequest = untagResourceCaptor.getValue();

        // expected Arn domainOwner should be with the passed in domainOwner from the template
        String expectedRepoArn = getExpectedRepoArn(REGION, DOMAIN_OWNER, DOMAIN_NAME, REPO_NAME);
        assertThat(capturedRequest.resourceArn()).isEqualTo(expectedRepoArn);
    }

    @Test
    public void handleRequest_removeTags_withoutDomainOwnerInTemplate() throws JsonProcessingException {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .build();

        ResourceModel previousModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .build();

        // expected Arn domainOwner should be with the admin account id from the request
        String expectedRepoArn = getExpectedRepoArn(REGION, ADMIN_ACCOUNT, DOMAIN_NAME, REPO_NAME);

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(expectedRepoArn)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .name(REPO_NAME)
            .repositoryName(REPO_NAME)
            .arn(expectedRepoArn)
            .description(DESCRIPTION)
            .build();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .previousResourceTags(DESIRED_TAGS_MAP)
            .awsPartition(PARTITION)
            .awsAccountId(ADMIN_ACCOUNT)
            .region(REGION)
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        ArgumentCaptor<UntagResourceRequest> untagResourceCaptor
            = ArgumentCaptor.forClass(UntagResourceRequest.class);

        verify(codeartifactClient).untagResource(untagResourceCaptor.capture());


        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(codeartifactClient, never()).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));

        verify(codeartifactClient).untagResource(untagResourceCaptor.capture());

        UntagResourceRequest capturedRequest = untagResourceCaptor.getValue();
        assertThat(capturedRequest.resourceArn()).isEqualTo(expectedRepoArn);
    }



    RepositoryDescription RepoInfoWithOutExternalConnections() {
        return RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();
    }

    ResourceModel resourceModelWithPypiExternalConnection() {
        return ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN_WITH_DOMAIN_OWNER)
            .externalConnections(Collections.singletonList(PYPI_EC))
            .description(DESCRIPTION)
            .build();
    }

    ResourceModel resourceModelWithUpstreams() {
        return ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .upstreams(UPSTREAMS)
            .repositoryName(REPO_NAME)
            .build();
    }
    ResourceModel resourceModel(Map<String, Object> policyDoc) {
        ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .repositoryName(REPO_NAME)
            .domainOwner(DOMAIN_OWNER)
            .build();

        if (policyDoc != null) {
            model.setPermissionsPolicyDocument(policyDoc);
        }
        return model;
    }
}
