package sideex;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.util.FormValidation;
import jenkins.org.apache.commons.validator.routines.UrlValidator;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import wagu.Block;
import wagu.Board;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
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
    public SideeXWebServiceClientAPI getClientAPI(@Nonnull AbstractBuild<?,?> build, @Nonnull BuildListener listener,
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
				UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
				if(!urlValidator.isValid(baseURL)) {
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
