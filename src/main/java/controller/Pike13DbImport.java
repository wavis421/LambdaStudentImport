package controller;

import java.net.HttpURLConnection;
import java.util.ArrayList;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import model.AttendanceEventModel;
import model.CoursesModel;
import model.MySqlDatabase;
import model.MySqlDbImports;
import model.ScheduleModel;
import model.StudentImportModel;
import model.StudentModel;

public class Pike13DbImport {
	// Custom field names for client data
	private final String GENDER_FIELD = "custom_field_106320";
	private final String GITHUB_FIELD = "custom_field_127885";
	private final String GRAD_YEAR_FIELD = "custom_field_145902";
	private final String EMERG_CONTACT_PHONE_FIELD = "custom_field_106322";
	private final String EMERG_CONTACT_EMAIL_FIELD = "custom_field_149434";
	private final String HOME_PHONE_FIELD = "custom_field_106498";
	private final String CURRENT_LEVEL_FIELD = "custom_field_157737";
	private final String LAST_EXAM_SCORE_FIELD = "custom_field_158633";

	// Custom field names for Staff Member data
	private final String STAFF_SF_CLIENT_ID_FIELD = "custom_field_152501";
	private final String STAFF_CATEGORY_FIELD = "custom_field_106325";

	// Indices for client data
	private final int CLIENT_ID_IDX = 0;
	private final int FIRST_NAME_IDX = 1;
	private final int LAST_NAME_IDX = 2;
	private final int GITHUB_IDX = 3;
	private final int GRAD_YEAR_IDX = 4;
	private final int GENDER_IDX = 5;
	private final int HOME_LOC_IDX = 6;
	private final int FIRST_VISIT_IDX = 7;
	private final int FUTURE_VISITS_IDX = 8;
	private final int EMAIL_IDX = 10;
	private final int ACCT_MGR_EMAIL_IDX = 11;
	private final int EMERG_EMAIL_IDX = 12;
	private final int MOBILE_PHONE_IDX = 13;
	private final int ACCT_MGR_PHONE_IDX = 14;
	private final int HOME_PHONE_IDX = 15;
	private final int EMERG_PHONE_IDX = 16;
	private final int BIRTHDATE_IDX = 17;
	private final int CURRENT_LEVEL_IDX = 18;
	private final int LAST_EXAM_SCORE_IDX = 19;

	// Indices for enrollment data
	private final int ENROLL_CLIENT_ID_IDX = 0;
	private final int ENROLL_FULL_NAME_IDX = 1;
	private final int ENROLL_SERVICE_DATE_IDX = 2;
	private final int ENROLL_EVENT_NAME_IDX = 3;
	private final int ENROLL_VISIT_ID_IDX = 4;
	private final int ENROLL_TEACHER_NAMES_IDX = 5;
	private final int ENROLL_SERVICE_CATEGORY_IDX = 6;
	private final int ENROLL_STATE_IDX = 7;
	private final int ENROLL_SERVICE_TIME_IDX = 8;

	// Indices for schedule data
	private final int SCHED_SERVICE_DAY_IDX = 0;
	private final int SCHED_SERVICE_TIME_IDX = 1;
	private final int SCHED_DURATION_MINS_IDX = 2;
	private final int SCHED_WKLY_EVENT_NAME_IDX = 3;
	private final int SCHED_ID_IDX = 4;

	// Indices for courses data
	private final int COURSES_SCHEDULE_ID_IDX = 0;
	private final int COURSES_EVENT_NAME_IDX = 1;
	private final int COURSE_ENROLLMENT_IDX = 2;

	// Indices for Student TA data
	private final static int STUDENT_TA_CLIENT_ID_IDX = 0;
	private final static int STUDENT_TA_STAFF_SINCE_DATE_IDX = 1;
	private final static int STUDENT_TA_NUM_PAST_EVENTS_IDX = 2;

