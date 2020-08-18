package sideex;

import hudson.Functions;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.AbstractItem;
import hudson.model.Action;
import jenkins.model.Jenkins;


public abstract class SideeXBaseAction implements Action {
	protected SideeX sideeXJenkinsPlugin;
	protected transient AbstractItem project;

	@Override
	public String getIconFileName() {
		return haveURL()? "/plugin/sideex/images/48x48/cloud-logo.png": null;
	}

	@Override
	public String getDisplayName() {
		return haveURL()? "SideeX View Report": null;
	}

	@Override
	public String getUrlName() {
		return haveURL()? sideeXJenkinsPlugin.getReportURL(): "";
	}
	
	protected abstract Boolean haveURL();
}
