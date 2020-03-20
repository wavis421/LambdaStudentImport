package lambda;

import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import controller.GithubApi;
import controller.Pike13Connect;
import controller.Pike13DbImport;
import controller.StudentImportEngine;
import model.LocationLookup;
import model.LogDataModel;
import model.MySqlDatabase;
import model.MySqlDbImports;
import model.MySqlDbLogging;
import model.StudentModel;
import model.StudentNameModel;

/**
 * The Student Data Import class is the controller for the nightly import of
 * data from Pike13 to The League AWS Tracker Database. Imported data includes
 * Student data, Attendance data and class/course Schedule data.
 * 
 * @author wavis
 *
 */

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
			new MySqlDbLogging(sqlDb);
			//MySqlDbLogging.insertLogData(LogDataModel.STARTING_TRACKER_IMPORT, new StudentNameModel("", "", false), 0,
			//		" for " + today.toString("yyyy-MM-dd") + " ***");

			MySqlDbImports sqlImportDb = new MySqlDbImports(sqlDb);
			StudentImportEngine importer = new StudentImportEngine(sqlImportDb);
			LocationLookup.setLocationData(sqlDb.getLocationList());

			// Remove log data older than 7 days
			importer.removeOldLogData(7);

			// Connect to Pike13 and import data
			Pike13Connect pike13Conn = new Pike13Connect(System.getenv("PIKE13_KEY"));
			Pike13DbImport pike13Api = new Pike13DbImport(sqlImportDb, pike13Conn);
			importer.importStudentsFromPike13(pike13Api);
			
			// Now update active student list and continue imports
			ArrayList<StudentModel> studentList = sqlImportDb.getActiveStudents();
			importer.importAttendanceFromPike13(startDateString, pike13Api, studentList);
			importer.importScheduleFromPike13(pike13Api, studentList);
			importer.importCoursesFromPike13(pike13Api);
			importer.importCourseAttendanceFromPike13(startDateString, courseEndDate, pike13Api, studentList);

			// Connect to Github and import data
			GithubApi githubApi = new GithubApi(sqlImportDb, System.getenv("GITHUB_KEY"));
			importer.importGithubComments(startDateString, pike13Api, githubApi, studentList);

			MySqlDbLogging.insertLogData(LogDataModel.TRACKER_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0,
					" for " + today.toString("yyyy-MM-dd") + " ***");
			sqlDb.disconnectDatabase();
		}

		return ("Start: " + today.toString("yyyy-MM-dd HH:mm:ss") + ", End: "
				+ (new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd HH:mm:ss")));
	}
}
