package software.amazon.codeartifact.domain;

import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awssdk.utils.CollectionUtils;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-codeartifact-domain.json");
    }

    public Map<String, String> resourceDefinedTags(final ResourceModel resourceModel) {
        if (CollectionUtils.isNullOrEmpty(resourceModel.getTags())) {
            return null;
        }

        return resourceModel.getTags()
            .stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }

}
