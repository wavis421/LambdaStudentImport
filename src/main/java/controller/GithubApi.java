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
import model.StudentNameModel;

public class GithubApi {
	private MySqlDbImports sqlDbImports;
	private RepositoryService repoService;
	private CommitService commitService;

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

	public void updateEmptyGithubComments(ArrayList<AttendanceEventModel> eventList) {
		String today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd");

		for (int i = 0; i < eventList.size(); i++) {
			// Update events with null github comments to avoid repeated searches
			AttendanceEventModel event = eventList.get(i);
			if (event.getGithubComments().equals("") && event.getServiceDateString().compareTo(today) < 0) {
				sqlDbImports.updateAttendance(event.getClientID(), event.getStudentNameModel(),
						event.getServiceDateString(), event.getEventName(), null, "", "");
			}
		}
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
							// Update comments & repo name, continue to next commit
							String message = commit.getCommit().getMessage();
							if (!message.equals("")) {
								event.setGithubComments(message);
								sqlDbImports.updateAttendance(event.getClientID(), event.getStudentNameModel(),
										commitDate, event.getEventName(), repo.getName(), event.getGithubComments(),
										event.getGitDescription());
							}
						}
					}
				}
			}

		} catch (NoSuchPageException e) {
			// Repo is empty, so just return
		}
	}
}
