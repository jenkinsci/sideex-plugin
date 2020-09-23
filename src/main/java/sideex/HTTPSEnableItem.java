package sideex;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

public class HTTPSEnableItem extends BuildDropDownList {
	private String baseURL;
	private String caFilePath;

    @DataBoundConstructor
    public HTTPSEnableItem(String baseURL, String caFilePath) throws Exception {
    	this.baseURL = StringUtils.trim(baseURL);
		this.caFilePath = StringUtils.trim(caFilePath);
		
		if (this.baseURL.charAt(this.baseURL.length() - 1) != '/') {
			this.baseURL = this.baseURL + "/";
		}
    }
    
    @Override
    public SideeXWebServiceClientAPI getClientAPI(@Nonnull Run<?, ?> build, @Nonnull TaskListener listener,
			String baseURL, ProtocolType type) throws InterruptedException, IOException {
		SideeXWebServiceClientAPI clientAPI = new SideeXWebServiceClientAPI(baseURL, type);
		
		return clientAPI;
	}

    @Extension
    public static class DescriptorImpl extends BuildDropDownListDescriptor {
        @Override
        public String getDisplayName() {
            return "HTTPS (Enable certificate checking)";
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
					throw new Exception("Invalid protocol");
				}
				return FormValidation.ok();
			} catch (Exception e) {
				return FormValidation.error(e.getMessage());
			}
		}
		
		public FormValidation doCheckCaFilePath(@QueryParameter String caFilePath) {
			try {
				if(StringUtils.trim(caFilePath).equals("")) {
					throw new Exception("Please enter certificate file path");
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
	
	public String getCaFilePath() {
		return caFilePath;
	}
}
