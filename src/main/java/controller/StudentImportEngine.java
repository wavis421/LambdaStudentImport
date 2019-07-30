package controller;

import java.util.ArrayList;
import java.util.Collections;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import model.AttendanceEventModel;
import model.CoursesModel;
import model.MySqlDbImports;
import model.MySqlDbLogging;
import model.PendingGithubModel;
import model.ScheduleModel;
import model.StudentImportModel;
import model.StudentModel;

public class StudentImportEngine {
	static final int SCHEDULE_DAYS_IN_PAST = 14;
	static final int COURSE_DAYS_IN_PAST = 14;
	static final int COURSE_DAYS_IN_FUTURE = 120;

	MySqlDbImports sqlImportDb;

	public StudentImportEngine(MySqlDbImports sqlImportDb) {
		this.sqlImportDb = sqlImportDb;
	}

	public void removeOldLogData(int numDays) {
		MySqlDbLogging.removeOldLogData(numDays);
	}

	public void importStudentsFromPike13(Pike13DbImport pike13Api) {
		// Get data from Pike13, then update student TA data from Staff DB
		ArrayList<StudentImportModel> studentList = pike13Api.getClients();
		pike13Api.updateStudentTAData(studentList);
		sqlImportDb.updateMissingCurrentClass();
		sqlImportDb.updateRegisteredClass();
		Collections.sort(studentList);

		// Update changes in database
		if (studentList.size() > 0) {
			sqlImportDb.importStudents(studentList);
			System.out.println(studentList.size() + " students imported from Pike13");
		}
	}

	public void importAttendanceFromPike13(String startDate, Pike13DbImport pike13Api) {
		// Get attendance data from Pike13 for all students
		ArrayList<AttendanceEventModel> eventList = pike13Api.getAttendance(startDate);

		// Update changes in database
		if (eventList.size() > 0) {
			// Import attendance and then re-sort attendance list
			sqlImportDb.importAttendance(startDate, eventList, true);
			sqlImportDb.createSortedAttendanceList();
			System.out.println(eventList.size() + " attendance records imported from Pike13");
		}

		// Get 'missing' attendance for new and returned students
		ArrayList<StudentModel> newStudents = sqlImportDb.getStudentsUsingFlag("NewStudent");
		if (newStudents.size() > 0) {
			eventList = pike13Api.getMissingAttendance(startDate, newStudents);
			if (eventList.size() > 0) {
				sqlImportDb.importAttendance(startDate, eventList, false);
				System.out.println(eventList.size() + " new student attendance records imported from Pike13");
			}
		}

		// Delete 'registered' attendance that has expired
		eventList = sqlImportDb.getExpiredEvents(startDate);
		if (eventList.size() > 0) {
			sqlImportDb.deleteExpiredAttendance(eventList);
			System.out.println(eventList.size() + " expired attendance records removed");
		}
	}

	public void importCourseAttendanceFromPike13(String startDate, String endDate, Pike13DbImport pike13Api) {
		// Get course attendance data from Pike13 for all students
		ArrayList<AttendanceEventModel> eventList = pike13Api.getCourseAttendance(startDate, endDate);

		// Update changes in database
		if (eventList.size() > 0) {
			sqlImportDb.importAttendance(startDate, eventList, false);
			System.out.println(eventList.size() + " course attendance records imported from Pike13, " + startDate
					+ " to " + endDate);
		}
	}

	public void importScheduleFromPike13(Pike13DbImport pike13Api) {
		String startDate = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"))
				.minusDays(SCHEDULE_DAYS_IN_PAST).toString("yyyy-MM-dd");

		// Get data from Pike13
		ArrayList<ScheduleModel> scheduleList = pike13Api.getSchedule(startDate);
		ArrayList<ScheduleModel> filteredList = new ArrayList<ScheduleModel>();

		// Pike13 will have duplicate Schedule ID's, so filter them out.
		// Grab the latest one, which will be the most up-to-date.
		if (scheduleList.size() > 0) {
			ScheduleModel lastUpdate = scheduleList.get(0);
			for (ScheduleModel item : scheduleList) {
				if (item.compareTo(lastUpdate) != 0)
					filteredList.add(lastUpdate);
				lastUpdate = item;
			}
			filteredList.add(lastUpdate);
		}

		// Update student age fields and count
		updateScheduleData(filteredList, pike13Api);

		// Update changes in database
		if (filteredList.size() > 0) {
			sqlImportDb.importSchedule(filteredList);
			System.out.println(filteredList.size() + " schedule records imported from Pike13");
		}
	}