	private final String getClientData = "{\"data\":{\"type\":\"queries\","
			// Get attributes: fields, page limit and filters
			+ "\"attributes\":{"
			// Select fields
			+ "\"fields\":[\"person_id\",\"first_name\",\"last_name\",\"" + GITHUB_FIELD + "\",\"" + GRAD_YEAR_FIELD + "\","
			+ "            \"" + GENDER_FIELD + "\",\"home_location_name\",\"first_visit_date\","
			+ "            \"future_visits\",\"completed_visits\",\"email\",\"account_manager_emails\","
			+ "            \"" + EMERG_CONTACT_EMAIL_FIELD + "\",\"phone\",\"account_manager_phones\","
			+ "            \"" + HOME_PHONE_FIELD + "\",\"" + EMERG_CONTACT_PHONE_FIELD + "\",\"birthdate\","
			+ "            \"" + CURRENT_LEVEL_FIELD + "\",\"" + LAST_EXAM_SCORE_FIELD + "\"],"
			// Page limit max is 500
			+ "\"page\":{\"limit\":500";
	
	private final String getClientData2 = "},"
			// Filter on Dependents NULL and either has future visits or recent completed visits
			+ "\"filter\":[\"and\",[[\"eq\",\"person_state\",\"active\"],"
			+ "                     [\"emp\",\"dependent_names\"],"
			+ "                     [\"or\",[[\"gt\",\"future_visits\",0],"
			+ "                              [\"gt\",\"last_visit_date\",\"0000-00-00\"]]]]]}}}";

	// Getting enrollment data is in 2 parts since page info gets inserted in middle.
	private final String getEnrollmentStudentTracker = "{\"data\":{\"type\":\"queries\","
			// Get attributes: fields, page limit
			+ "\"attributes\":{"
			// Select fields
			+ "\"fields\":[\"person_id\",\"full_name\",\"service_date\",\"event_name\",\"visit_id\",\"instructor_names\","
			+ "            \"service_category\",\"state\",\"service_time\"],"
			// Page limit max is 500
			+ "\"page\":{\"limit\":500";

	private final String getEnrollmentStudentTracker2 = "},"
			// Filter on State completed and since date OR make-up class for this week
			+ "\"filter\":[\"or\",[[\"and\",[[\"eq\",\"state\",\"completed\"],"
			+ "                              [\"btw\",\"service_date\",[\"0000-00-00\",\"1111-11-11\"]]]],"
			+ "                    [\"and\",[[\"eq\",\"state\",\"registered\"],"
			+ "                              [\"btw\",\"service_date\",[\"2222-22-22\",\"3333-33-33\"]],"
			+ "                              [\"starts\",\"service_category\",\"class\"]]]]]}}}";

	private final String getEnrollmentStudentTracker2WithName = "},"
			// Filter on State completed, since date and student name
			+ "\"filter\":[\"and\",[[\"eq\",\"state\",\"completed\"],"
			+ "                     [\"btw\",\"service_date\",[\"0000-00-00\",\"1111-11-11\"]],"
			+ "                     [\"starts\",\"service_category\",\"Class\"],"
			+ "                     [\"eq\",\"full_name\",\"NNNNNN\"]]]}}}";

	private final String getCourseEnrollmentStudentTracker2 = "},"
			// Filter on course, state completed or enrolled and since date
			+ "\"filter\":[\"and\",[[\"or\",[[\"eq\",\"state\",\"completed\"],"
			+ "                              [\"eq\",\"state\",\"registered\"]]],"
			+ "                     [\"btw\",\"service_date\",[\"0000-00-00\",\"1111-11-11\"]],"
			+ "                     [\"starts\",\"service_type\",\"course\"],"
			+ "                     [\"nemp\",\"event_name\"]]]}}}";

	// Get schedule data
	private final String getScheduleData = "{\"data\":{\"type\":\"queries\","
			// Get attributes: fields, page limit and filters
			+ "\"attributes\":{"
			// Select fields
			+ "\"fields\":[\"service_day\",\"service_time\",\"duration_in_minutes\",\"event_name\",\"event_occurrence_id\"],"
			// Page limit max is 500
			+ "\"page\":{\"limit\":500},"
			// Filter on 'this week' and 'starts with Class' and event name not null
			+ "\"filter\":[\"and\",[[\"btw\",\"service_date\",[\"0000-00-00\",\"1111-11-11\"]],"
			+ "                     [\"starts\",\"service_category\",\"Class\"],"
			+ "                     [\"ne\",\"service_category\",\"class jslam\"],"
			+ "                     [\"nemp\",\"event_name\"]]]}}}";

