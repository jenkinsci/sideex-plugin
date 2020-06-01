package sideex;

import java.io.IOException;

import javax.annotation.Nonnull;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public abstract class BuildDropDownList implements Describable<BuildDropDownList> {

	@Override
	public Descriptor<BuildDropDownList> getDescriptor() {
		return Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
	}
	
	public SideeXWebServiceClientAPI getClientAPI(@Nonnull Run<?, ?> build, @Nonnull TaskListener listener,
			String baseURL, ProtocalType type) throws InterruptedException, IOException {
		SideeXWebServiceClientAPI httpService = new SideeXWebServiceClientAPI(baseURL, type);
		
		return httpService;
	}
}

