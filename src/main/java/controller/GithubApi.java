package controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.NoSuchPageException;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import model.AttendanceEventModel;
import model.LogDataModel;
import model.MySqlDbImports;
import model.MySqlDbLogging;
import model.StudentModel;
import model.StudentNameModel;

public class GithubApi {
	private static final int NEW_GITUSER_UPDATE_WINDOW = 2; // Months

	private MySqlDbImports sqlDbImports;
	private RepositoryService repoService;
	private CommitService commitService;

	// Repo lists for workshop and levels 0 - 5
	private List<Repository> repoListLevelIntro;
	private List<Repository> repoListLevel0;
	private List<Repository> repoListLevel1;
	private List<Repository> repoListLevel2;
	private List<Repository> repoListLevel3;
	private List<Repository> repoListLevel4;
	private List<Repository> repoListLevel5;

	public GithubApi(MySqlDbImports sqlDbImports, String githubToken) {
		this.sqlDbImports = sqlDbImports;

		// OAuth2 token authentication
		GitHubClient client = new GitHubClient();
		client.setOAuth2Token(githubToken);

		// Get Repo and Commit services
		repoService = new RepositoryService(client);
		commitService = new CommitService(client);
	}

	public boolean importGithubComments(String startDate, ArrayList<AttendanceEventModel> eventList) {
		// eventList contains all attendance since 'startDate' with null comments
		String lastGithubUser = "";
		List<Repository> repoList = null;

		for (int i = 0; i < eventList.size(); i++) {
			// Get commit info from DB for each student/date combo
			AttendanceEventModel event = eventList.get(i);
			if (event.getGithubComments() != null && !event.getGithubComments().trim().equals(""))
				// Skip non-empty comments
				continue;
			
			String gitUser = event.getGithubName().toLowerCase();

			try {
				if (!gitUser.equals(lastGithubUser)) {
					// New github user, need to get new repo array
					lastGithubUser = gitUser;
					repoList = repoService.getRepositories(gitUser);

					// Loop through all repos to check for updates
					for (int j = 0; j < repoList.size(); j++) {
						Repository repo = repoList.get(j);

						// Update all user comments in this repo list
						updateUserGithubComments(gitUser, startDate, eventList, repo);
					}
				}

			} catch (IOException e) {
				if (e.getMessage().startsWith("API rate limit exceeded")) {
					// Rate limit exceeded, so abort
					MySqlDbLogging.insertLogData(LogDataModel.GITHUB_IMPORT_ABORTED,
							new StudentNameModel("", "", false), 0, ": Github API rate limit exceeded ***");
					return false;

				} else {
					MySqlDbLogging.insertLogData(LogDataModel.GITHUB_IMPORT_FAILURE, event.getStudentNameModel(),
							event.getClientID(), " for gitUser '" + gitUser + "': " + e.getMessage());
				}
			}
		}
		return true;
	}

	public void importGithubCommentsByLevel(int level, String startDate, List<Repository> repoList,
			ArrayList<AttendanceEventModel> eventList) {
		// Check for empty event list
		if (eventList.size() == 0)
			return;

		// Get all repositories by league level
		if (repoList == null) {
			repoList = getRepoListByLevel(level);
			if (repoList == null)
				return;
		}

		// eventList contains all attendance since 'startDate' with null comments
		String lastGithubUser = "";

		for (int i = 0; i < eventList.size(); i++) {
			// Get commit info from DB for each student/date combo
			AttendanceEventModel event = eventList.get(i);

			// Skip this event if it has already been processed
			if (!event.getGithubComments().equals(""))
				continue;

			String gitUser = event.getGithubName().toLowerCase();

			if (!gitUser.equals(lastGithubUser)) {
				// New github user, need to get new repos
				lastGithubUser = gitUser;

				for (int j = 0; j < repoList.size(); j++) {
					Repository repo = repoList.get(j);
					String repoName = repo.getName().toLowerCase();

					if (repoName.endsWith("-" + gitUser)) {
						// Update all user comments in this repo list
						updateUserGithubComments(gitUser, startDate, eventList, repo);
					}
				}
			}
		}
	}

	public void updateMissingGithubComments() {
		// Import github comments from start date for new github user names
		ArrayList<StudentModel> newGithubList = sqlDbImports.getStudentsUsingFlag("NewGithub");

		if (newGithubList.size() == 0)
			return;

		// Create repo lists for each level
		repoListLevelIntro = getRepoListByLevel(-1);
		repoListLevel0 = getRepoListByLevel(0);
		repoListLevel1 = getRepoListByLevel(1);
		repoListLevel2 = getRepoListByLevel(2);
		repoListLevel3 = getRepoListByLevel(3);
		repoListLevel4 = getRepoListByLevel(4);
		repoListLevel5 = getRepoListByLevel(5);
		String earliestDate = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"))
				.minusMonths(NEW_GITUSER_UPDATE_WINDOW).toString("yyyy-MM-dd");

		for (int i = 0; i < newGithubList.size(); i++) {
			StudentModel student = newGithubList.get(i);
			if (student.getStartDate() != null) {
				// Catch up only as far back as 2 months ago
				String catchupStartDate = student.getStartDate().toString();
				if (catchupStartDate.compareTo(earliestDate) < 0)
					catchupStartDate = earliestDate;

				// Import missing github comments
				ArrayList<AttendanceEventModel> eventList = sqlDbImports.getEventsWithNoComments(catchupStartDate,
						student.getClientID(), true);
				importGithubCommentsByLevel(-1, catchupStartDate, repoListLevelIntro, eventList);
				importGithubCommentsByLevel(0, catchupStartDate, repoListLevel0, eventList);
				importGithubCommentsByLevel(1, catchupStartDate, repoListLevel1, eventList);
				importGithubCommentsByLevel(2, catchupStartDate, repoListLevel2, eventList);
				importGithubCommentsByLevel(3, catchupStartDate, repoListLevel3, eventList);
				importGithubCommentsByLevel(4, catchupStartDate, repoListLevel4, eventList);
				importGithubCommentsByLevel(5, catchupStartDate, repoListLevel5, eventList);
				importGithubComments(catchupStartDate, eventList);

				// Set student 'new github' flag back to false
				sqlDbImports.updateStudentFlags(student, "NewGithub", 0);
			}
		}
	}

