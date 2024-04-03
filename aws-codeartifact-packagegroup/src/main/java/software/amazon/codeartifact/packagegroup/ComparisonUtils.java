package software.amazon.codeartifact.packagegroup;

import software.amazon.awssdk.services.codeartifact.model.PackageGroupOriginRestrictionMode;

import java.util.Objects;

public class ComparisonUtils {

    public static boolean willUpdateDescription(final ResourceModel desiredModel, final ResourceModel prevModel) {
        if (prevModel == null && desiredModel.getDescription() != null) return true;

        return (prevModel != null) && !Objects.equals(prevModel.getDescription(), desiredModel.getDescription());
    }

    public static boolean willUpdateContactInfo(final ResourceModel desiredModel, final ResourceModel prevModel) {
        if (prevModel == null && desiredModel.getContactInfo() != null) return true;

        return (prevModel != null) && !Objects.equals(prevModel.getContactInfo(), desiredModel.getContactInfo());
    }

    public static boolean willUpdateOriginConfiguration(final ResourceModel desiredModel, final ResourceModel prevModel) {
        return prevModel != null && !Objects.equals(prevModel.getOriginConfiguration(), desiredModel.getOriginConfiguration());
    }

    public static boolean willNotUpdateOriginConfigurationOnCreation(final ResourceModel model) {
        if (model.getOriginConfiguration() == null) return true;

        boolean willNotUpdatePublish = false;
        boolean willNotUpdateExternalUpstream = false;
        boolean willNotUpdateInternalUpstream = false;

        if (model.getOriginConfiguration().getRestrictions().getPublish() == null) {
            willNotUpdatePublish = true;
        } else if (model.getOriginConfiguration().getRestrictions().getPublish().getRestrictionMode().equals(PackageGroupOriginRestrictionMode.INHERIT.toString()) &&
                model.getOriginConfiguration().getRestrictions().getPublish().getRepositories() == null) {
            willNotUpdatePublish = true;
        }

        if (model.getOriginConfiguration().getRestrictions().getExternalUpstream() == null) {
            willNotUpdateExternalUpstream = true;
        } else if (model.getOriginConfiguration().getRestrictions().getExternalUpstream().getRestrictionMode().equals(PackageGroupOriginRestrictionMode.INHERIT.toString()) &&
                model.getOriginConfiguration().getRestrictions().getExternalUpstream().getRepositories() == null) {
            willNotUpdateExternalUpstream = true;
        }

        if (model.getOriginConfiguration().getRestrictions().getInternalUpstream() == null) {
            willNotUpdateInternalUpstream = true;
        } else if (model.getOriginConfiguration().getRestrictions().getInternalUpstream().getRestrictionMode().equals(PackageGroupOriginRestrictionMode.INHERIT.toString()) &&
                model.getOriginConfiguration().getRestrictions().getInternalUpstream().getRepositories() == null) {
            willNotUpdateInternalUpstream = true;
        }

        return willNotUpdatePublish && willNotUpdateExternalUpstream && willNotUpdateInternalUpstream;
    }
}