	private void updateScheduleData(ArrayList<ScheduleModel> schedule, Pike13DbImport pike13Api) {
		// Update the age fields and the attendance count for each class in schedule
		ArrayList<StudentModel> students = sqlImportDb.getActiveStudents();

		for (ScheduleModel sched : schedule) {
			String className = sched.getClassName().trim();
			int attCount = 0, ageCount = 0;
			Double ageMin = 0.0, ageMax = 0.0, ageAvg = 0.0, ageTot = 0.0;
			int[][] moduleCnt = new int[8][10]; // Curr count by levels 0-7, for modules 0-9
			int[] levelCnt = new int[8]; // Student count by level for this class

			for (StudentModel stud : students) {
				// Only process students who are in levels 0 through 7
				if (!stud.getCurrentLevel().equals("") && stud.getCurrentLevel().charAt(0) > '7')
					continue;

				// Update for each student in this class
				if (className.equals(stud.getCurrentClass().trim()) || className.equals(stud.getRegisterClass().trim())) {
					attCount++; // Update attendance for this class

					// Increment count for current level
					int level = 0;
					if (!stud.getCurrentLevel().equals(""))
						level = Integer.parseInt(stud.getCurrentLevel());
					levelCnt[level]++;

					// Update min, max age
					if (stud.getAge() > 0) {
						ageCount++;
						ageTot += stud.getAge();
						if (ageMin == 0 || stud.getAge() < ageMin)
							ageMin = stud.getAge();
						if (stud.getAge() > ageMax)
							ageMax = stud.getAge();
					}

					// Update count per level & module
					if (stud.getCurrentModule() != null && !stud.getCurrentModule().equals("")
							&& stud.getCurrentModule().charAt(0) >= '0' && stud.getCurrentModule().charAt(0) <= '9')
						moduleCnt[level][stud.getCurrentModule().charAt(0) - '0']++;
				}
			}

			// If any students in this scheduled class, update the class level field
			if (attCount > 0) {
				String levelString = "";
				for (int i = 0; i < levelCnt.length; i++) { // Loop thru each level
					if (levelCnt[i] > 0) {
						String moduleString = "";
						for (int j = 0; j < 10; j++) { // Loop thru each model for this level
							if (moduleCnt[i][j] > 0) {
								if (moduleString.equals(""))
									moduleString = " (Mod ";
								else
									moduleString += ", ";
								moduleString += j;
							}
						}
						if (!moduleString.equals(""))
							moduleString += ")";
						if (!levelString.equals(""))
							levelString += ", ";
						levelString += levelCnt[i] + "@L" + i + moduleString;
					}
				}
				
				// Update room field
				String room = getRoomFromScheduleID(sched.getScheduleID(), pike13Api);
				boolean roomMismatch = checkRoomMismatch(className, room, levelString);

				// Update schedule with attendance and level info
				if (ageCount > 0) {
					ageAvg = ageTot / ageCount;
					sched.setMiscSchedFields(attCount, ageMin.toString().substring(0, 4),
							ageMax.toString().substring(0, 4), ageAvg.toString().substring(0, 4), levelString, 
							room, roomMismatch);
				} else
					sched.setMiscSchedFields(attCount, "", "", "", levelString, room, roomMismatch);
				
			} else {
				sched.setMiscSchedFields(0, "", "", "", "", "", false);
			}
		}
	}

	private String getRoomFromScheduleID (int scheduleID, Pike13DbImport pike13Api) {
		String roomName = pike13Api.getRoomField(scheduleID);
		
		// Now sort/filter the room names
		String sortedRoomName = "";
		for (int i = 0; i <= 7; i++) {
			if (roomName.contains("Level " + i)) {
				if (!sortedRoomName.equals(""))
					sortedRoomName += ",";
				sortedRoomName += i;
			}
		}
		return sortedRoomName;
	}
	
	private boolean checkRoomMismatch (String className, String room, String levelString) {
		if (!className.contains("Java@CV"))
			return false;
		
		for (Integer i = 0; i <= 7; i++) {
			if (levelString.contains("L" + i) && !room.contains(i.toString()))
				return true;
		}
		return false;
	}

	public void importCoursesFromPike13(Pike13DbImport pike13Api) {
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));
		String startDate = today.minusDays(COURSE_DAYS_IN_PAST).toString("yyyy-MM-dd");
		String endDate = today.plusDays(COURSE_DAYS_IN_FUTURE).toString("yyyy-MM-dd");

		// Get data from Pike13
		ArrayList<CoursesModel> coursesList = pike13Api.getCourses(startDate, endDate);

		// Update changes in database
		if (coursesList.size() > 0) {
			sqlImportDb.importCourses(coursesList);
			System.out.println(coursesList.size() + " course records imported from Pike13");
		}
	}

	public void importGithubComments(String startDate, GithubApi githubApi) {
		// Update github comments from "pending github" table.
		// This table is populated each time a student commits to a league github classroom.
		ArrayList<PendingGithubModel> githubList = sqlImportDb.getPendingGithubEvents();
		ArrayList<AttendanceEventModel> eventList = sqlImportDb.getEventsWithNoComments(startDate, 0, true);

		int origGithubListSize = githubList.size();
		if (eventList.size() > 0)
			sqlImportDb.updatePendingGithubComments(githubList, startDate, eventList);

		// Get list of events with missing comments
		eventList = sqlImportDb.getEventsWithNoComments(startDate, 0, false);

		if (eventList.size() > 0) {
			// Import Github comments that are not in git classroom
			githubApi.importGithubComments(startDate, eventList);

			// Update any remaining null comments to show event was processed
			githubApi.updateEmptyGithubComments(eventList);

			System.out.println((eventList.size() + (origGithubListSize - githubList.size())) + " github records processed");
		}
	}
}
