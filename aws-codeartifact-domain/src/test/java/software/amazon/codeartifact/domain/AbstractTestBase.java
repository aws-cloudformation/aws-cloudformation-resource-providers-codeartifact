package software.amazon.codeartifact.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;
  protected static final String DOMAIN_NAME = "test-domain-name";
  protected static final String ENCRYPTION_KEY_ARN = "testKey/Arn";
  protected static final String DOMAIN_OWNER = "123456789";
  protected static final String PARTITION = "aws";
  protected static final String REGION = "us-west-2";
  protected static final String DOMAIN_ARN =
      String.format("arn:aws:codeartifact:region:%s:domain/%s", DOMAIN_OWNER, DOMAIN_NAME);
  protected static final Map<String, Object> TEST_POLICY_DOC = Collections.singletonMap("key0", "value0");
  protected final Instant NOW = Instant.now();
  protected final int REPO_COUNT = 2;
  protected final String STATUS = "Active";
  protected final int ASSET_SIZE = 1234;
  protected final List<Tag> RESOURCE_MODEL_TAGS = ImmutableList.of(
                    Tag.builder().key("key1").value("value1").build(),
                    Tag.builder().key("key2").value("value2").build());

  protected final Map<String, String> DESIRED_TAGS_MAP = ImmutableMap.of(
      "key1","value1",
      "key2","value2");

  protected final List<software.amazon.awssdk.services.codeartifact.model.Tag> SERVICE_TAGS = ImmutableList.of(
      software.amazon.awssdk.services.codeartifact.model.Tag.builder().key("key1").value("value1").build(),
      software.amazon.awssdk.services.codeartifact.model.Tag.builder().key("key2").value("value2").build());

  public static final ObjectMapper MAPPER = new ObjectMapper();

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
}