	public void updateEmptyGithubComments(ArrayList<AttendanceEventModel> eventList) {
		String today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd");

		for (int i = 0; i < eventList.size(); i++) {
			// Update events with null github comments to avoid repeated searches
			AttendanceEventModel event = eventList.get(i);
			if (event.getGithubComments().equals("") && event.getServiceDateString().compareTo(today) < 0) {
				sqlDbImports.updateAttendance(event.getClientID(), event.getStudentNameModel(),
						event.getServiceDateString(), event.getEventName(), null, "");
			}
		}
	}

	private List<Repository> getRepoListByLevel(int level) {
		// League levels use Github classroom with user 'League-Level0-Student'
		// Intro to Java Workshop uses Github classroom with user 'League-Workshop'
		String ownerName;
		List<Repository> repoList;

		if (level == -1) {
			ownerName = "League-Workshop";
			repoList = getRepoList(-1, ownerName);
		} else {
			ownerName = "League-Level" + level + "-Student";
			repoList = getRepoList(level, ownerName);
		}

		if (repoList.size() == 0)
			return null;

		return repoList;
	}

	private List<Repository> getRepoList(int level, String ownerName) {
		try {
			switch (level) {
			case -1:
				if (repoListLevelIntro == null)
					repoListLevelIntro = repoService.getRepositories(ownerName);
				return repoListLevelIntro;
			case 0:
				if (repoListLevel0 == null)
					repoListLevel0 = repoService.getRepositories(ownerName);
				return repoListLevel0;
			case 1:
				if (repoListLevel1 == null)
					repoListLevel1 = repoService.getRepositories(ownerName);
				return repoListLevel1;
			case 2:
				if (repoListLevel2 == null)
					repoListLevel2 = repoService.getRepositories(ownerName);
				return repoListLevel2;
			case 3:
				if (repoListLevel3 == null)
					repoListLevel3 = repoService.getRepositories(ownerName);
				return repoListLevel3;
			case 4:
				if (repoListLevel4 == null)
					repoListLevel4 = repoService.getRepositories(ownerName);
				return repoListLevel4;
			case 5:
				if (repoListLevel5 == null)
					repoListLevel5 = repoService.getRepositories(ownerName);
				return repoListLevel5;
			}

		} catch (IOException e) {
			if (e.getMessage().startsWith("API rate limit exceeded"))
				MySqlDbLogging.insertLogData(LogDataModel.GITHUB_IMPORT_ABORTED, new StudentNameModel("", "", false), 0,
						": Github API rate limit exceeded ***");
			else
				MySqlDbLogging.insertLogData(LogDataModel.GITHUB_MODULE_REPO_ERROR, null, 0,
						" for " + ownerName + ": " + e.getMessage());
		}
		return null;
	}

	private void updateUserGithubComments(String githubUser, String startDate, List<AttendanceEventModel> eventList,
			Repository repo) {
		// Get all the commits for this repo within date range
		try {
			for (Collection<RepositoryCommit> commitPage : commitService.pageCommits(repo, 20)) {
				// Loop through each commit for this page
				for (RepositoryCommit commit : commitPage) {
					// Get commit date
					long commitDateLong = commit.getCommit().getCommitter().getDate().getTime();
					String commitDate = new DateTime(commitDateLong).withZone(DateTimeZone.forID("America/Los_Angeles"))
							.toString("yyyy-MM-dd");

					// Commits ordered by date, so once date is old then move on
					if (commitDate.compareTo(startDate) < 0)
						return;

					// Find gituser & date match in event list; append multiple comments
					for (int k = 0; k < eventList.size(); k++) {
						AttendanceEventModel event = eventList.get(k);
						if (commitDate.equals(event.getServiceDateString())
								&& githubUser.equals(event.getGithubName().toLowerCase())) {
							// Trim github message to get only summary data
							String message = trimMessage(commit.getCommit().getMessage());

							// Update comments & repo name, continue to next commit
							if (!message.equals("")) {
								event.setGithubComments(message);
								sqlDbImports.updateAttendance(event.getClientID(), event.getStudentNameModel(),
										commitDate, event.getEventName(), repo.getName(), event.getGithubComments());
								System.out.println(
										"  " + event.getGithubName() + " '" + repo.getName() + "' on " + commitDate
												+ " for " + event.getEventName() + ": " + event.getGithubComments());
							}
						}
					}
				}
			}

		} catch (NoSuchPageException e) {
			// Repo is empty, so just return
		}
	}

	private String trimMessage(String inputMsg) {
		// Trim message up to first new-line character
		inputMsg = inputMsg.trim();
		int idx = inputMsg.indexOf("\n");
		if (idx > -1)
			return inputMsg.substring(0, idx);
		else
			return inputMsg;
	}
}
