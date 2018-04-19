package lambda;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import controller.GithubApi;
import controller.StudentImportEngine;
import controller.Pike13Api;
import model.MySqlDatabase;

public class StudentImport {

	private MySqlDatabase sqlDb;

	public String myHandler(Object input, Context context) {
		// Import data starting 7 days ago
		String startDateString = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).minusDays(7)
				.toString().substring(0, 10);

		// Today (start time)
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));

		LambdaLogger logger = context.getLogger();
		logger.log(today.toString("yyyy-MM-dd HH:mm:ss") + " " + input + "\n");

		// Connect to database
		sqlDb = new MySqlDatabase(System.getenv("PASSWORD"), MySqlDatabase.STUDENT_IMPORT_NO_SSH);
		if (sqlDb.connectDatabase()) {
			StudentImportEngine importer = new StudentImportEngine(sqlDb);

			// Connect to Pike13 and import data
			Pike13Api pike13Api = new Pike13Api(sqlDb, System.getenv("PIKE13_KEY"));
			importer.importStudentsFromPike13(pike13Api);
			importer.importAttendanceFromPike13(startDateString, pike13Api);
			importer.importScheduleFromPike13(pike13Api);

			// Connect to github and import data
			GithubApi githubApi = new GithubApi(sqlDb, System.getenv("GITHUB_KEY"));
			importer.importGithubComments(startDateString, githubApi);

			sqlDb.disconnectDatabase();
		}

		return ("Start: " + today.toString("yyyy-MM-dd HH:mm:ss") + ", End: "
				+ (new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd HH:mm:ss")));
	}
}
