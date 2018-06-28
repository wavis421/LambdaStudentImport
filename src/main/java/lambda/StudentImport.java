package lambda;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import controller.GithubApi;
import controller.Pike13Api;
import controller.StudentImportEngine;
import model.LocationLookup;
import model.MySqlDatabase;

public class StudentImport {

	private static final int ATTEND_NUM_DAYS_IN_PAST = 21;
	private static final int ATTEND_NUM_DAYS_IN_FUTURE = 120;
	private MySqlDatabase sqlDb;

	public String myHandler(Object input, Context context) {
		// Import data starting 7 days ago
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));
		String startDateString = today.minusDays(ATTEND_NUM_DAYS_IN_PAST).toString().substring(0, 10);
		String courseEndDate = today.plusDays(ATTEND_NUM_DAYS_IN_FUTURE).toString().substring(0, 10);

		LambdaLogger logger = context.getLogger();
		logger.log(today.toString("yyyy-MM-dd HH:mm:ss") + " " + input + "\n");

		// Connect to database
		sqlDb = new MySqlDatabase(System.getenv("PASSWORD"), MySqlDatabase.STUDENT_IMPORT_NO_SSH);
		if (sqlDb.connectDatabase()) {
			StudentImportEngine importer = new StudentImportEngine(sqlDb);
			LocationLookup.setLocationData(sqlDb.getLocationList());

			// Connect to Pike13 and import data
			Pike13Api pike13Api = new Pike13Api(sqlDb, System.getenv("PIKE13_KEY"));
			importer.importStudentsFromPike13(pike13Api);
			importer.importAttendanceFromPike13(startDateString, pike13Api);
			importer.importScheduleFromPike13(pike13Api);
			importer.importCoursesFromPike13(pike13Api);
			importer.importCourseAttendanceFromPike13(startDateString, courseEndDate, pike13Api);

			// Connect to github and import data
			GithubApi githubApi = new GithubApi(sqlDb, System.getenv("GITHUB_KEY"));
			importer.importGithubComments(startDateString, githubApi);

			sqlDb.disconnectDatabase();
		}

		return ("Start: " + today.toString("yyyy-MM-dd HH:mm:ss") + ", End: "
				+ (new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd HH:mm:ss")));
	}
}
