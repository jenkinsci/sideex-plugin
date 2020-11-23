package sideex;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import hudson.util.DirScanner;
import hudson.util.FormValidation;
import hudson.util.io.ArchiverFactory;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import wagu.Block;
import wagu.Board;

public class SideeX extends Builder implements SimpleBuildStep {
	private final BuildDropDownList protocolMenu;
	private String stateTime;
	private String testCaseFilePath;
	private String reportURL;
	
	
	@DataBoundConstructor
	public SideeX(BuildDropDownList protocolMenu, String stateTime, String testCaseFilePath) {
		this.protocolMenu =  protocolMenu;
		this.stateTime = StringUtils.trim(stateTime);
		this.testCaseFilePath = StringUtils.trim(testCaseFilePath);
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

		public List<BuildDropDownListDescriptor> getProtocolMenu() {
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
		if (protocolMenu instanceof HTTPItem) {
			HTTPItem httpItem = (HTTPItem) protocolMenu;
			wsClient = this.protocolMenu.getClientAPI(build, listener, httpItem.getBaseURL(), ProtocolType.HTTP);
		} else if(protocolMenu instanceof HTTPSDisableItem) {
			HTTPSDisableItem httpsDisableItem = (HTTPSDisableItem) protocolMenu;
			wsClient = this.protocolMenu.getClientAPI(build, listener, httpsDisableItem.getBaseURL(), ProtocolType.HTTPS_DISABLE);
		} else if(protocolMenu instanceof HTTPSEnableItem) {
			HTTPSEnableItem httpsEnableItem = (HTTPSEnableItem) protocolMenu;
			wsClient = this.protocolMenu.getClientAPI(build, listener, httpsEnableItem.getBaseURL(), ProtocolType.HTTPS_ENABLE);
			FilePath inputsFileFilePath = workspace.child(httpsEnableItem.getCaFilePath());
			if (!(inputsFileFilePath.exists() && !inputsFileFilePath.isDirectory())) {
				listener.error("Specified Certificate file path '" + httpsEnableItem.getCaFilePath() + "' does not exist.");
				build.setResult(Result.FAILURE);
			}
			try {
				wsClient.setCertificate(inputsFileFilePath);
			} catch (Exception e) {
				listener.error(e.getMessage());
				build.setResult(Result.FAILURE);
			}
		}
		
		if(wsClient == null) 
			return;
		
		FilePath testCaseFilePath = workspace.child(getTestCaseFilePath());
		String tokenResponse = "", token = "", stateResponse = "", state = "", logUrl = "";
		boolean running = true, passed, first = true, isTestCaseFolder = false;
		Map<String, FilePath> fileParams = new HashMap<String, FilePath>();
		JSONArray summary;

		if(!testCaseFilePath.exists()) {
			throw new Error("Do not exist the file "+ testCaseFilePath.getRemote());
		} else if(testCaseFilePath.isDirectory()) {
			FilePath tempDir = workspace.child(workspace.getRemote());
			tempDir = tempDir.createTempDir(testCaseFilePath.getName(), null);
			testCaseFilePath.copyRecursiveTo(tempDir);
			
			FilePath tempZip = workspace.child(tempDir.getName()+".zip");
		    tempDir.zip(tempZip);
		    testCaseFilePath = tempZip;
			isTestCaseFolder = true;
		}
		
		fileParams.put(testCaseFilePath.getName(), testCaseFilePath);
		try {
			tokenResponse = JSONObject.fromObject(wsClient.runTestSuite(fileParams)).getString("token");
		} catch (IOException e) {
			listener.error("SideeX WebService Plugin cannot connect to your server, " + 
							"please check SideeXWebServicePlugin's settings and the state of your server");
			listener.error(e.getMessage());
			throw e;
		}

		if (!tokenResponse.trim().equals("")) {
			listener.getLogger().println("SideeX WebService Token: "+tokenResponse);

			while (running) {
				try {
					stateResponse = wsClient.getState(tokenResponse);
				} catch (IOException e) {
					listener.error("SideeX WebService Plugin cannot connect to your server, "+ 
								"please check SideeXWebServicePlugin's base URL settings and the state of your server");
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
					this.reportURL = JSONObject.fromObject(stateResponse).getJSONObject("reports").getString("url").toString();
					summary = JSONObject.fromObject(stateResponse).getJSONObject("reports").getJSONArray("summarry");

					this.parseError(JSONObject.fromObject(stateResponse).getJSONObject("webservice"), listener);
					listener.getLogger().println("The test report can be downloaded at " + this.reportURL + ".");

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
					this.parseError(JSONObject.fromObject(stateResponse).getJSONObject("webservice"), listener);
					build.setResult(Result.FAILURE);
				} else {
					Thread.sleep(Long.parseLong(this.stateTime));
				}
			}
		}
		SideeXWebServiceClientAPI.setHTTPSToDefault();
		if(isTestCaseFolder == true) {
			if(!testCaseFilePath.delete()) {
				listener.error("Does not exits the "+testCaseFilePath.getName());
			}
		}
	}
	
	@Override
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
		List<Action> actions = new ArrayList<>();
		actions.add(new SideeXAction(project));
		return actions;
	}

	void parseError(JSONObject webservice, TaskListener listener) {
		try {
			for (int i = 0; i < webservice.getJSONArray("error").size(); i++) {
				showRunnerLog(JSONObject.fromObject(webservice.getJSONArray("error").get(i)).getString("type"),
						JSONObject.fromObject(webservice.getJSONArray("error").get(i)).getString("message"), listener);
			}
		} catch (Exception e) {}
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
	
	public BuildDropDownList getProtocolMenu() {
		return protocolMenu;
	}

	public String getTestCaseFilePath() {
		return testCaseFilePath;
	}
	

	public String getStateTime() {
		return stateTime;
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

