package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.codeartifact.model.ListPackageGroupsRequest;
import software.amazon.awssdk.services.codeartifact.model.ListPackageGroupsResponse;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupSummary;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase{

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
    }


    private static final String PGC_ARN_1 = "arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/npm/%2a";
    private static final String PGC_ARN_2 = "arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/npm/test/%2a";

    private final PackageGroupSummary packageGroupSummary1 = PackageGroupSummary.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .pattern(PACKAGE_GROUP_PATTERN)
            .arn(PGC_ARN_1)
            .build();

    private final PackageGroupSummary packageGroupSummary2 = PackageGroupSummary.builder()
            .domainName(DOMAIN_NAME)
            .domainOwner(DOMAIN_OWNER)
            .pattern(PACKAGE_GROUP_PATTERN_1)
            .arn(PGC_ARN_2)
            .build();

    @Test
    public void handleRequest_SimpleSuccess() {
        final ListHandler handler = new ListHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .awsPartition("aws")
            .region("us-west-2")
            .awsAccountId(DOMAIN_OWNER)
            .build();

        when(proxy.injectCredentialsAndInvokeV2(any(ListPackageGroupsRequest.class), any())).thenReturn(
                ListPackageGroupsResponse.builder()
                        .packageGroups(packageGroupSummary1, packageGroupSummary2)
                        .nextToken("fakeNextToken")
                        .build()
        );

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, null, logger);

        List<ResourceModel> models = response.getResourceModels();
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        assertThat(models.get(0).getArn()).isEqualTo(PGC_ARN_1);
        assertThat(models.get(1).getArn()).isEqualTo(PGC_ARN_2);
    }

    @Test
    public void handleRequest_generalException() {
        final ListHandler handler = new ListHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(any(ListPackageGroupsRequest.class), any()))
                .thenThrow(AwsServiceException.class);

        assertThrows(CfnGeneralServiceException.class, () -> handler.handleRequest(proxy, request, null, logger));
    }
}
