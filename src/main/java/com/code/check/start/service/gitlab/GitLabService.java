package com.code.check.start.service.gitlab;

import lombok.RequiredArgsConstructor;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * @Author yueyue.guan
 * @date 2025/8/18 15:54
 * @desc
 */
@Service
@RequiredArgsConstructor
public class GitLabService {

    @Autowired
    private GitLabApi gitLabApi;

    /**
     * 获取项目信息
     */
    public Project getProject(Long projectId) throws GitLabApiException {
        return gitLabApi.getProjectApi().getProject(projectId.intValue());
    }

    /**
     * 获取提交详情
     */
    public Commit getCommit(Long projectId, String commitId) throws GitLabApiException {
        return gitLabApi.getCommitsApi().getCommit(projectId.intValue(), commitId);
    }

    /**
     * 获取提交的变更文件
     */
    public List<Diff> getCommitDiffs(Long projectId, String commitId) throws GitLabApiException {
        return gitLabApi.getCommitsApi().getDiff(projectId.intValue(), commitId);
    }

    /**
     * 获取合并请求详情
     */
    public MergeRequest getMergeRequest(Long projectId, Long mergeRequestId) throws GitLabApiException {
        return gitLabApi.getMergeRequestApi().getMergeRequest(projectId.intValue(), mergeRequestId);
    }

    /**
     * 获取合并请求的所有提交
     */
    public List<Commit> getMergeRequestCommits(Long projectId, Long mergeRequestId) throws GitLabApiException {
        return gitLabApi.getMergeRequestApi().getCommits(projectId.intValue(), mergeRequestId);
    }

    /**
     * 验证GitLab WebHook签名
     *
     * @param secretToken GitLab中配置的WebHook密钥
     * @param payload 接收到的WebHook请求体
     * @param signature 从请求头X-Gitlab-Token或X-Hub-Signature获取的签名
     * @return 验证是否通过
     */
    public static boolean verifyWebHookSignature(String secretToken, String payload, String signature) {
        if (secretToken == null || secretToken.isEmpty() || payload == null || signature == null) {
            return false;
        }
        return true;
    }

}
