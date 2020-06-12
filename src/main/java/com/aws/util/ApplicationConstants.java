package com.aws.util;

/**
 * List all access keys associated with an IAM user
 */
public class ApplicationConstants {
	private ApplicationConstants() {
		System.out.println("Private Constructor");
	}

	public static final String NEW_ROW = "\n";
	public static final String COMMA = ",";
	public static final String AMPERSAND = "&";

	public static final String SECURITY_GROUP_ID = "SecurityGroupId";
	public static final String EMPTY_STRING = "";
	public static final String ALL = "ALL";
	public static final String PROJECT = "Project";
	public static final String NAME = "Name";

	public static final String STATUS = "Status";
	public static final String MESSAGE = "Message";

	public static final String SUCCESS_MESSAGE = "Successfully Completed";
	public static final String RETRY_MESSAGE = "Retriable error";
	public static final String SKIPPED_MESSAGE = "Security group in use";
	public static final String DEFAULT_MESSAGE = "Default security group";

	public static final String COMPLETED = "Completed";
	public static final String ERROR = "Error";
	public static final String RETRY = "Retry";
	public static final String SKIPPED = "Skipped";
	public static final String DEFAULT = "Default";

}
