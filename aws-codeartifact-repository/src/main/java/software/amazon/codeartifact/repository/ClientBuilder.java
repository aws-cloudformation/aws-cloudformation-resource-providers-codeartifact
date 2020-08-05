package software.amazon.codeartifact.repository;

import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {
  public static CodeartifactClient getClient() {
    return CodeartifactClient.builder()
        .httpClient(LambdaWrapper.HTTP_CLIENT)
        .build();
  }
}
