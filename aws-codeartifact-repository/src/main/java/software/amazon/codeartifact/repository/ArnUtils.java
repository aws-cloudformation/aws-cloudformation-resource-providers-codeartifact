package software.amazon.codeartifact.repository;

public class ArnUtils {
    public static final String SERVICE_NAME = "codeartifact";
    public static final String REPO_RESOURCE_TYPE = "repository";
    public static RepositoryArn fromArn(String arn) {

        // TODO (jonjara) move ArnUtils to a common module
        final int NUMBER_OF_ARN_FIELDS = 6;
        // Sample ARNs:
        // arn:aws:codeartifact:<region>:<domain-owner>:repository/<domain-name>/<repo-name>
        String[] terms = arn.split(":", NUMBER_OF_ARN_FIELDS);

        String partition = terms[1];
        String service = terms[2];
        String region = terms[3];
        String domainOwner = terms[4];
        String[] compoundName = terms[5].split("/", 3);

        String resourceType = compoundName[0];
        String domainName = compoundName[1];
        String repoName = compoundName[2];

        return RepositoryArn.builder()
            .partition(partition)
            .region(region)
            .service(service)
            .owner(domainOwner)
            .type(resourceType)
            .domainName(domainName)
            .repoName(repoName)
            .build();
    }

    public static RepositoryArn repoArn(
        String partition, String region, String domainOwner, String domainName, String repoName
    ) {
        return RepositoryArn.builder()
            .partition(partition)
            .region(region)
            .service(SERVICE_NAME)
            .owner(domainOwner)
            .type(REPO_RESOURCE_TYPE)
            .domainName(domainName)
            .repoName(repoName)
            .build();
    }

}
