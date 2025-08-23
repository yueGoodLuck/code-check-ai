package com.code.check.start.config;

import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Version;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author yueyue.guan
 * @date 2025/8/18 15:53
 * @desc
 */
@Configuration
@Slf4j
public class GitLabConfig {

    @Value("${gitlab.base-url}")
    private String gitLabBaseUrl;

    @Value("${gitlab.private-token}")
    private String privateToken;

    @Bean
    public GitLabApi gitLabApi() throws GitLabApiException {
        GitLabApi gitLabApi = new GitLabApi(GitLabApi.ApiVersion.V4, gitLabBaseUrl, privateToken);
        // 关键配置：处理 HTTP 和证书问题
        gitLabApi.setIgnoreCertificateErrors(true); // 允许 HTTP/自签名证书
        // 测试连接
        Version version = gitLabApi.getVersion();
        log.info("GitLab version: {}", version);
        return gitLabApi;


    }


}
