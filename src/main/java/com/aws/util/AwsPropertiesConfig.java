package com.aws.util;

/**
 * List all access keys associated with an IAM user
 */
public class AwsPropertiesConfig {
	private AwsPropertiesConfig() {
		System.out.println("Private Constructor");
	}

	public static final String AWS_READ_PROFILE = "read";
	public static final String AWS_WRITE_PROFILE = "write";
}
