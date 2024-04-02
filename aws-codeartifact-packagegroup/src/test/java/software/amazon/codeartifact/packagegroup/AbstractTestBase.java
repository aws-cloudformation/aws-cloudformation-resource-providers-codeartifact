package software.amazon.codeartifact.packagegroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginConfiguration;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestriction;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionMode;
import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionType;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTestBase {
  public static final ObjectMapper MAPPER = new ObjectMapper();
  protected static final String DOMAIN_NAME = "test-domain-name";
  protected static final String PARTITION = "aws";
  protected static final String REGION = "us-west-2";
  protected static final String DOMAIN_OWNER = "12345";
  protected static final String ROOT_PACKAGE_GROUP = "/*";
  protected static final String PACKAGE_GROUP_PATTERN = "/npm/*";
  protected static final String PACKAGE_GROUP_PATTERN_1 = "/npm/test/*";
  protected static final String ROOT_PACKAGE_GROUP_ARN = "arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/%2a";
  protected static final String PGC_ARN_WITH_DOMAIN_OWNER = "arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/npm/%2a";

  protected static final String DESCRIPTION = "pgcDescription";
  protected static final String UPDATED_DESCRIPTION = "updated-pgcDescription";
  protected static final String CONTACT_INFO = "contactInfo";
  protected static final String UPDATED_CONTACT_INFO = "updated-contactInfo";
  protected static final String ALLOW = PackageGroupOriginRestrictionMode.ALLOW.toString();
  protected static final String BLOCK = PackageGroupOriginRestrictionMode.BLOCK.toString();
  protected static final String INHERIT = PackageGroupOriginRestrictionMode.INHERIT.toString();
  protected static final String ALLOW_SPECIFIC_REPOSITORIES = PackageGroupOriginRestrictionMode.ALLOW_SPECIFIC_REPOSITORIES.toString();
  protected static final List<String> ALLOWED_REPOS = ImmutableList.of("repo1", "repo2", "repo3");
  protected static final List<String> UPDATED_ALLOWED_REPOS = ImmutableList.of("repo2", "repo3", "repo4", "repo5");
  protected static final List<String> LIST_OF_100_ALLOWED_REPOS = IntStream.range(0, 100).mapToObj(i -> "repo"+i).collect(Collectors.toList());
  protected static final List<String> LIST_OF_150_ALLOWED_REPOS = IntStream.range(0, 150).mapToObj(i -> "repo"+i).collect(Collectors.toList());
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> RESTRICTION_MAP =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(ALLOW).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(BLOCK).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(3L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> DEFAULT_GENERAL_GROUP_RESTRICTION_MAP =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(INHERIT).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(INHERIT).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(INHERIT).repositoriesCount(0L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> DEFAULT_ROOT_GROUP_RESTRICTION_MAP =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(ALLOW).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW).repositoriesCount(0L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> UPDATED_RESTRICTION_MAP =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(4L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(BLOCK).repositoriesCount(0L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> PUBLISH_ONLY_EMPTY_LIST_RESTRICTION_MAP =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(0L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> UPDATED_LARGE_REPO_LIST_RESTRICTION_MAP_1 =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(INHERIT).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(100L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(0L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));
  protected static final Map<PackageGroupOriginRestrictionType, PackageGroupOriginRestriction> UPDATED_LARGE_REPO_LIST_RESTRICTION_MAP_2 =
      Stream.of(new Object[][] {
          { PackageGroupOriginRestrictionType.PUBLISH, PackageGroupOriginRestriction.builder().mode(INHERIT).repositoriesCount(0L).build() },
          { PackageGroupOriginRestrictionType.EXTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(150L).build() },
          { PackageGroupOriginRestrictionType.INTERNAL_UPSTREAM, PackageGroupOriginRestriction.builder().mode(ALLOW_SPECIFIC_REPOSITORIES).repositoriesCount(0L).build() },
      }).collect(Collectors.toMap(data -> (PackageGroupOriginRestrictionType) data[0], data -> (PackageGroupOriginRestriction) data[1]));

  protected static final PackageGroupOriginConfiguration PACKAGE_GROUP_ORIGIN_CONFIGURATION =
      PackageGroupOriginConfiguration.builder()
          .restrictions(RESTRICTION_MAP)
          .build();
  protected static final PackageGroupOriginConfiguration DEFAULT_GENERAL_GROUP_ORIGIN_CONFIGURATION =
      PackageGroupOriginConfiguration.builder()
          .restrictions(DEFAULT_GENERAL_GROUP_RESTRICTION_MAP)
          .build();
  protected static final PackageGroupOriginConfiguration DEFAULT_ROOT_GROUP_ORIGIN_CONFIGURATION =
      PackageGroupOriginConfiguration.builder()
          .restrictions(DEFAULT_ROOT_GROUP_RESTRICTION_MAP)
          .build();
  protected static final PackageGroupOriginConfiguration UPDATED_PACKAGE_GROUP_ORIGIN_CONFIGURATION =
      PackageGroupOriginConfiguration.builder()
          .restrictions(UPDATED_RESTRICTION_MAP)
          .build();
  protected static final PackageGroupOriginConfiguration PUBLISH_ONLY_EMPTY_LIST_PACKAGE_GROUP_ORIGIN_CONFIGURATION =
      PackageGroupOriginConfiguration.builder()
          .restrictions(PUBLISH_ONLY_EMPTY_LIST_RESTRICTION_MAP)
          .build();
  protected static final PackageGroupOriginConfiguration UPDATED_LARGE_REPO_LIST_ORIGIN_CONFIGURATION_1 =
      PackageGroupOriginConfiguration.builder()
          .restrictions(UPDATED_LARGE_REPO_LIST_RESTRICTION_MAP_1)
          .build();
  protected static final PackageGroupOriginConfiguration UPDATED_LARGE_REPO_LIST_ORIGIN_CONFIGURATION_2 =
      PackageGroupOriginConfiguration.builder()
          .restrictions(UPDATED_LARGE_REPO_LIST_RESTRICTION_MAP_2)
          .build();

  protected static final OriginConfiguration RESOURCE_MODEL_ORIGIN_CONFIGURATION = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(ALLOW).build())
              .externalUpstream(RestrictionType.builder()
                      .restrictionMode(BLOCK).build())
              .internalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(ALLOWED_REPOS).build())
              .build()
      ).build();
  protected static final OriginConfiguration DEFAULT_GENERAL_GROUP_RESOURCE_MODEL_ORIGIN_CONFIGURATION = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(INHERIT).build())
              .externalUpstream(RestrictionType.builder()
                  .restrictionMode(INHERIT).build())
              .internalUpstream(RestrictionType.builder()
                  .restrictionMode(INHERIT).build())
              .build()
      ).build();
  protected static final OriginConfiguration DEFAULT_ROOT_GROUP_RESOURCE_MODEL_ORIGIN_CONFIGURATION = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(ALLOW).build())
              .externalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW).build())
              .internalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW).build())
              .build()
      ).build();
  protected static final OriginConfiguration UPDATED_RESOURCE_MODEL_ORIGIN_CONFIGURATION = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(UPDATED_ALLOWED_REPOS).build())
              .externalUpstream(RestrictionType.builder().restrictionMode(ALLOW).build())
              .internalUpstream(RestrictionType.builder().restrictionMode(BLOCK).build())
              .build()
      ).build();
  protected static final OriginConfiguration PUBLISH_ONLY_EMPTY_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(ALLOW_SPECIFIC_REPOSITORIES).repositories(Collections.emptyList()).build())
              .externalUpstream(RestrictionType.builder().restrictionMode(ALLOW_SPECIFIC_REPOSITORIES).repositories(Collections.emptyList()).build())
              .internalUpstream(RestrictionType.builder().restrictionMode(ALLOW_SPECIFIC_REPOSITORIES).repositories(Collections.emptyList()).build())
              .build()
      ).build();
  protected static final OriginConfiguration LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(INHERIT).build())
              .externalUpstream(RestrictionType.builder()
                  .restrictionMode(INHERIT).build())
              .internalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(LIST_OF_150_ALLOWED_REPOS).build())
              .build()
      ).build();
  protected static final OriginConfiguration UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_1 = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(INHERIT).build())
              .externalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(LIST_OF_100_ALLOWED_REPOS).build())
              .internalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(Collections.emptyList()).build())
              .build()
      ).build();
  protected static final OriginConfiguration UPDATED_LARGE_REPO_LIST_RESOURCE_MODEL_ORIGIN_CONFIGURATION_2 = OriginConfiguration.builder()
      .restrictions(
          Restrictions.builder()
              .publish(RestrictionType.builder().restrictionMode(INHERIT).build())
              .externalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(LIST_OF_150_ALLOWED_REPOS).build())
              .internalUpstream(RestrictionType.builder()
                  .restrictionMode(ALLOW_SPECIFIC_REPOSITORIES)
                  .repositories(Collections.emptyList()).build())
              .build()
      ).build();

  protected final List<software.amazon.awssdk.services.codeartifact.model.Tag> SERVICE_TAGS = ImmutableList.of(
      software.amazon.awssdk.services.codeartifact.model.Tag.builder().key("key1").value("value1").build(),
      software.amazon.awssdk.services.codeartifact.model.Tag.builder().key("key2").value("value2").build()
  );
  protected final List<software.amazon.codeartifact.packagegroup.Tag> RESOURCE_MODEL_TAGS = ImmutableList.of(
      software.amazon.codeartifact.packagegroup.Tag.builder().key("key1").value("value1").build(),
      Tag.builder().key("key2").value("value2").build());

  protected final Map<String, String> DESIRED_TAGS_MAP = ImmutableMap.of(
      "key1","value1",
      "key2","value2");

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
      ProgressEvent<software.amazon.codeartifact.packagegroup.ResourceModel, CallbackContext> response,
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

    protected void assertInProgress(
            ProgressEvent<software.amazon.codeartifact.packagegroup.ResourceModel, CallbackContext> response,
            ResourceModel desiredOutputModel
    ) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(desiredOutputModel);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(10);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
