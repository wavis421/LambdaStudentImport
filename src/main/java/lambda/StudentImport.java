package lambda;

import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import controller.GithubApi;
import controller.Pike13Api;
import model.AttendanceEventModel;
import model.LogDataModel;
import model.MySqlDatabase;
import model.ScheduleModel;
import model.StudentImportModel;
import model.StudentModel;
import model.StudentNameModel;

public class StudentImport {

	private MySqlDatabase sqlDb;

	public String myHandler(Object input, Context context) {
		// Import data starting 7 days ago
		String startDateString = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).minusDays(7)
				.toString().substring(0, 10);

		// Today (start time)
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));

		LambdaLogger logger = context.getLogger();
		logger.log("Input: " + input + " for " + today.toString("yyyy-MM-dd HH:mm:ss") + "\n");

		// Connect to database
		sqlDb = new MySqlDatabase(System.getenv("PASSWORD"), MySqlDatabase.STUDENT_IMPORT_NO_SSH);
		if (sqlDb.connectDatabase()) {
			// Connect to Pike13 and import data
			Pike13Api pike13Api = new Pike13Api(sqlDb, System.getenv("PIKE13_KEY"));
			importStudentsFromPike13(pike13Api);
			importAttendanceFromPike13(startDateString, pike13Api);
			importScheduleFromPike13(pike13Api);

			// Connect to github and import data
			GithubApi githubApi = new GithubApi(sqlDb, System.getenv("GITHUB_KEY"));
			importGithubComments(startDateString, githubApi);

			sqlDb.disconnectDatabase();
		}

		return ("Start: " + today.toString("yyyy-MM-dd HH:mm:ss") + ", End: "
				+ (new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd HH:mm:ss")));
	}

	public void importStudentsFromPike13(Pike13Api pike13Api) {
		String today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd")
				.substring(0, 10);
		sqlDb.insertLogData(LogDataModel.STARTING_STUDENT_IMPORT, new StudentNameModel("", "", false), 0,
				" for " + today + " ***");

		// Get data from Pike13
		ArrayList<StudentImportModel> studentList = pike13Api.getClients();

		// Update changes in database
		if (studentList.size() > 0)
			sqlDb.importStudents(studentList);

		sqlDb.insertLogData(LogDataModel.STUDENT_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0,
				" for " + today + " ***");
	}

	public void importAttendanceFromPike13(String startDate, Pike13Api pike13Api) {
		sqlDb.insertLogData(LogDataModel.STARTING_ATTENDANCE_IMPORT, new StudentNameModel("", "", false), 0,
				" starting from " + startDate + " ***");

		// Get attendance data from Pike13 for all students
		ArrayList<AttendanceEventModel> eventList = pike13Api.getAttendance(startDate);

		// Update changes in database
		if (eventList.size() > 0)
			sqlDb.importAttendance(eventList);

		// Get 'missing' attendance for new and returned students
		ArrayList<StudentModel> newStudents = sqlDb.getStudentsUsingFlag("NewStudent");
		if (newStudents.size() > 0) {
			eventList = pike13Api.getMissingAttendance(startDate, newStudents);
			if (eventList.size() > 0)
				sqlDb.importAttendance(eventList);
		}

		sqlDb.insertLogData(LogDataModel.ATTENDANCE_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0,
				" starting from " + startDate + " ***");
	}

	public void importScheduleFromPike13(Pike13Api pike13Api) {
		String startDate = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).minusDays(14)
				.toString("yyyy-MM-dd");
		sqlDb.insertLogData(LogDataModel.STARTING_SCHEDULE_IMPORT, new StudentNameModel("", "", false), 0,
				" as of " + startDate.substring(0, 10) + " ***");

		// Get data from Pike13
		ArrayList<ScheduleModel> scheduleList = pike13Api.getSchedule(startDate);

		// Update changes in database
		if (scheduleList.size() > 0)
			sqlDb.importSchedule(scheduleList);

		sqlDb.insertLogData(LogDataModel.SCHEDULE_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0,
				" as of " + startDate.substring(0, 10) + " ***");
	}

	public void importGithubComments(String startDate, GithubApi githubApi) {
		boolean result;
		sqlDb.insertLogData(LogDataModel.STARTING_GITHUB_IMPORT, new StudentNameModel("", "", false), 0,
				" starting from " + startDate + " ***");

		// Get list of events with missing comments
		ArrayList<AttendanceEventModel> eventList = sqlDb.getEventsWithNoComments(startDate, 0, false);
		if (eventList.size() > 0) {
			// Import Github comments
			result = githubApi.importGithubComments(startDate, eventList);

			if (result) {
				// Import github comments for level 0 & 1
				githubApi.importGithubCommentsByLevel(0, startDate, null, eventList);
				githubApi.importGithubCommentsByLevel(1, startDate, null, eventList);

				// Updated github comments for users with new user name
				githubApi.updateMissingGithubComments();

				// Update any remaining null comments to show event was processed
				githubApi.updateEmptyGithubComments(eventList);

			} else {
				sqlDb.insertLogData(LogDataModel.GITHUB_IMPORT_ABORTED, new StudentNameModel("", "", false), 0,
						": Github API rate limit exceeded ***");
				return;
			}
		}

		sqlDb.insertLogData(LogDataModel.GITHUB_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0,
				" starting from " + startDate + " ***");
	}
}
