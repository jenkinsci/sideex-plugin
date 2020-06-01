package sideex;

import hudson.model.Descriptor;

public abstract class BuildDropDownListDescriptor extends Descriptor<BuildDropDownList> {
    public boolean isApplicableAsBuildStep() {
        return false;
    }
}