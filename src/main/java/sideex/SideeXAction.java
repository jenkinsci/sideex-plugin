package sideex;

import javax.annotation.Nonnull;

import hudson.Functions;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class SideeXAction extends SideeXBaseAction implements RunAction2, ProminentProjectAction {
	private transient Run<?, ?> build;
	
	public SideeXAction(AbstractItem project) {
		super();
		this.project = project;
    }

	@Override
	public void onAttached(Run<?, ?> build) {
		this.build = build;
	}

    @Override
    public void onLoad(Run<?, ?> build) {
        this.build = build; 
    }

    public Run getRun() { 
        return build;
    }

	@Override
	protected Boolean haveURL() {
		final Job job = (Job) this.project;
		Run run = null;
		if(job != null) {
			run = job.getBuild(job.getLastBuild().getId());
			if (run != null) {
				SideeX tempSideeXJenkinsPlugin = run.getAction(SideeXBuildAction.class).sideeXJenkinsPlugin;
				if(tempSideeXJenkinsPlugin != null) {
					if(!tempSideeXJenkinsPlugin.getReportURL().equals("")) {
						this.sideeXJenkinsPlugin = tempSideeXJenkinsPlugin;
						return true;
					}
				}
			}
		}
		
		return false;
	}
}
