package com.dish.anywhere.aws.iam.read;

import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListSshPublicKeysRequest;
import software.amazon.awssdk.services.iam.model.ListSshPublicKeysResponse;
import software.amazon.awssdk.services.iam.model.ListUsersRequest;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.SSHPublicKeyMetadata;
import software.amazon.awssdk.services.iam.model.User;

/**
 * List all access keys associated with an IAM user
 */
public class ListSshPublicKeys {
	public static void main(String[] args) {
		Region region = Region.AWS_GLOBAL;
		IamClient iam = IamClient.builder()
				.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
				.region(region).build();
		ListUsersRequest userRequest = ListUsersRequest.builder().build();
		ListUsersResponse userResponse = iam.listUsers(userRequest);
		for (User user : userResponse.users()) {
			ListSshPublicKeysRequest sshRequest = ListSshPublicKeysRequest.builder().userName(user.userName()).build();
			ListSshPublicKeysResponse sshResponse = iam.listSSHPublicKeys(sshRequest);
			if (!sshResponse.sshPublicKeys().isEmpty()) {
				for (SSHPublicKeyMetadata metadata : sshResponse.sshPublicKeys()) {
					System.out.format("SSH access key found for %s  Ssh Metadata : %s \r\n", user.userName(),
							metadata.toString());
				}
			} else {
				System.out.format("SSH access key not found for %s \r\n", user.userName());
			}
		}

		iam.close();
	}
}
