package sideex;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import wagu.Block;
import wagu.Board;

public class SideeX extends Builder implements SimpleBuildStep {
	private final BuildDropDownList protocalMenu;
	private String stateTime;
	private String testCaseFilePath;
	private String reportFolderPath;
	private String reportURL;
	
	
	@DataBoundConstructor
	public SideeX(BuildDropDownList protocalMenu, String stateTime, String testCaseFilePath, String reportFolderPath) {
		this.protocalMenu =  protocalMenu;
		this.stateTime = StringUtils.trim(stateTime);
		this.testCaseFilePath = StringUtils.trim(testCaseFilePath);
		this.reportFolderPath = StringUtils.trim(reportFolderPath);
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return jobType == FreeStyleProject.class;
		}

		@Override
		public String getDisplayName() {
			return "Execute SideeX Web Testing";
		}

		public List<BuildDropDownListDescriptor> getProtocalMenu() {
			List<BuildDropDownListDescriptor> descriptors = Jenkins.get()
					.getDescriptorList(BuildDropDownList.class);
			List<BuildDropDownListDescriptor> supportedStrategies = new ArrayList<>(descriptors.size());

			for (BuildDropDownListDescriptor descriptor : descriptors) {
				if (descriptor.isApplicableAsBuildStep()) {
					supportedStrategies.add(descriptor);
				}
			}

			return supportedStrategies;
		}
		
		public FormValidation doCheckStateTime(@QueryParameter String stateTime) {
			try {
				Long.valueOf(stateTime);
				return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("Please enter a periodically time");
			}
		}
		