	// Get workshop and summer slam data
	private final String getCoursesData = "{\"data\":{\"type\":\"queries\","
			// Get attributes: fields, page limit and filters
			+ "\"attributes\":{"
			// Select fields
			+ "\"fields\":[\"event_id\",\"event_name\",\"enrollment_count\"],"
			// Page limit max is 500
			+ "\"page\":{\"limit\":500},"
			// Filter on 'this week' and 'starts with Class' and event name not null
			+ "\"filter\":[\"and\",[[\"btw\",\"service_date\",[\"0000-00-00\",\"1111-11-11\"]],"
			+ "                     [\"starts\",\"service_type\",\"course\"]]]}}}";

	// Get Student TA data
	private final String getStudentTAData = "{\"data\":{\"type\":\"queries\","
			// Get attributes: fields, page limit and filters
			+ "\"attributes\":{"
			// Select fields
			+ "\"fields\":[\"" + STAFF_SF_CLIENT_ID_FIELD + "\",\"staff_since_date\",\"past_events\"],"
			// Page limit max is 500
			+ "\"page\":{\"limit\":500},"
			// Filter on Staff Category and staff member active
			+ "\"filter\":[\"and\",[[\"eq\",\"person_state\",\"active\"],"
			+ "                     [\"eq\",\"" + STAFF_CATEGORY_FIELD + "\",\"Student TA\"],"
			+ "                     [\"starts\",\"full_name\",\"TA-\"]]]}}}";

	private MySqlDbImports mySqlDbImports;
	private Pike13Connect pike13Conn;

	public Pike13DbImport(MySqlDbImports mySqlDbImports, Pike13Connect pike13Conn) {
		this.mySqlDbImports = mySqlDbImports;
		this.pike13Conn = pike13Conn;
	}

	public ArrayList<StudentImportModel> getClients() {
		ArrayList<StudentImportModel> studentList = new ArrayList<StudentImportModel>();
		boolean hasMore = false;
		String lastKey = "";

		// Insert since date for completed visit (in last 30 days)
		String clients2 = getClientData2.replaceFirst("0000-00-00",
				new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).
					minusDays(MySqlDatabase.CLASS_ATTEND_NUM_DAYS_TO_KEEP).toString("yyyy-MM-dd"));

