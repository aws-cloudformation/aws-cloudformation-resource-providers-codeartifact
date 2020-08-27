package software.amazon.codeartifact.repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
  public static final ObjectMapper MAPPER = new ObjectMapper();
  protected static final String DOMAIN_NAME = "test-domain-name";
  protected static final String DOMAIN_OWNER = "12345";
  protected static final String ADMIN_ACCOUNT = "54321";
  protected static final String REPO_NAME = "test-repo-name";
  protected static final String REPO_ARN = String.format("arn:aws:codeartifact:region:%s:repository/%s/%s",
      DOMAIN_OWNER, DOMAIN_NAME, REPO_NAME);
  protected static final Map<String, Object> TEST_POLICY_DOC_0 = Collections.singletonMap("key0", "value0");
  protected static final Map<String, Object> TEST_POLICY_DOC_1 = Collections.singletonMap("key1", "value1");

  protected static final String TEST_POLICY_DOC_JSON = "{\"key\":\"value\"}";
  protected static final String DESCRIPTION = "repoDescription";
  protected final String UPSTREAM_0 = "upstream0";
  protected final String UPSTREAM_1 = "upstream1";
  protected final String NPM_EC = "public:npmjs";
  protected final String PYPI_EC = "public:pypi";

  protected final List<String> UPSTREAMS = Arrays.asList(UPSTREAM_0,UPSTREAM_1);

  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();
  }
  static ProxyClient<CodeartifactClient> MOCK_PROXY(
    final AmazonWebServicesClientProxy proxy,
    final CodeartifactClient sdkClient) {
    return new ProxyClient<CodeartifactClient>() {
      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
      injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
        return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
      CompletableFuture<ResponseT>
      injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
      IterableT
      injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
        return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
      injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
      injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public CodeartifactClient client() {
        return sdkClient;
      }
    };
  }

  protected void assertSuccess(
      ProgressEvent<ResourceModel, CallbackContext> response,
      ResourceModel desiredOutputModel
  ) {
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
    assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
  }
}
