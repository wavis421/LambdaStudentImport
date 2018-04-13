package com.amazonaws.lambda.league;

import java.io.IOException;

import org.junit.BeforeClass;

import com.amazonaws.services.lambda.runtime.Context;

/**
 * A simple test harness for locally invoking your Lambda function handler.
 */
public class StudentImportTest {

	//private static Object input;
	private static String input;

	@BeforeClass
	public static void createInput() throws IOException {
		// TODO: set up your sample input object here.
		input = "Wendy";
	}

	private Context createContext() {
		TestContext ctx = new TestContext();

		// TODO: customize your context here if needed.
		// ctx.setFunctionName("Your Function Name");

		return ctx;
	}

	/*
	@Test
	public void testStudentImportHandler() {
		StudentImport handler = new StudentImport();
		Context ctx = createContext();

		String output = handler.myHandler(input, ctx);

		// TODO: validate output here if needed.
		Assert.assertEquals("Hello, " + input + "!", output);
	}
	*/
}