		do {
			// Get URL connection with authorization
			HttpURLConnection conn;

			// Send the query
			if (hasMore)
				conn = pike13Conn.sendQueryToUrl("clients", getClientData + ",\"starting_after\":\"" + lastKey + "\"" + clients2, false);
			else
				conn = pike13Conn.sendQueryToUrl("clients", getClientData + clients2, false);

			if (conn == null)
				return studentList;

			// Get input stream and read data
			JsonObject jsonObj = pike13Conn.readInputStream(conn);
			if (jsonObj == null) {
				conn.disconnect();
				return studentList;
			}
			JsonArray jsonArray = jsonObj.getJsonArray("rows");

			for (int i = 0; i < jsonArray.size(); i++) {
				// Get fields for each person
				JsonArray personArray = (JsonArray) jsonArray.get(i);
				String firstName = pike13Conn.stripQuotes(personArray.get(FIRST_NAME_IDX).toString());
				
				String birthday = null;
				if (personArray.get(BIRTHDATE_IDX) != null)
					birthday = pike13Conn.stripQuotes(personArray.get(BIRTHDATE_IDX).toString());

				if (!firstName.startsWith("Guest") && !firstName.equals("Test") && !firstName.startsWith("TestChild")) {
					// Get fields for this Json array entry
					StudentImportModel model = new StudentImportModel(personArray.getInt(CLIENT_ID_IDX),
							pike13Conn.stripQuotes(personArray.get(LAST_NAME_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(FIRST_NAME_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(GITHUB_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(GENDER_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(FIRST_VISIT_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(HOME_LOC_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(GRAD_YEAR_IDX).toString().trim()),
							pike13Conn.stripQuotes(personArray.get(EMAIL_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(ACCT_MGR_EMAIL_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(EMERG_EMAIL_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(MOBILE_PHONE_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(ACCT_MGR_PHONE_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(HOME_PHONE_IDX).toString()),
							pike13Conn.stripQuotes(personArray.get(EMERG_PHONE_IDX).toString()),
							birthday, pike13Conn.stripQuotes(personArray.get(CURRENT_LEVEL_IDX).toString()),
							personArray.getInt(FUTURE_VISITS_IDX), 
							pike13Conn.stripQuotes(personArray.get(LAST_EXAM_SCORE_IDX).toString()));
					studentList.add(model);
				}
			}

			// Check to see if there are more pages
			hasMore = jsonObj.getBoolean("has_more");
			if (hasMore)
				lastKey = jsonObj.getString("last_key");

			conn.disconnect();

		} while (hasMore);

		return studentList;
	}

	public ArrayList<AttendanceEventModel> getAttendance(String startDate) {
		// Insert start date and end date into enrollment command string
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));
		String enroll2 = getEnrollmentStudentTracker2.replaceFirst("0000-00-00", startDate);
		enroll2 = enroll2.replaceFirst("1111-11-11", today.toString("yyyy-MM-dd"));
		enroll2 = enroll2.replaceFirst("2222-22-22", today.toString("yyyy-MM-dd"));
		enroll2 = enroll2.replaceFirst("3333-33-33", today.plusDays(6).toString("yyyy-MM-dd"));

		// Get attendance for all students
		return getEnrollmentByCmdString(getEnrollmentStudentTracker, enroll2);
	}

	private ArrayList<AttendanceEventModel> getEnrollmentByCmdString(String cmdString1, String cmdString2) {
		ArrayList<AttendanceEventModel> eventList = new ArrayList<AttendanceEventModel>();
		boolean hasMore = false;
		String lastKey = "";

		do {
			// Get URL connection with authorization and send the query; add page info if necessary
			HttpURLConnection conn;
			if (hasMore)
				conn = pike13Conn.sendQueryToUrl("enrollments", cmdString1 + ",\"starting_after\":\"" + lastKey + "\"" + cmdString2, false);
			else
				conn = pike13Conn.sendQueryToUrl("enrollments", cmdString1 + cmdString2, false);

			if (conn == null)
				return eventList;

			// Get input stream and read data
			JsonObject jsonObj = pike13Conn.readInputStream(conn);
			if (jsonObj == null) {
				conn.disconnect();
				return eventList;
			}
			JsonArray jsonArray = jsonObj.getJsonArray("rows");

			for (int i = 0; i < jsonArray.size(); i++) {
				// Get fields for each event
				JsonArray eventArray = (JsonArray) jsonArray.get(i);
				String eventName = pike13Conn.stripQuotes(eventArray.get(ENROLL_EVENT_NAME_IDX).toString());
				String serviceDate = pike13Conn.stripQuotes(eventArray.get(ENROLL_SERVICE_DATE_IDX).toString());

				// Add event to list
				if (!eventName.equals("") && !eventName.equals("\"\"") && !serviceDate.equals("")) {
					eventList.add(new AttendanceEventModel(eventArray.getInt(ENROLL_CLIENT_ID_IDX),
							eventArray.getInt(ENROLL_VISIT_ID_IDX),
							pike13Conn.stripQuotes(eventArray.get(ENROLL_FULL_NAME_IDX).toString()), serviceDate, 
							pike13Conn.stripQuotes(eventArray.get(ENROLL_SERVICE_TIME_IDX).toString()), eventName,
							pike13Conn.stripQuotes(eventArray.get(ENROLL_TEACHER_NAMES_IDX).toString()),
							pike13Conn.stripQuotes(eventArray.get(ENROLL_SERVICE_CATEGORY_IDX).toString()),
							pike13Conn.stripQuotes(eventArray.get(ENROLL_STATE_IDX).toString()), null));
				}
			}

			// Check to see if there are more pages
			hasMore = jsonObj.getBoolean("has_more");
			if (hasMore)
				lastKey = jsonObj.getString("last_key");

			conn.disconnect();

		} while (hasMore && cmdString2 != "");

		return eventList;
	}

	public ArrayList<AttendanceEventModel> getMissingAttendance(String endDate, ArrayList<StudentModel> studentList) {
		ArrayList<AttendanceEventModel> eventList = new ArrayList<AttendanceEventModel>();

		// Insert end date into enrollment command string
		String enroll2 = getEnrollmentStudentTracker2WithName.replaceFirst("1111-11-11", endDate);

		for (int i = 0; i < studentList.size(); i++) {
			StudentModel student = studentList.get(i);
			if (student.getStartDate() != null) {
				// Get student start date and ignore if date is beyond end date
				String catchupStartDate = student.getStartDate().toString();
				if (catchupStartDate.compareTo(endDate) >= 0)
					continue;

				// Catch up only as far back as 3 months ago
				String earliestDate = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).minusMonths(3)
						.toString("yyyy-MM-dd");
				if (catchupStartDate.compareTo(earliestDate) < 0)
					catchupStartDate = earliestDate;

				// Get attendance for this student
				String enrollTemp = enroll2.replaceFirst("0000-00-00", catchupStartDate);
				enrollTemp = enrollTemp.replaceFirst("NNNNNN", student.getFirstName() + " " + student.getLastName());
				eventList.addAll(getEnrollmentByCmdString(getEnrollmentStudentTracker, enrollTemp));

				// Set student 'NewStudent' flag back to false
				mySqlDbImports.updateStudentFlags(student, "NewStudent", 0);
			}
		}

		return eventList;
	}

	public ArrayList<ScheduleModel> getSchedule(String startDate) {
		ArrayList<ScheduleModel> scheduleList = new ArrayList<ScheduleModel>();

		// Insert start date and end date into schedule command string.
		String scheduleString = getScheduleData.replaceFirst("0000-00-00", startDate);
		scheduleString = scheduleString.replaceFirst("1111-11-11",
				new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).plusDays(6).toString("yyyy-MM-dd"));

		// Get URL connection with authorization and send query
		HttpURLConnection conn = pike13Conn.sendQueryToUrl("event_occurrences", scheduleString, false);
		if (conn == null)
			return scheduleList;

		// Get input stream and read data
		JsonObject jsonObj = pike13Conn.readInputStream(conn);
		if (jsonObj == null) {
			conn.disconnect();
			return scheduleList;
		}
		JsonArray jsonArray = jsonObj.getJsonArray("rows");

		for (int i = 0; i < jsonArray.size(); i++) {
			// Get fields for each event in the schedule
			JsonArray scheduleArray = (JsonArray) jsonArray.get(i);

			// Get event name, day-of-week and duration
			String eventName = pike13Conn.stripQuotes(scheduleArray.get(SCHED_WKLY_EVENT_NAME_IDX).toString());
			String serviceDayString = pike13Conn.stripQuotes(scheduleArray.get(SCHED_SERVICE_DAY_IDX).toString());
			int serviceDay = Integer.parseInt(serviceDayString);
			String startTime = pike13Conn.stripQuotes(scheduleArray.get(SCHED_SERVICE_TIME_IDX).toString());
			int duration = scheduleArray.getInt(SCHED_DURATION_MINS_IDX);

			// Add event to list
			scheduleList.add(new ScheduleModel(scheduleArray.getInt(SCHED_ID_IDX), serviceDay, startTime, duration, eventName));
		}

		conn.disconnect();
		return scheduleList;
	}

	public String getRoomField(int scheduleId) {
		// Get URL connection with authorization and send query
		HttpURLConnection conn = pike13Conn.sendQueryToUrl("event_occurrences?ids=" + scheduleId, "", true);
		if (conn == null)
			return "";

		// Get input stream and read data
		JsonObject jsonObj = pike13Conn.readCoreInputStream(conn);
		if (jsonObj == null) {
			conn.disconnect();
			return "";
		}

		// Get all scheduled events using Pike13 CORE API
		JsonArray jsonArray = jsonObj.getJsonArray("event_occurrences");
		if (jsonArray != null && jsonArray.size() > 0) {
			// Get fields for this event in the schedule
			JsonObject event = jsonArray.getJsonObject(0);
			JsonArray resources = event.getJsonArray("resources");
			String eventName = event.get("name").toString();

			/* Get fields for each scheduled event */
			if (resources.size() > 0 && eventName.contains("Java@CV")) {
				// Process resources field by extracting the ROOM field
				String roomName = "";
				for (int i = 0; i < resources.size(); i++) {
					JsonObject res = resources.getJsonObject(i);
					if (!roomName.equals(""))
						roomName += ", ";
					roomName += res.getString("name");
				}
				return roomName;
			}
		}

		conn.disconnect();
		return "";
	}

	public ArrayList<CoursesModel> getCourses(String startDate, String endDate) {
		ArrayList<CoursesModel> coursesList = new ArrayList<CoursesModel>();

		// Insert start date and end date into schedule command string.
		String coursesString = getCoursesData.replaceFirst("0000-00-00", startDate);
		coursesString = coursesString.replaceFirst("1111-11-11", endDate);

		// Get URL connection with authorization and send query
		HttpURLConnection conn = pike13Conn.sendQueryToUrl("event_occurrences", coursesString, false);
		if (conn == null)
			return coursesList;

		// Get input stream and read data
		JsonObject jsonObj = pike13Conn.readInputStream(conn);
		if (jsonObj == null) {
			conn.disconnect();
			return coursesList;
		}
		JsonArray jsonArray = jsonObj.getJsonArray("rows");

		for (int i = 0; i < jsonArray.size(); i++) {
			// Get fields for each event in the schedule
			JsonArray coursesArray = (JsonArray) jsonArray.get(i);

			// Add event to list
			coursesList.add(new CoursesModel(coursesArray.getInt(COURSES_SCHEDULE_ID_IDX),
					pike13Conn.stripQuotes(coursesArray.get(COURSES_EVENT_NAME_IDX).toString()),
					coursesArray.getInt(COURSE_ENROLLMENT_IDX)));
		}

		conn.disconnect();
		return coursesList;
	}

	public ArrayList<AttendanceEventModel> getCourseAttendance(String startDate, String endDate) {
		// Insert start date and end date into enrollment command string
		String enroll2 = getCourseEnrollmentStudentTracker2.replaceFirst("0000-00-00", startDate);
		enroll2 = enroll2.replaceFirst("1111-11-11", endDate);

		// Get attendance for all students
		return getEnrollmentByCmdString(getEnrollmentStudentTracker, enroll2);
	}

	public void updateStudentTAData(ArrayList<StudentImportModel> students) {
		// Get URL connection and send the query
		HttpURLConnection conn = pike13Conn.sendQueryToUrl("staff_members", getStudentTAData, false);
		if (conn == null)
			return;

		// Get input stream and read data
		JsonObject jsonObj = pike13Conn.readInputStream(conn);
		if (jsonObj == null) {
			conn.disconnect();
			return;
		}
		JsonArray jsonArray = jsonObj.getJsonArray("rows");
		int taCount = 0;

		for (int i = 0; i < jsonArray.size(); i++) {
			// Get fields for each TA. Ignore all TA's without Client ID or not in student list
			JsonArray staffArray = (JsonArray) jsonArray.get(i);
			if (staffArray.get(STUDENT_TA_CLIENT_ID_IDX) == null)
				continue;
			
			String clientID = pike13Conn.stripQuotes(staffArray.get(STUDENT_TA_CLIENT_ID_IDX).toString());
			StudentImportModel ta = findClientID(clientID, students);
			if (ta == null)
				continue;
			
			ta.setStaffData(pike13Conn.stripQuotes(staffArray.get(STUDENT_TA_STAFF_SINCE_DATE_IDX).toString()), 
					staffArray.getInt(STUDENT_TA_NUM_PAST_EVENTS_IDX));
			taCount++;
		}

		conn.disconnect();
		System.out.println("Num Student TA's: " + jsonArray.size() + " (" + taCount + ")");
	}

	private StudentImportModel findClientID(String clientID, ArrayList<StudentImportModel> students) {
		if (!clientID.matches("\\d+"))
			// Old client ID's were not numbers!
			return null;
		
		int s_id = Integer.parseInt(clientID);
		
		for (StudentImportModel s : students) {
			if (s_id == s.getClientID()) {
				return s;
			}
		}
		return null;
	}
}

