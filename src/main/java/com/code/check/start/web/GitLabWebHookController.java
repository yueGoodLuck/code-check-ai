package com.code.check.start.web;

import com.alibaba.fastjson.JSON;
import com.code.check.start.event.gitlab.GitlabEventPublisher;
import com.code.check.start.model.CodeSubmission;
import com.code.check.start.model.GitlabEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;


/**
 * @Author yueyue.guan
 * @date 2025/8/18 15:54
 * @desc
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class GitLabWebHookController {


    private final ObjectMapper objectMapper;

    @Value("${gitlab.webhook.secret}")
    private String webhookSecret;

    @PostMapping("${gitlab.webhook.endpoint}")
    public ResponseEntity<Void> handleWebHook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String eventType,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token) {

        log.info("Received GitLab webhook event: {} token:{}", eventType, token);

        // 验证签名
        /*if (!GitLabService.verifyWebHookSignature(webhookSecret, payload, token)) {
            log.warn("Invalid GitLab webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }*/

        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            GitlabEventType eventTypeEnum = GitlabEventType.getByEventType(eventType);

            if (eventType == null) {
                log.info("Unhandled GitLab event type: {}", eventType);
                return ResponseEntity.ok().build();
            }
            handlePushEvent(payloadNode, eventTypeEnum);

            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Error processing webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void handlePushEvent(JsonNode payload, GitlabEventType eventTypeEnum) {
        try {
            Long projectId = payload.get("project_id").asLong();
            String projectName = payload.get("project").get("name").asText();
            String repositoryUrl = payload.get("repository").get("url").asText();
            // 处理每个提交
            for (JsonNode commitNode : payload.get("commits")) {
                String commitId = commitNode.get("id").asText();
                String authorName = commitNode.get("author").get("name").asText();
                String commitMessage = commitNode.get("message").asText();

                CodeSubmission submission = CodeSubmission.builder()
                        .projectId(projectId)
                        .projectName(projectName)
                        .repositoryUrl(repositoryUrl)
                        .commitId(commitId)
                        .author(authorName)
                        .message(commitMessage)
                        .type(eventTypeEnum.getSubmissionType())
                        .build();

                // 发布代码提交事件
                GitlabEventPublisher.publishEvent(submission);
            }
        } catch (Exception e) {
            log.error("Error handling push event", e);
        }
    }


}
