package software.amazon.codeartifact.domain;

public class ArnUtils {
    public static final String SERVICE_NAME = "codeartifact";
    public static final String DOMAIN_RESOURCE_TYPE = "domain";
    public static final String REPO_RESOURCE_TYPE = "repository";


    public static Arn fromArn(String arn) {

        // TODO (jonjara) move ArnUtils to a common module
        final int NUMBER_OF_ARN_FIELDS = 6;
        // Sample ARNs:
        // arn:aws:codeartifact:<region>:<domain-owner>:domain/<domain-name>
        String[] terms = arn.split(":", NUMBER_OF_ARN_FIELDS);

        String partition = terms[1];
        String service = terms[2];
        String region = terms[3];
        String domainOwner = terms[4];
        String[] compoundName = terms[5].split("/", 2);

        String resourceType = compoundName[0];
        String domainName = compoundName[1];

        return Arn.builder()
            .partition(partition)
            .region(region)
            .service(service)
            .owner(domainOwner)
            .type(resourceType)
            .shortId(domainName)
            .build();
    }

    public static Arn domainArn(
        String partition, String region, String domainOwner, String domainName
    ) {
        return Arn.builder()
            .partition(partition)
            .region(region)
            .service(SERVICE_NAME)
            .owner(domainOwner)
            .type(DOMAIN_RESOURCE_TYPE)
            .shortId(domainName)
            .build();
    }

    public static Arn repoArn(
        String partition, String region, String domainOwner, String domainName, String repoName
    ) {
        return Arn.builder()
            .partition(partition)
            .region(region)
            .service(SERVICE_NAME)
            .owner(domainOwner)
            .type(REPO_RESOURCE_TYPE)
            .shortId(String.format("%s/%s", domainName, repoName))
            .build();
    }

}
