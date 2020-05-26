
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import wagu.Block;
import wagu.Board;

public class SideeXJenkinsPlugin extends Builder {
	private String baseURL;
	private String stateTime;
	private String inputsFilePath;
	private String reportFolderPath;

	@DataBoundConstructor
	public SideeXJenkinsPlugin(String baseURL, String stateTime, String inputsFilePath, String reportFolderPath) {
		this.baseURL = StringUtils.trim(baseURL);
		this.stateTime = StringUtils.trim(stateTime);
		this.inputsFilePath = StringUtils.trim(inputsFilePath);
		this.reportFolderPath = StringUtils.trim(reportFolderPath);

		if (this.baseURL.charAt(this.baseURL.length() - 1) != '/') {
			this.baseURL = this.baseURL + "/";
		}
		if (this.stateTime == "" || Long.valueOf(this.stateTime) < 2000) {
			this.stateTime = "2000";
		}
	}

	@Override
	public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		HttpService httpService = new HttpService();
		FilePath inputsFilePath = build.getProject().getSomeWorkspace().child(getInputsFilePath());
		FilePath reportFolderPath = build.getProject().getSomeWorkspace().child(getReportFolderPath());
		File inputsFile = new File(inputsFilePath.getRemote());
		File reportFolder = new File(reportFolderPath.getRemote());
		String tokenResponse = "", token = "", stateResponse = "", state = "", onlineReportURL = "", downloadReportURL = "", logUrl = "";
		boolean running = true, passed, first = true;
		Map<String, File> fileParams = new HashMap<String, File>();
		Map<String, String> params = new HashMap<String, String>();
		JSONArray summary;

		if (!(inputsFile.exists() && !inputsFile.isDirectory())) {
			listener.error("Specified test suites file path '" + inputsFilePath + "' does not exist.");
			build.setResult(Result.FAILURE);
			return true;
		}

		fileParams.put(inputsFile.getName(), inputsFile);
		try {
			tokenResponse = httpService.runTestSuite(getBaseURL() + "sideex-webservice", fileParams);
		} catch (IOException e) {
			listener.error(
					"SideeX WebService Plugin cannot connect to your server, please check SideeXWebServicePlugin's base URL settings and the state of your server");
			throw e;
		}

		if (!tokenResponse.trim().equals("")) {
			token = JSONObject.fromObject(tokenResponse).getString("token");
			listener.getLogger().println(token);

			while (running) {
				params.put("token", token);

				try {
					stateResponse = httpService.getState(getBaseURL() + "sideex-webservice-state", params);
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
					onlineReportURL = JSONObject.fromObject(stateResponse).getJSONObject("reports").getString("onlineReportURL").toString();
					downloadReportURL = JSONObject.fromObject(stateResponse).getJSONObject("reports").getString("downloadReportURL").toString();
					logUrl = JSONObject.fromObject(stateResponse).getJSONObject("logs").getString("url").toString();
					summary = JSONObject.fromObject(stateResponse).getJSONObject("reports").getJSONArray("summarry");

					if (!getReportFolderPath().equals("")) {
						if (!reportFolder.exists()) {
							reportFolder.mkdir();
						}
						FileUtils.cleanDirectory(reportFolder);
						httpService.download(getBaseURL() + "sideex-webservice-reports", params,
								reportFolderPath.getRemote() + "/reports.zip");
						httpService.download(getBaseURL() + "sideex-webservice-logs", params,
								reportFolderPath.getRemote() + "/logs.zip");
						new UnzipUtility().unzip(reportFolderPath.getRemote() + "/reports.zip",
								reportFolderPath.getRemote());
						new UnzipUtility().unzip(reportFolderPath.getRemote() + "/logs.zip",
								reportFolderPath.getRemote());
						new File(reportFolderPath.getRemote() + "/reports.zip").delete();
						new File(reportFolderPath.getRemote() + "/logs.zip").delete();
					}
					listener.getLogger().println("The test report can be viewed at " + onlineReportURL + ".");
					listener.getLogger().println("The test report can be downloaded at " + downloadReportURL + ".");
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
					Thread.sleep(Long.valueOf(this.stateTime));
				}
			}
		}

		return true;
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

		public FormValidation doCheckIpAddress(@QueryParameter String ipAddress) {
			try {
				new URL(ipAddress);
				return FormValidation.ok();
			} catch (Exception e) {
				return FormValidation.error("Please enter a hostname");
			}
		}

		public FormValidation doCheckStateTime(@QueryParameter String stateTime) {
			try {
				Long.valueOf(stateTime);
				return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("Please enter a periodically time");
			}
		}
	}

	void parseLog(String logs, BuildListener listener) {
		JSONObject log = JSONObject.fromObject(logs);
		for (int i = 0; i < log.getJSONArray("logs").size(); i++) {
			showRunnerLog(JSONObject.fromObject(log.getJSONArray("logs").get(i)).getString("type"),
					JSONObject.fromObject(log.getJSONArray("logs").get(i)).getString("message"), listener);
		}
	}

	void showRunnerLog(String type, String str, BuildListener listener) {
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

	public String getInputsFilePath() {
		return inputsFilePath;
	}

	public String getStateTime() {
		return stateTime;
	}

	public String getReportFolderPath() {
		return reportFolderPath;
	}

	public String getBaseURL() {
		return baseURL;
	}

	public String getSummarryFormat(JSONObject object, int size) {
		final int maxWidth = 115;
		final int leftRowWidth = 25;
		final int leftBlockWidth = 27;
		final int rightRowWidth = maxWidth - leftBlockWidth - 1;
		int suiteHeight = 1, contentHeight = 1;

		ArrayList<String> summarryList = summarryObjectToList(object);
		// Create a substring that matches the width of the right field
		String suitesArray[] = conformWidthColumn(summarryList.get(0), rightRowWidth);
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
//				block.add(block.get(block.size() - 1).getBelowBlock().setDataAlign(Block.DATA_CENTER));
				// Get the reference of the Header Block in the List and build the Block on the right.
				block.add(block.get(block.size() - 1).getBelowBlock()
						.setRightBlock(new Block(board, rowWidthLength.get(2), rowWidthLength.get(3), row.get(1))
								.setDataAlign(Block.DATA_CENTER)));
			} else {
				// Get the previous block's reference and set the next block's value.
				block.get(block.size() - 1)
						.setBelowBlock(new Block(board, rowWidthLength.get(0), rowWidthLength.get(1), row.get(0))
								.setDataAlign(Block.DATA_CENTER));
//				block.add(block.get(block.size() - 2).getBelowBlock().setDataAlign(Block.DATA_CENTER));
				// Get the previous Block's reference to build the Block on the right.
				block.add(block.get(block.size() - 1).getBelowBlock()
						.setRightBlock(new Block(board, rowWidthLength.get(2), rowWidthLength.get(3), row.get(1))
								.setDataAlign(Block.DATA_CENTER)));
			}
		}

		return block.get(0);
	}

	public String[] conformWidthColumn(String str, int num) {
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
