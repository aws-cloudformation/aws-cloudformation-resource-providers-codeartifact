package software.amazon.codeartifact.domain;

import org.immutables.value.Value;

import com.google.common.base.MoreObjects;

@Value.Immutable
@Value.Style(allParameters = true, typeImmutable = "*", typeAbstract = "Abstract*")
public abstract class AbstractDomainArn {
    // TODO (jonjara) move this class to a common module
    @Value.Default
    public String partition() {
        return "aws";
    }

    public abstract String service();

    public abstract String region();

    // the accountId component of the ARN
    public abstract String owner();

    // the type of the resource: "repository" or "domain"
    public abstract String type();

    public abstract String domainName();

    public String arn() {
        StringBuilder sb = new StringBuilder().append("arn")
            .append(":")
            .append(partition())
            .append(":")
            .append(service())
            .append(":")
            .append(MoreObjects.firstNonNull(region(), ""))
            .append(":")
            .append(owner());

        return sb.append(":")
            .append(type())
            .append("/")
            .append(domainName())
            .toString();
    }
}
