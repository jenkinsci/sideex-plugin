package sideex;

import hudson.Functions;
import hudson.model.AbstractItem;
import hudson.model.Action;


public abstract class SideeXBaseAction implements Action {
	protected SideeX sideeXJenkinsPlugin;
	protected transient AbstractItem project;

	@Override
	public String getIconFileName() {
//		return haveURL()? "/plugin/sideex/logo-48.png": null;
		return haveURL()? "graph.gif": null;
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
