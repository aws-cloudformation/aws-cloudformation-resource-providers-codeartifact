package software.amazon.codeartifact.domain;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
  protected static final String DOMAIN_ARN = "testDomainArn";
  protected static final String ENCRYPTION_KEY_ARN = "testKeyArn";
  protected static final String DOMAIN_OWNER = "123456789";
  protected static final String TEST_POLICY_DOC = "{\"Version\":\"2012-10-17\","
      + "\"Statement\":[{\"Sid\":\"ContributorPolicy\",\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"arn:aws:iam::946525001030:root\"},\"Action\":[\"codeartifact:CreateRepository\",\"codeartifact:DeleteDomain\",\"codeartifact:DeleteDomainPermissionsPolicy\",\"codeartifact:DescribeDomain\",\"codeartifact:GetAuthorizationToken\",\"codeartifact:GetDomainPermissionsPolicy\",\"codeartifact:ListRepositoriesInDomain\",\"codeartifact:PutDomainPermissionsPolicy\",\"sts:GetServiceBearerToken\"],\"Resource\":\"*\"}]}";
  protected final Instant NOW = Instant.now();
  protected final int REPO_COUNT = 2;
  protected final String STATUS = "Active";
  protected final int ASSET_SIZE = 1234;

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
