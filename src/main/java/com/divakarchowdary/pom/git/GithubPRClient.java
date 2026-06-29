package com.divakarchowdary.pom.git;

import com.divakarchowdary.pom.model.PRInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class GithubPRClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();


    public PRInfo createPullRequest(String apiBase, String owner, String repo,
                                    String head, String base, String title,
                                    String body, String token) throws IOException {

        String url = apiBase + "/repos/" + owner + "/" + repo + "/pulls";

        String json = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"head\":\"%s\",\"base\":\"%s\"}",
                escape(title), escape(body), head, base);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertSuccess(response, "PR creation failed for " + owner + "/" + repo);
            return parsePRInfo(response.body().string());
        }
    }


    public void assignReviewers(String apiBase, String owner, String repo,
                                int prNumber, List<String> reviewers, String token) throws IOException {

        String url = apiBase + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/requested_reviewers";
        String json = "{\"reviewers\":[" + toJsonArray(reviewers) + "]}";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertSuccess(response, "Reviewer assignment failed for PR #" + prNumber);
            log.info("Reviewers {} assigned to PR #{}", reviewers, prNumber);
        }
    }

    public PRInfo findExistingPR(String apiBase, String owner, String repo,
                                  String headBranch, String token) throws IOException {

        String url = apiBase + "/repos/" + owner + "/" + repo
                     + "/pulls?state=open&head=" + owner + ":" + headBranch;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertSuccess(response, "Failed to fetch open PRs for " + repo);
            String body = response.body().string();

            if (body.trim().equals("[]")) {
                return null;
            }

            return parseFirstPRFromArray(body);
        }
    }


    public void approvePR(String apiBase, String owner, String repo,
                          int prNumber, String approverToken) throws IOException {

        String url = apiBase + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews";
        String json = "{\"event\":\"APPROVE\"}";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + approverToken)
                .addHeader("User-Agent", "PomStandardizationAgent")
                .addHeader("Accept", "application/vnd.github+json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertSuccess(response, "PR approval failed for PR #" + prNumber);
            log.info("PR #{} approved in {}/{}", prNumber, owner, repo);
        }
    }


    public void mergePR(String apiBase, String owner, String repo,
                        int prNumber, String approverToken) throws IOException {

        String url = apiBase + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/merge";
        String json = "{\"merge_method\":\"merge\"}";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "token " + approverToken)
                .addHeader("User-Agent", "PomStandardizationAgent")
                .addHeader("Accept", "application/vnd.github+json")
                .put(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertSuccess(response, "PR merge failed for PR #" + prNumber);
            log.info("PR #{} merged in {}/{}", prNumber, owner, repo);
        }
    }

    private void assertSuccess(Response response, String errorMessage) throws IOException {
        if (!response.isSuccessful()) {
            String body = response.body() != null ? response.body().string() : "<empty>";
            throw new IOException(errorMessage + " | HTTP " + response.code() + " | " + body);
        }
    }

    private PRInfo parsePRInfo(String json) {
        PRInfo info = new PRInfo();
        info.setPrNumber(extractIntField(json, "\"number\""));
        info.setPrUrl(extractStringField(json, "\"html_url\""));
        info.setAuthor(extractStringField(json, "\"login\""));
        return info;
    }

    private PRInfo parseFirstPRFromArray(String json) {
        String inner = json.trim();
        if (inner.startsWith("[")) {
            inner = inner.substring(1, inner.lastIndexOf("]")).trim();
        }
        return parsePRInfo(inner);
    }

    private int extractIntField(String json, String key) {
        try {
            int keyIdx = json.indexOf(key);
            if (keyIdx < 0) return 0;
            String after = json.substring(keyIdx + key.length());
            String value = after.replaceFirst("^\\s*:\\s*", "");
            String num = value.split("[,}\\]]")[0].trim();
            return Integer.parseInt(num);
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractStringField(String json, String key) {
        try {
            int keyIdx = json.indexOf(key);
            if (keyIdx < 0) return null;
            String after = json.substring(keyIdx + key.length());
            after = after.replaceFirst("^\\s*:\\s*\"", "");
            return after.substring(0, after.indexOf("\""));
        } catch (Exception e) {
            return null;
        }
    }

    private String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i)).append("\"");
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
