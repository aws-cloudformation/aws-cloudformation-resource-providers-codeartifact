package software.amazon.codeartifact.packagegroup;

import org.immutables.value.Value;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Value.Immutable
@Value.Style(allParameters = true, typeImmutable = "*", typeAbstract = "Abstract*")
public abstract class AbstractPackageGroupArn {

    @Value.Default
    public String partition() {
        return "aws";
    }

    public abstract String service();

    public abstract String region();

    // the accountId component of the ARN
    public abstract String owner();

    // the type of the resource: "packagegroup", "repository" or "domain"
    public abstract String type();

    public abstract String packageGroupName();

    public abstract String domainName();

    public abstract String arn();

    public static PackageGroupArn fromArn(String arn) {

        final int NUMBER_OF_ARN_FIELDS = 6;

        String[] terms = arn.split(":", NUMBER_OF_ARN_FIELDS);

        String partition = terms[1];
        String service = terms[2];
        String region = terms[3];
        String domainOwner = terms[4];
        String[] compoundName = terms[5].split("/", 3);

        String resourceType = compoundName[0];
        String domainName = compoundName[1];
        String packageGroupName = "/" + URLDecoder.decode(compoundName[2], StandardCharsets.UTF_8);

        return PackageGroupArn.builder()
                .arn(arn)
                .partition(partition)
                .region(region)
                .service(service)
                .owner(domainOwner)
                .type(resourceType)
                .domainName(domainName)
                .packageGroupName(packageGroupName)
                .build();
    }
}
