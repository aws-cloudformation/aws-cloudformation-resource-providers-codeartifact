package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.services.codeartifact.CodeartifactClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {
    private static final String CFN_USER_AGENT_PREFIX = "aws-cloudformation-resource-handlers";

    public static CodeartifactClient getClient() {
        return CodeartifactClient.builder()
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_PREFIX, CFN_USER_AGENT_PREFIX)
                    .build()
            )
            .httpClient(LambdaWrapper.HTTP_CLIENT)
            .build();
    }
}
