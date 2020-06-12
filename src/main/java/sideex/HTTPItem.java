package sideex;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
//import jenkins.org.apache.commons.validator.routines.UrlValidator;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.URL;
import javax.annotation.Nonnull;

public class HTTPItem extends BuildDropDownList {
	private String baseURL;

    @DataBoundConstructor
	public HTTPItem(String baseURL) {
    	this.baseURL = StringUtils.trim(baseURL);
    	if (this.baseURL.charAt(this.baseURL.length() - 1) != '/') {
			this.baseURL = this.baseURL + "/";
		}
    }
    
    @Override
    public SideeXWebServiceClientAPI getClientAPI(@Nonnull Run<?, ?> build, @Nonnull TaskListener listener,
			String baseURL, ProtocalType type) throws InterruptedException, IOException {
		SideeXWebServiceClientAPI clientAPI = new SideeXWebServiceClientAPI(baseURL, type);
		
		return clientAPI;
	}
	
    @Override
    public Descriptor<BuildDropDownList> getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension(ordinal=100) // This is displayed at the top as the default
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends BuildDropDownListDescriptor {

        @Override
        public String getDisplayName() {
            return "HTTP";
        }

        @Override
        public boolean isApplicableAsBuildStep() {
            return true;
        }

		public FormValidation doCheckBaseURL(@QueryParameter String baseURL) {
			try {
				URLValidator urlValidator = new URLValidator();
				if(!urlValidator.urlValidator(baseURL)) {
					throw new Exception("Invalid base URL");
				}
				if(!(new URL(baseURL).getProtocol().equals("http"))) {
					throw new Exception("Invalid protocal");
				}
				return FormValidation.ok();
			} catch (Exception e) {
				return FormValidation.error(e.getMessage());
			}
		}
    }
    
	public String getBaseURL() {
		return baseURL;
	}
}
