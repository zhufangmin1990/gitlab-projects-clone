package com.opensource.gitlab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensource.gitlab.vo.GitBranch;
import com.opensource.gitlab.vo.GitGroup;
import com.opensource.gitlab.vo.GitProject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过gitlab Api自动下载gitLab上的所有项目
 */
@Service
public class GitlabProjectCloneService {

    @Value("${git.gitlabUrl}")
    private String gitlabUrl;

    @Value("${git.privateToken}")
    private String privateToken;

    @Value("${git.projectDir}")
    private String projectDir;

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    RestTemplate restTemplate;

    @PostConstruct
    private void start() {
        File execDir = new File(projectDir);
        System.out.println("start get gitlab projects");
        List<GitGroup> groups = getGroups();
        try {
            System.out.println(objectMapper.writeValueAsString(groups));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        for (GitGroup group : groups) {
            if(group.getName().equals("geometry")){
                String parentGrpId = String.valueOf(group.getId());
                List<GitGroup> subGroups = getSubGroups(parentGrpId,null);
                if (subGroups != null) {
                    for (GitGroup subGroup : subGroups) {
                        System.out.println("start clone projects in " + subGroup.getName());
                        String subGrpId = String.valueOf(subGroup.getId());
                        List<GitProject> projects = getProjectsByGroup(subGrpId);
                        for (GitProject project : projects) {
                            clone("master", project, execDir);
                        }
                    }
                }

            }

        }
        System.out.println("end get gitlab projects");
    }

    /**
     * 获取所有项目
     *
     * @return
     */
    private List<GitProject> getAllProjects() {
        String url = gitlabUrl + "/api/v3/projects?per_page={per_page}&private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("per_page", "100");
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitProject>> responseType = new ParameterizedTypeReference<List<GitProject>>() {
        };
        ResponseEntity<List<GitProject>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
    * 获取子分组
     * @param parentGrpId
    * */
    private List<GitGroup>  getSubGroups(String  parentGrpId,List<GitGroup> groups){
        if(groups == null){
          groups=new ArrayList<>();
        }
        String url = gitlabUrl + "/api/v3/groups/{parent_id}/subgroups/?per_page={per_page}&private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("parent_id",parentGrpId);
        uriVariables.put("per_page", "100");
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitGroup>> responseType = new ParameterizedTypeReference<List<GitGroup>>() {
        };
        ResponseEntity<List<GitGroup>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);

        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            List<GitGroup> subGitGrps = responseEntity.getBody();
            if(subGitGrps.size()>0){
                for(GitGroup subGitGrp:subGitGrps){
                    String gid = String.valueOf(subGitGrp.getId());
                     List<GitGroup> subGroups= getSubGroups(gid, groups);
                    if(subGroups!=null &&subGroups.size()>0){
                        groups.addAll(subGroups) ;
                    }else{
                        return groups;
                    }
                }
            }
        }
        return null;
    }
    /**
     * 获取指定分组下的项目
     *
     * @param groupId
     * @return
     */

    private List<GitProject> getProjectsByGroup(String groupId) {

        String url = gitlabUrl + "/api/v3/groups/{id}/projects?per_page={per_page}&private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("id", groupId);
        uriVariables.put("per_page", "100");
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitProject>> responseType = new ParameterizedTypeReference<List<GitProject>>() {
        };
        ResponseEntity<List<GitProject>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 获取分组列表
     *
     * @return
     */
    private List<GitGroup> getGroups() {
        String url = gitlabUrl + "/api/v3/groups?private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("private_token", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitGroup>> responseType = new ParameterizedTypeReference<List<GitGroup>>() {
        };
        ResponseEntity<List<GitGroup>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 获取最近修改的分支名称
     *
     * @param projectId 项目ID
     * @return
     */
    private String getLastActivityBranchName(Long projectId) {
        List<GitBranch> branches = getBranches(projectId);
        if (CollectionUtils.isEmpty(branches)) {
            return "";
        }
        GitBranch gitBranch = getLastActivityBranch(branches);
        return gitBranch.getName();
    }

    /**
     * 获取指定项目的分支列表
     * https://docs.gitlab.com/ee/api/branches.html#branches-api
     *
     * @param projectId 项目ID
     * @return
     */
    private List<GitBranch> getBranches(Long projectId) {
        String url = gitlabUrl + "/api/v3/projects/{projectId}/repository/branches?private_token={privateToken}";
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("projectId", projectId);
        uriVariables.put("privateToken", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitBranch>> responseType = new ParameterizedTypeReference<List<GitBranch>>() {
        };
        ResponseEntity<List<GitBranch>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 获取最近修改的分支
     *
     * @param gitBranches 分支列表
     * @return
     */
    private GitBranch getLastActivityBranch(final List<GitBranch> gitBranches) {
        GitBranch lastActivityBranch = gitBranches.get(0);
        for (GitBranch gitBranch : gitBranches) {
            if (gitBranch.getCommit().getCommittedDate().getTime() > lastActivityBranch.getCommit().getCommittedDate().getTime()) {
                lastActivityBranch = gitBranch;
            }
        }
        return lastActivityBranch;
    }

    private void clone(String branchName, GitProject gitProject, File execDir) {
        String command = String.format("git clone -b %s %s %s", branchName, gitProject.getHttpUrlToRepo(), gitProject.getName());
        System.out.println("start exec command : " + command);
        try {
            Process exec = Runtime.getRuntime().exec(command, null, execDir);
            exec.waitFor();
            String successResult = StreamUtils.copyToString(exec.getInputStream(), Charset.forName("UTF-8"));
            String errorResult = StreamUtils.copyToString(exec.getErrorStream(),Charset.forName("UTF-8"));
            System.out.println("successResult: " + successResult);
            System.out.println("errorResult: " + errorResult);
            System.out.println("================================");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
