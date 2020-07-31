package com.dish.anywhere.aws.iam.read;

import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.GetGroupRequest;
import software.amazon.awssdk.services.iam.model.GetGroupResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionRequest;
import software.amazon.awssdk.services.iam.model.Group;
import software.amazon.awssdk.services.iam.model.ListAttachedGroupPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListEntitiesForPolicyRequest;
import software.amazon.awssdk.services.iam.model.ListUsersRequest;
import software.amazon.awssdk.services.iam.model.PolicyGroup;
import software.amazon.awssdk.services.iam.model.PolicyRole;
import software.amazon.awssdk.services.iam.model.PolicyUser;
import software.amazon.awssdk.services.iam.model.User;

/**
 * List all policies based upon the input file we defined in the input folder
 */
public class ListRoleInlinePolicies {
	private static Logger log = Logger.getLogger(ListRoleInlinePolicies.class);
	private static String format = "%s|%s|%s|%s|%s\r\n";
	private static final String header = "Policy|Permissions|Roles|Direct Users|Groups\r\n";

	public static void main(String[] args) {
		log.info("Started ListRoleInlinePolicies process");
		String inputFileName = ".\\input\\ListRoleInlinePoliciesInput.csv";
		String outputFileName = ".\\output\\ListRoleInlinePoliciesOutput-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(outputFileName, header, null);

		Region region = Region.AWS_GLOBAL;
		IamClient iam = IamClient.builder()
				.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
				.region(region).build();
		BufferedReader csvReader;
		try {
			csvReader = new BufferedReader(new FileReader(inputFileName));
			String row = null;
			while ((row = csvReader.readLine()) != null) {
				if (row != null) {
					String roleName = row;
					ListAttachedRolePoliciesRequest listAttachedGroupPoliciesRequest = ListAttachedRolePoliciesRequest.builder().roleName(roleName).build();
					List<AttachedPolicy> attachedPolicies = iam.listAttachedRolePolicies(listAttachedGroupPoliciesRequest).attachedPolicies();
					System.out.println(attachedPolicies);
//					describePolicies(outputFileName, policy, policyDocument, policyRoles, policyUsers, policyGroups);
				}
			}
			csvReader.close();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("Completed ListRoleInlinePolicies process");
		iam.close();
	}

	/***
	 * Format the content before writing
	 * 
	 * @param outputFileName
	 * @param policy
	 * @param permissions
	 * @param policyRoles 
	 * @param policyUsers 
	 */
	private static void describePolicies(String outputFileName, String policy, String permissions, String policyRoles, String policyUsers, String policyGroups) {
		String output = "";
		output = String.format(format, policy, permissions, policyRoles, policyUsers, policyGroups);
		print(outputFileName, output, StandardOpenOption.APPEND);
	}

	/***
	 * Write the content to the file
	 * 
	 * @param fileName
	 * @param content
	 * @param standardOpenOption
	 * @catches IOException
	 */
	private static void print(String fileName, String content, StandardOpenOption standardOpenOption) {
		try {
			if (standardOpenOption != null) {
				Files.write(Paths.get(fileName), content.getBytes(), StandardOpenOption.APPEND);
			} else {
				Files.write(Paths.get(fileName), content.getBytes());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/***
	 * Iterate over to find the most recent version of a given policy
	 * 
	 * @param iam
	 * @param policyName
	 */
	private static String[] checkArn(IamClient iam, String policyName) {
		String[] response = {};

		for (int i = 25; i > 0; i--) {
			String versionId = "v" + i;
			try {
				response = checkArnWithVersion(iam, policyName, versionId);
			} catch (RuntimeException e) {
			}
			if (response.length != 0) {
				break;
			}
		}

		return response;
	}

	/***
	 * Reach out to retrieve policy document
	 * 
	 * @param iam
	 * @param policyName
	 * @param versionId
	 * @catches RuntimeException
	 */
	private static String[] checkArnWithVersion(IamClient iam, String policyName, String versionId) {
		String response = "";
		String policyArn = "";

		try {
			policyArn = "arn:aws:iam::012954582121:policy/" + policyName;
			GetPolicyVersionRequest request = GetPolicyVersionRequest.builder().policyArn(policyArn)
					.versionId(versionId).build();
			response = iam.getPolicyVersion(request).policyVersion().document();
		} catch (RuntimeException e) {
			policyArn = "arn:aws:iam::012954582121:policy/service-role/" + policyName;
			GetPolicyVersionRequest request = GetPolicyVersionRequest.builder().policyArn(policyArn)
					.versionId(versionId).build();
			response = iam.getPolicyVersion(request).policyVersion().document();
		}
		
		String[] arnResponse = {policyArn, response};

		return arnResponse;
	}

	/***
	 * Parse the users assigned to a policy to readable format for the spreadsheet
	 * 
	 * @param iam
	 * @param listEntitiesForPolicyRequest
	 */
	private static String parsePolicyUsers(IamClient iam, ListEntitiesForPolicyRequest listEntitiesForPolicyRequest) {
		String allUsers = "";
		List<PolicyUser> listPolicyUsers = iam.listEntitiesForPolicy(listEntitiesForPolicyRequest).policyUsers();
		for (int i = 0; i < listPolicyUsers.size(); i++) {
			allUsers = allUsers + listPolicyUsers.get(i).userName();
			if (listPolicyUsers.size() - 1 != i) {
				allUsers = allUsers + "&";
			}
		}
		return allUsers;
	}

	/***
	 * Parse the roles assigned to a policy to readable format for the spreadsheet
	 * 
	 * @param iam
	 * @param listEntitiesForPolicyRequest
	 */
	private static String parsePolicyRoles(IamClient iam, ListEntitiesForPolicyRequest listEntitiesForPolicyRequest) {
		String allRoles = "";
		List<PolicyRole> listPolicyRoles = iam.listEntitiesForPolicy(listEntitiesForPolicyRequest).policyRoles();
		for (int i = 0; i < listPolicyRoles.size(); i++) {
			allRoles = allRoles + listPolicyRoles.get(i).roleName();
			if (listPolicyRoles.size() - 1 != i) {
				allRoles = allRoles + "&";
			}
		}
		return allRoles;
	}

	/***
	 * Parse the roles assigned to a policy to readable format for the spreadsheet
	 * 
	 * @param iam
	 * @param listEntitiesForPolicyRequest
	 */
	private static String parsePolicyGroups(IamClient iam, ListEntitiesForPolicyRequest listEntitiesForPolicyRequest) {
		String allGroups = "";
		List<PolicyGroup> listPolicyGroups = iam.listEntitiesForPolicy(listEntitiesForPolicyRequest).policyGroups();
		for (int i = 0; i < listPolicyGroups.size(); i++) {
			GetGroupRequest getGroupRequest = GetGroupRequest.builder().groupName(listPolicyGroups.get(i).groupName()).build();
			
			List<User> listGroupUsers = iam.getGroup(getGroupRequest).users();
			String groupUsers = "";
			for (int j = 0; j < listGroupUsers.size(); j++) {
				groupUsers = groupUsers + listGroupUsers.get(j).userName();
				if (listGroupUsers.size() - 1 != j) {
					groupUsers = groupUsers + "&";
				}
			}
			
			allGroups = allGroups + listPolicyGroups.get(i).groupName() + ": {" + groupUsers + "}";
			if (listPolicyGroups.size() - 1 != i) {
				allGroups = allGroups + "&";
			}
		}
		return allGroups;
	}

}
