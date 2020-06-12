package sideex;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class HTTPSDisableItem extends BuildDropDownList {
	private String baseURL;

    @DataBoundConstructor
    public HTTPSDisableItem(String baseURL) {
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

    @Extension
    public static class DescriptorImpl extends BuildDropDownListDescriptor {
        @Override
        public String getDisplayName() {
            return "HTTPS (Disable certificate checking)";
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
				if(!(new URL(baseURL).getProtocol().equals("https"))) {
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
