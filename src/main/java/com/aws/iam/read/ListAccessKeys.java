package com.dish.anywhere.aws.iam.read;

import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AccessKeyMetadata;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;

/**
 * List all access keys associated with an IAM user
 */
public class ListAccessKeys {
	public static void main(String[] args) {
		Region region = Region.AWS_GLOBAL;
		IamClient iam = IamClient.builder()
				.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
				.region(region).build();
		ListAccessKeysRequest request = ListAccessKeysRequest.builder().userName("aws-cli").build();
		ListAccessKeysResponse response = iam.listAccessKeys(request);
		for (AccessKeyMetadata metadata : response.accessKeyMetadata()) {
			System.out.format("Retrieved access key %s", metadata.toString());
		}
		iam.close();
	}
}
