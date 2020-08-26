package software.amazon.codeartifact.repository;

import java.util.Objects;
import java.util.Set;

import com.google.common.collect.Sets;

public class ComparisonUtils {

    public static boolean willAddUpstreams(final ResourceModel desiredModel, final ResourceModel prevModel) {
        return prevModel.getUpstreams() == null && desiredModel.getUpstreams() != null;
    }

    public static boolean willNotUpdateDescription(final ResourceModel desiredModel, final ResourceModel prevModel) {
        return Objects.equals(prevModel.getDescription(), desiredModel.getDescription());
    }
    public static boolean willUpdateCreateOnlyProperty(final ResourceModel desiredModel, final ResourceModel prevModel) {
        return !Objects.equals(desiredModel.getRepositoryName(), prevModel.getRepositoryName()) ||
            !Objects.equals(desiredModel.getDomainName(), prevModel.getDomainName()) ||
            !Objects.equals(desiredModel.getDomainOwner(), prevModel.getDomainOwner());
    }

    public static boolean upstreamsAreEqual(final ResourceModel desiredModel, final ResourceModel prevModel) {
        Set<String> prevUpstreamsSet = prevModel.getUpstreams() == null ? null :
            Sets.newHashSet(prevModel.getUpstreams());

        Set<String> desiredUpstreamsSet = desiredModel.getUpstreams() == null ? null :
            Sets.newHashSet(desiredModel.getUpstreams());

        return Objects.equals(prevUpstreamsSet, desiredUpstreamsSet);
    }
}
