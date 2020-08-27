package software.amazon.codeartifact.domain;

import org.immutables.value.Value;

import com.google.common.base.MoreObjects;

@Value.Immutable
@Value.Style(allParameters = true, typeImmutable = "*", typeAbstract = "Abstract*")
public abstract class AbstractArn {
    // TODO (jonjara) move this class to a common module and use it for both repositories and domains

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

    // the component from the Id of the resource without the type:
    // "<domainName>/<resourceName>" or "<domainName>"
    public abstract String shortId();

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
            .append(shortId())
            .toString();
    }
}
