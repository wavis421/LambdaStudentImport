package lambda;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.sforce.soap.enterprise.Connector;
import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import controller.Pike13Connect;
import controller.Pike13SalesforceImport;
import controller.SalesForceImportEngine;
import model.LocationLookup;
import model.LogDataModel;
import model.MySqlDatabase;
import model.MySqlDbLogging;
import model.StudentNameModel;

public class SalesForceImport {
	// Import -30 to +45 days
	private static final int DATE_RANGE_PAST_IN_DAYS = 21;
	private static final int DATE_RANGE_FUTURE_IN_DAYS = 45;

	MySqlDatabase sqlDb;
	String startDate, endDate;

	public String myHandler(Object input, Context context) {

		// Set start/end date to -30/+90 days
		DateTime t = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));
		String today = t.toString("yyyy-MM-dd HH:mm:ss");
		startDate = t.minusDays(DATE_RANGE_PAST_IN_DAYS).toString("yyyy-MM-dd");
		endDate = t.plusDays(DATE_RANGE_FUTURE_IN_DAYS).toString("yyyy-MM-dd");

		LambdaLogger logger = context.getLogger();
		logger.log(today + " " + input + "\n");

		// Connect to database
		sqlDb = new MySqlDatabase(System.getenv("PASSWORD"), MySqlDatabase.STUDENT_IMPORT_NO_SSH);
		if (!sqlDb.connectDatabase()) {
			logger.log("Failure connecting to mySql database!");
			return ("Start: " + today + ", End: " + (new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"))
					.toString("yyyy-MM-dd HH:mm:ss")));
		}

		new MySqlDbLogging(sqlDb);
		MySqlDbLogging.insertLogData(LogDataModel.STARTING_SALES_FORCE_IMPORT, new StudentNameModel("", "", false), 0,
				" from " + startDate + " to " + endDate + " ***");

		// Connect to Pike13
		Pike13Connect pike13Conn = new Pike13Connect(System.getenv("PIKE13_KEY"));
		Pike13SalesforceImport pike13Api = new Pike13SalesforceImport(pike13Conn);

		// Connect to SalesForce
		EnterpriseConnection salesForceApi = null;
		ConnectorConfig config = new ConnectorConfig();
		config.setUsername(System.getenv("SALESFORCE_USER"));
		config.setPassword(System.getenv("SALESFORCE_KEY"));
		config.setTraceMessage(false);

		for (int i = 0; i < 2; i++) { // Try twice
			try {
				salesForceApi = Connector.newConnection(config);
				break;

			} catch (ConnectionException e1) {
				e1.printStackTrace();
				if (i > 0) {
					lambdaFunctionEnd(LogDataModel.SALES_FORCE_CONNECTION_ERROR, e1.getMessage());
					return ("Start: " + today + ", End: " + (new DateTime()
							.withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd HH:mm:ss")));
				}
			}
		}

		// Perform the update to SalesForce
		SalesForceImportEngine importer = new SalesForceImportEngine(sqlDb, pike13Api, salesForceApi);
		LocationLookup.setLocationData(sqlDb.getLocationList());
		importer.updateSalesForce(today, startDate, endDate);

		// Clean up and exit
		lambdaFunctionEnd(-1, null); // -1 indicates no error
		return ("Start: " + today + ", End: "
				+ (new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles")).toString("yyyy-MM-dd HH:mm:ss")));
	}

	private void lambdaFunctionEnd(int errorCode, String errorMessage) {
		if (errorCode == -1) {
			// Success
			MySqlDbLogging.insertLogData(LogDataModel.SALES_FORCE_IMPORT_COMPLETE, new StudentNameModel("", "", false),
					0, " from " + startDate + " to " + endDate + " ***");
		} else {
			// Failure
			MySqlDbLogging.insertLogData(errorCode, new StudentNameModel("", "", false), 0, ": " + errorMessage);
			MySqlDbLogging.insertLogData(LogDataModel.SALES_FORCE_IMPORT_ABORTED, new StudentNameModel("", "", false),
					0, " from " + startDate + " to " + endDate + " ***");
		}
		sqlDb.disconnectDatabase();
	}
}
