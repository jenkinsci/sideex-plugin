package sideex;

public class SideeXBuildAction extends SideeXBaseAction {

	public SideeXBuildAction(SideeX sideeXJenkinsPlugin) {
		super();
		this.sideeXJenkinsPlugin = sideeXJenkinsPlugin;
	}
	
	@Override
	public String getIconFileName() {
		return sideeXJenkinsPlugin.getReportURL().equals("")? null: "/plugin/sideex/images/48x48/cloud-logo.png";
	}

	@Override
	public String getDisplayName() {
		return sideeXJenkinsPlugin.getReportURL().equals("")? null: "SideeX View Report";
	}

	@Override
	public String getUrlName() {
		return sideeXJenkinsPlugin.getReportURL().equals("")? "": sideeXJenkinsPlugin.getReportURL();
	}

	@Override
	protected Boolean haveURL() {
		return false;
	}

}
