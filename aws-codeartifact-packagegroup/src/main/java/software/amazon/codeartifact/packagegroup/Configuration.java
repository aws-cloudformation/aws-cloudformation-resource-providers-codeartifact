package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.utils.CollectionUtils;

import java.util.Map;
import java.util.stream.Collectors;

class Configuration extends BaseConfiguration {
    public Configuration() {
        super("aws-codeartifact-packagegroup.json");
    }

    public Map<String, String> resourceDefinedTags(final ResourceModel resourceModel) {
        if (CollectionUtils.isNullOrEmpty(resourceModel.getTags()))
            return null;

        return resourceModel.getTags()
                .stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }
}