		public FormValidation doCheckTestCaseFilePath(@QueryParameter String testCaseFilePath) {
			try {
				if(StringUtils.trim(testCaseFilePath).equals("")) {
					throw new Exception("Please enter test case file path");
				}
				return FormValidation.ok();
			} catch (Exception e) {
				return FormValidation.error(e.getMessage());
			}
		}
	}

	@SuppressWarnings("unused")
	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		this.reportURL = "";
		SideeXWebServiceClientAPI wsClient = null;
		
		build.addAction(new SideeXBuildAction(this));
		
		// Dropdown menu
		if (protocalMenu instanceof HTTPItem) {
			HTTPItem httpItem = (HTTPItem) protocalMenu;
			wsClient = this.protocalMenu.getClientAPI(build, listener, httpItem.getBaseURL(), ProtocalType.HTTP);
		} else if(protocalMenu instanceof HTTPSDisableItem) {
			HTTPSDisableItem httpsDisableItem = (HTTPSDisableItem) protocalMenu;
			wsClient = this.protocalMenu.getClientAPI(build, listener, httpsDisableItem.getBaseURL(), ProtocalType.HTTPS_DISABLE);
		} else if(protocalMenu instanceof HTTPSEnableItem) {
			HTTPSEnableItem httpsEnableItem = (HTTPSEnableItem) protocalMenu;
			wsClient = this.protocalMenu.getClientAPI(build, listener, httpsEnableItem.getBaseURL(), ProtocalType.HTTPS_ENABLE);
			File inputsFile = new File(httpsEnableItem.getCaFilePath());
			if (!(inputsFile.exists() && !inputsFile.isDirectory())) {
				listener.error("Specified Certificate file path '" + httpsEnableItem.getCaFilePath() + "' does not exist.");
				build.setResult(Result.FAILURE);
			}
			try {
				wsClient.setCertificate(httpsEnableItem.getCaFilePath());
			} catch (Exception e) {
				listener.error(e.getMessage());
				build.setResult(Result.FAILURE);
			}
		}
		
		if(wsClient == null) 
			return;
		
		
		FilePath testCaseFilePath = workspace.child(getTestCaseFilePath());
		FilePath reportFolderPath = workspace.child(getReportFolderPath());
		File testCaseFile = new File(testCaseFilePath.getRemote());
		File reportFolder = new File(reportFolderPath.getRemote());
		String tokenResponse = "", token = "", stateResponse = "", state = "", reportURL = "", logUrl = "";
		boolean running = true, passed, first = true, isTestCaseFolder = false;
		Map<String, File> fileParams = new HashMap<String, File>();
		JSONArray summary;

		if(testCaseFile.exists() && !testCaseFile.isDirectory()) {
			testCaseFile = new File(testCaseFilePath.getRemote());
		} else if(testCaseFile.isDirectory()) {
			ZipUtility zipUtil = new ZipUtility();
			String[] myFiles = {testCaseFile.getAbsolutePath()};
			zipUtil.zip(myFiles, workspace.getRemote()+"\\"+testCaseFile.getName()+".zip");
			testCaseFile = new File(workspace.getRemote()+"\\"+testCaseFile.getName()+".zip");
			isTestCaseFolder = true;
		} else {
			listener.error("Specified test suites file path '" + testCaseFilePath + "' does not exist.");
			build.setResult(Result.FAILURE);
		}
		
		fileParams.put(testCaseFile.getName(), testCaseFile);
		try {
			tokenResponse = wsClient.runTestSuite(fileParams);
		} catch (IOException e) {
			listener.error(
					"SideeX WebService Plugin cannot connect to your server, please check SideeXWebServicePlugin's settings and the state of your server");
			throw e;
		}

		if (!tokenResponse.trim().equals("")) {
			token = JSONObject.fromObject(tokenResponse).getString("token");
			listener.getLogger().println(token);

			while (running) {

				try {
					stateResponse = wsClient.getState(token);
				} catch (IOException e) {
					listener.error(
							"SideeX WebService Plugin cannot connect to your server, please check SideeXWebServicePlugin's base URL settings and the state of your server");
					throw e;
				}
				state = JSONObject.fromObject(stateResponse).getJSONObject("webservice").getString("state");
				if (first && !state.equals("complete")) {
					listener.getLogger().println("SideeX WebSerivce state is " + state);
					first = false;
				}

				if (state.equals("complete")) {
					listener.getLogger().println("SideeX WebSerivce state is " + state);
					running = false;
					passed = JSONObject.fromObject(stateResponse).getJSONObject("reports").getBoolean("passed");
					reportURL = JSONObject.fromObject(stateResponse).getJSONObject("reports").getString("url").toString();
					logUrl = JSONObject.fromObject(stateResponse).getJSONObject("logs").getString("url").toString();
					summary = JSONObject.fromObject(stateResponse).getJSONObject("reports").getJSONArray("summarry");

					if (!getReportFolderPath().equals("")) {
						if (!reportFolder.exists()) {
							boolean reportDir = reportFolder.mkdirs();
						}
						FileUtils.cleanDirectory(reportFolder);
						Map<String, String> formData = new HashMap<String, String>();
	                    formData.put("token", token);
	                    wsClient.download(formData, reportFolderPath.getRemote() + "/logs.zip", 1);
	                    formData.put("file", "reports.zip");
	                    wsClient.download(formData, reportFolderPath.getRemote() + "/reports.zip", 0);
						new UnzipUtility().unzip(reportFolderPath.getRemote() + "/reports.zip",
								reportFolderPath.getRemote());
						new UnzipUtility().unzip(reportFolderPath.getRemote() + "/logs.zip",
								reportFolderPath.getRemote());
						boolean isDelReports = new File(reportFolderPath.getRemote() + "/reports.zip").delete();
						boolean isDelLogs = new File(reportFolderPath.getRemote() + "/logs.zip").delete();
					}
					this.reportURL = reportURL;
					listener.getLogger().println("The test report can be downloaded at " + reportURL + ".");
					listener.getLogger().println("The log can be downloaded at " + logUrl + ".");

					for (int i = 0; i < summary.size(); i++) {
						listener.getLogger().println(getSummarryFormat(JSONObject.fromObject(summary.get(i)), 60));
					}
					if (passed == false) {
						listener.error("Test Case Failed");
						build.setResult(Result.FAILURE);
					} else {
						listener.getLogger().println("Test Case Passed");
					}
				} else if (state.equals("error")) {
					running = false;
					listener.error("SideeX WebService has encountered a runtime error");
					this.parseLog(JSONObject.fromObject(stateResponse).getJSONObject("webservice").getString("errorMessage"),
							listener);
					logUrl = JSONObject.fromObject(stateResponse).getJSONObject("logs").getString("url").toString();
					listener.getLogger().println("The log can be download at " + logUrl + ".");
					build.setResult(Result.FAILURE);
				} else {
					Thread.sleep(Long.parseLong(this.stateTime));
				}
			}
		}
		SideeXWebServiceClientAPI.setHTTPSToDefault();
		if(isTestCaseFolder == true) {
			if(!testCaseFile.delete()) {
				listener.error("Does not exits the "+testCaseFile.getName());
			}
		}
	}
	


	@Override
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
		List<Action> actions = new ArrayList<>();
		actions.add(new SideeXAction(project));
		return actions;
	}

	
	void parseLog(String logs, TaskListener listener) {
		JSONObject log = JSONObject.fromObject(logs);
		for (int i = 0; i < log.getJSONArray("logs").size(); i++) {
			showRunnerLog(JSONObject.fromObject(log.getJSONArray("logs").get(i)).getString("type"),
					JSONObject.fromObject(log.getJSONArray("logs").get(i)).getString("message"), listener);
		}
	}

	void showRunnerLog(String type, String str, TaskListener listener) {
		String output = "";
		switch (type) {
		case "info":
			output = "[INFO] " + str;
			break;
		case "warn":
			output = "[WARN] " + str;
			break;
		case "error":
			output = "[ERROR] " + str;
			break;
		case "debug":
			output = "[DEBUG] " + str;
			break;
		default:
			output = "[INFO] " + str;
			break;
		}
		listener.getLogger().println(output);
	}
	
	public BuildDropDownList getProtocalMenu() {
		return protocalMenu;
	}

	public String getTestCaseFilePath() {
		return testCaseFilePath;
	}
	

	public String getStateTime() {
		return stateTime;
	}

	public String getReportFolderPath() {
		return reportFolderPath;
	}

	public String getReportURL() {
		return reportURL;
	}

	public String getSummarryFormat(JSONObject object, int size) {
		final int maxWidth = 115;
		final int leftRowWidth = 25;
		final int leftBlockWidth = 27;
		final int rightRowWidth = maxWidth - leftBlockWidth - 1;
		int suiteHeight = 1, contentHeight = 1;

		ArrayList<String> summarryList = summarryObjectToList(object);
		// Create a substring that matches the width of the right field
		String suitesArray[] = wrapText(summarryList.get(0), rightRowWidth);
		String suites = String.join("\n", suitesArray);

		if (suitesArray.length == 1) {
			suiteHeight = 1;
		} else {
			suiteHeight = suitesArray.length + 2;
		}

		Board board = new Board(maxWidth);
		// Set the width and height of the Header.
		List<Integer> headerWidthLength = Arrays.asList(maxWidth - 2, contentHeight);
		// Set the width and height of the left and right Blocks
		List<List<Integer>> colWidthLength = Arrays.asList(
				Arrays.asList(leftRowWidth, suiteHeight, rightRowWidth, suiteHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight),
				Arrays.asList(leftRowWidth, contentHeight, rightRowWidth, contentHeight));
		// Set the value of the left and right of the row.
		List<List<String>> rowsList = Arrays.asList(Arrays.asList("Suites", suites),
				Arrays.asList("SideeX Version", summarryList.get(1)), Arrays.asList("Browser", summarryList.get(2)),
				Arrays.asList("Platform", summarryList.get(3)), Arrays.asList("Language", summarryList.get(4)),
				Arrays.asList("Start Time", summarryList.get(5)), Arrays.asList("End Time", summarryList.get(6)),
				Arrays.asList("Passed / Total Suite(s)", summarryList.get(7)),
				Arrays.asList("Passed / Total Case(s)", summarryList.get(8)));

		Block suitesBlock = listToBlock(board, rowsList, colWidthLength, "Report Summarry", headerWidthLength);

		return board.setInitialBlock(suitesBlock.setDataAlign(Block.DATA_CENTER)).build().getPreview();
	}

	public ArrayList<String> summarryObjectToList(JSONObject object) {
		DateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String summarryTitle[] = { "Suites", "SideeXVersion", "Browser", "Platform", "Language", "StartTime", "EndTime",
				"PassedSuite", "TotalSuite", "PassedCase", "TotalPassedCase" };

		ArrayList<String> summarryList = new ArrayList<String>();
		summarryList.add(object.getJSONArray(summarryTitle[0]).toString().replaceAll("[\\[\\]\"]", ""));
		summarryList.add(object.getString(summarryTitle[1]).replaceAll("[\\[\\]]", "").replaceAll(",", "."));
		summarryList.add(object.getString(summarryTitle[2]));
		summarryList.add(object.getString(summarryTitle[3]));
		summarryList.add(object.getString(summarryTitle[4]));
		summarryList.add(sdf.format(object.getLong(summarryTitle[5])).replaceAll("/", ""));
		summarryList.add(sdf.format(object.getLong(summarryTitle[6])).replaceAll("/", ""));
		summarryList.add(object.getString(summarryTitle[7]) + " / " + object.getString(summarryTitle[8]));
		summarryList.add(object.getString(summarryTitle[9]) + " / " + object.getString(summarryTitle[10]));

		return summarryList;
	}

	public Block listToBlock(Board board, List<List<String>> rowsList, List<List<Integer>> colWidthLength,
			String header, List<Integer> headerWidthLength) {
		List<Block> block = new ArrayList<>();

		for (int i = 0; i < rowsList.size(); i++) {
			List<String> row = rowsList.get(i);
			List<Integer> rowWidthLength = colWidthLength.get(i);
			if (block.size() == 0) {// Set the Header Block and the next Block
				// Create a Block, add the header to the List
				block.add(new Block(board, headerWidthLength.get(0), headerWidthLength.get(1), header)
						.setDataAlign(Block.DATA_CENTER));
				// Get the Header Block's reference in the List and set the next Block's value.
				block.get(block.size() - 1)
						.setBelowBlock(new Block(board, rowWidthLength.get(0), rowWidthLength.get(1), row.get(0))
								.setDataAlign(Block.DATA_CENTER));
				// Get the reference of the Header Block in the List and build the Block on the right.
				block.add(block.get(block.size() - 1).getBelowBlock()
						.setRightBlock(new Block(board, rowWidthLength.get(2), rowWidthLength.get(3), row.get(1))
								.setDataAlign(Block.DATA_CENTER)));
			} else {
				// Get the previous block's reference and set the next block's value.
				block.get(block.size() - 1)
						.setBelowBlock(new Block(board, rowWidthLength.get(0), rowWidthLength.get(1), row.get(0))
								.setDataAlign(Block.DATA_CENTER));
				// Get the previous Block's reference to build the Block on the right.
				block.add(block.get(block.size() - 1).getBelowBlock()
						.setRightBlock(new Block(board, rowWidthLength.get(2), rowWidthLength.get(3), row.get(1))
								.setDataAlign(Block.DATA_CENTER)));
			}
		}

		return block.get(0);
	}

	public String[] wrapText(String str, int num) {
		ArrayList<String> result = new ArrayList<String>();
		StringBuilder temp = new StringBuilder();
		int index = 0;
		String arr[] = str.split(",");
		temp.append(arr[0]);
		for (int i = 1; i < arr.length; i++) {
			if ((temp.toString().length() + arr[i].length() + 2) < num) {
				temp.append(", " + arr[i]);
			} else {
				result.add(index++, temp.toString() + ",");
				temp = new StringBuilder(arr[i]);
			}
		}
		result.add(index, temp.toString());
		String[] array = new String[result.size()];
		System.arraycopy(result.toArray(), 0, array, 0, result.size());

		return array;
	}
}

