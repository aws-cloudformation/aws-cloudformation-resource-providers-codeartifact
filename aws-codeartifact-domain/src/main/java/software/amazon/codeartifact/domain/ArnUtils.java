package software.amazon.codeartifact.domain;

public class ArnUtils {
    public static final String SERVICE_NAME = "codeartifact";
    public static final String DOMAIN_RESOURCE_TYPE = "domain";

    public static DomainArn fromArn(String arn) {

        // TODO (jonjara) move ArnUtils to a common module
        final int NUMBER_OF_ARN_FIELDS = 6;
        // Sample ARNs:
        // arn:aws:codeartifact:<region>:<domain-owner>:domain/<domain-name>
        // arn:aws:codeartifact:<region>:<domain-owner>:repository/<domain-name>/<repo-name>
        String[] terms = arn.split(":", NUMBER_OF_ARN_FIELDS);

        String partition = terms[1];
        String service = terms[2];
        String region = terms[3];
        String domainOwner = terms[4];
        String[] compoundName = terms[5].split("/", 2);

        String resourceType = compoundName[0];
        String domainName = compoundName[1];

        return DomainArn.builder()
            .partition(partition)
            .region(region)
            .service(service)
            .owner(domainOwner)
            .type(resourceType)
            .domainName(domainName)
            .build();
    }

    public static DomainArn domainArn(
        String partition, String region, String domainOwner, String domainName
    ) {
        return DomainArn.builder()
            .partition(partition)
            .region(region)
            .service(SERVICE_NAME)
            .owner(domainOwner)
            .type(DOMAIN_RESOURCE_TYPE)
            .domainName(domainName)
            .build();
    }

}
