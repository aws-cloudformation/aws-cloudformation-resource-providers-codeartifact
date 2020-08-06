package software.amazon.codeartifact.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.AssociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.DeleteRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.DescribeRepositoryResponse;
import software.amazon.awssdk.services.codeartifact.model.DisassociateExternalConnectionRequest;
import software.amazon.awssdk.services.codeartifact.model.GetRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyRequest;
import software.amazon.awssdk.services.codeartifact.model.PutRepositoryPermissionsPolicyResponse;
import software.amazon.awssdk.services.codeartifact.model.RepositoryDescription;
import software.amazon.awssdk.services.codeartifact.model.RepositoryExternalConnectionInfo;
import software.amazon.awssdk.services.codeartifact.model.ResourceNotFoundException;
import software.amazon.awssdk.services.codeartifact.model.ResourcePolicy;
import software.amazon.awssdk.services.codeartifact.model.UpdateRepositoryRequest;
import software.amazon.awssdk.services.codeartifact.model.UpdateRepositoryResponse;
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
        .arn(REPO_ARN)
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

    @Test
    public void handleRequest_SimpleSuccess_withPolicyDoc() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .policyDocument(TEST_POLICY_DOC)
            .build();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        PutRepositoryPermissionsPolicyResponse putRepoPolicyResponse = PutRepositoryPermissionsPolicyResponse.builder()
            .policy(
                ResourcePolicy.builder()
                    .document(TEST_POLICY_DOC_JSON)
                    .build()
            )
            .build();

        when(proxyClient.client().putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class))).thenReturn(putRepoPolicyResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class));
        verify(codeartifactClient).putRepositoryPermissionsPolicy(any(PutRepositoryPermissionsPolicyRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_withUpstreams() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .upstreams(UPSTREAMS)
            .policyDocument(TEST_POLICY_DOC)
            .build();

        final RepositoryDescription repositoryDescription = RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
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
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .upstreams(UPSTREAMS)
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        UpdateRepositoryResponse updatePackageVersionsStatusResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updatePackageVersionsStatusResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);

        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_withNewExternalConnections() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        // describe repo response has no external connections, we need to add the one in the Resourcemodel
        final RepositoryDescription repositoryDescription = RepoInfoWithOutExternalConnections();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        UpdateRepositoryResponse updatePackageVersionsStatusResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updatePackageVersionsStatusResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);
        when(proxyClient.client().deleteRepositoryPermissionsPolicy(any(DeleteRepositoryPermissionsPolicyRequest.class)))
            .thenReturn(DeleteRepositoryPermissionsPolicyResponse.builder().build());

        // this happens when permission policy is stabilized
        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));

        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_withSameExternalConnections() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        // describe repo response has no external connections, we need to add the one in the Resourcemodel
        final RepositoryDescription repositoryDescription = RepoInfoWithNpmExternalConnection();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        UpdateRepositoryResponse updatePackageVersionsStatusResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updatePackageVersionsStatusResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);
        when(proxyClient.client().deleteRepositoryPermissionsPolicy(any(DeleteRepositoryPermissionsPolicyRequest.class)))
            .thenReturn(DeleteRepositoryPermissionsPolicyResponse.builder().build());
        // this happens when permission policy is stabilized
        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));

        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_withExistingExternalConnections() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Collections.singletonList(NPM_EC))
            .description(DESCRIPTION)
            .build();

        // describe repo response has one external connection, we need to remove and add new one
        final RepositoryDescription repositoryDescription = RepoInfoWithPypIExternalConnection();

        final ResourceModel desiredOutputModel = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .arn(REPO_ARN)
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .administratorAccount(ADMIN_ACCOUNT)
            .build();

        UpdateRepositoryResponse updatePackageVersionsStatusResponse = UpdateRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();
        when(proxyClient.client().updateRepository(any(UpdateRepositoryRequest.class))).thenReturn(updatePackageVersionsStatusResponse);

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);
        when(proxyClient.client().deleteRepositoryPermissionsPolicy(any(DeleteRepositoryPermissionsPolicyRequest.class)))
            .thenReturn(DeleteRepositoryPermissionsPolicyResponse.builder().build());
        // this happens when permission policy is stabilized
        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, desiredOutputModel);
        verify(codeartifactClient, times(2)).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient).updateRepository(any(UpdateRepositoryRequest.class));
        verify(codeartifactClient).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient).disassociateExternalConnection(any(DisassociateExternalConnectionRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess_withTooManyExternalConnections() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .repositoryName(REPO_NAME)
            .externalConnections(Arrays.asList(NPM_EC, PYPI_EC))
            .description(DESCRIPTION)
            .build();

        // describe repo response has no external connections, we need to add the one in the ResourceModel
        final RepositoryDescription repositoryDescription = RepoInfoWithOutExternalConnections();

        DescribeRepositoryResponse describeRepositoryResponse = DescribeRepositoryResponse.builder()
            .repository(repositoryDescription)
            .build();

        when(proxyClient.client().describeRepository(any(DescribeRepositoryRequest.class))).thenReturn(describeRepositoryResponse);
        when(proxyClient.client().deleteRepositoryPermissionsPolicy(any(DeleteRepositoryPermissionsPolicyRequest.class)))
            .thenReturn(DeleteRepositoryPermissionsPolicyResponse.builder().build());

        // this happens when permission policy is stabilized
        when(proxyClient.client().getRepositoryPermissionsPolicy(any(GetRepositoryPermissionsPolicyRequest.class))).thenThrow(
            ResourceNotFoundException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);

        verify(codeartifactClient).describeRepository(any(DescribeRepositoryRequest.class));
        verify(codeartifactClient, never()).associateExternalConnection(any(AssociateExternalConnectionRequest.class));
        verify(codeartifactClient, never()).disassociateExternalConnection(any(DisassociateExternalConnectionRequest.class));
    }

    RepositoryDescription RepoInfoWithOutExternalConnections() {
        return RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();
    }

    RepositoryDescription RepoInfoWithPypIExternalConnection() {
        return RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .externalConnections(
                RepositoryExternalConnectionInfo.builder()
                .externalConnectionName(PYPI_EC)
                .build()
            )
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();
    }

    RepositoryDescription RepoInfoWithNpmExternalConnection() {
        return RepositoryDescription.builder()
            .name(REPO_NAME)
            .administratorAccount(ADMIN_ACCOUNT)
            .arn(REPO_ARN)
            .externalConnections(
                RepositoryExternalConnectionInfo.builder()
                    .externalConnectionName(NPM_EC)
                    .build()
            )
            .upstreams(Collections.emptyList())
            .description(DESCRIPTION)
            .domainOwner(DOMAIN_OWNER)
            .domainName(DOMAIN_NAME)
            .build();
    }
}
