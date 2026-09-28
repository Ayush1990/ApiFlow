package com.apiflow.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.apiflow.model.IdentitySettings;

@Service
public class PullRequestService {

	private static final Pattern GITHUB = Pattern.compile("github\\.com[:/]([^/]+)/([^/.]+)");
	private static final Pattern GITLAB = Pattern.compile("gitlab\\.com[:/](.+?)/([^/.]+)");
	private static final Pattern BITBUCKET = Pattern.compile("bitbucket\\.org[:/]([^/]+)/([^/.]+)");

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	public List<PullRequest> list(String remoteUrl, IdentitySettings identity) {
		Repo repo = parse(remoteUrl);
		if (repo == null) {
			throw new IllegalArgumentException("Remote URL is not a supported Git provider");
		}
		if (identity.getGitProviderToken().isBlank()) {
			throw new IllegalArgumentException("Add a Git provider token in Workspace → Enterprise settings");
		}
		return switch (repo.provider()) {
			case "github" -> listGitHub(repo, identity.getGitProviderToken());
			case "bitbucket" -> listBitbucket(repo, identity.getGitProviderToken());
			default -> listGitLab(repo, identity.getGitProviderToken());
		};
	}

	public PullRequest create(String remoteUrl, IdentitySettings identity, String title, String body, String head, String base) {
		Repo repo = parse(remoteUrl);
		if (repo == null) {
			throw new IllegalArgumentException("Remote URL is not a supported Git provider");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("PR title is required");
		}
		String token = identity.getGitProviderToken();
		if (token.isBlank()) {
			throw new IllegalArgumentException("Add a Git provider token in Workspace → Enterprise settings");
		}
		return switch (repo.provider()) {
			case "github" -> createGitHub(repo, token, title, body, head, base);
			case "bitbucket" -> createBitbucket(repo, token, title, body, head, base);
			default -> createGitLab(repo, token, title, body, head, base);
		};
	}

	public String merge(String remoteUrl, IdentitySettings identity, int number) {
		Repo repo = parse(remoteUrl);
		if (repo == null) {
			throw new IllegalArgumentException("Remote URL is not a supported Git provider");
		}
		String token = identity.getGitProviderToken();
		return switch (repo.provider()) {
			case "github" -> mergeGitHub(repo, token, number);
			case "bitbucket" -> mergeBitbucket(repo, token, number);
			default -> mergeGitLab(repo, token, number);
		};
	}

	private List<PullRequest> listGitHub(Repo repo, String token) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo.owner() + "/" + repo.name() + "/pulls?state=open"))
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/vnd.github+json")
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GitHub API returned " + response.statusCode());
			}
			List<PullRequest> items = new ArrayList<>();
			for (String chunk : response.body().split("\\{\"url\":")) {
				if (!chunk.contains("\"number\"")) {
					continue;
				}
				items.add(new PullRequest(
					extractInt(chunk, "number"),
					extractString(chunk, "title"),
					extractString(chunk, "html_url"),
					extractNested(chunk, "head", "ref"),
					extractNested(chunk, "base", "ref"),
					extractString(chunk, "state")
				));
			}
			return items;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GitHub PR list failed: " + ex.getMessage());
		}
	}

	private PullRequest createGitHub(Repo repo, String token, String title, String body, String head, String base) {
		String payload = "{\"title\":" + json(title) + ",\"body\":" + json(body == null ? "" : body)
			+ ",\"head\":" + json(head == null || head.isBlank() ? repo.name() : head)
			+ ",\"base\":" + json(base == null || base.isBlank() ? "main" : base) + "}";
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo.owner() + "/" + repo.name() + "/pulls"))
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/vnd.github+json")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GitHub API returned " + response.statusCode() + ": " + response.body());
			}
			String chunk = response.body();
			return new PullRequest(
				extractInt(chunk, "number"),
				extractString(chunk, "title"),
				extractString(chunk, "html_url"),
				extractNested(chunk, "head", "ref"),
				extractNested(chunk, "base", "ref"),
				extractString(chunk, "state")
			);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GitHub PR create failed: " + ex.getMessage());
		}
	}

	private String mergeGitHub(Repo repo, String token, int number) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo.owner() + "/" + repo.name() + "/pulls/" + number + "/merge"))
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/vnd.github+json")
				.PUT(HttpRequest.BodyPublishers.ofString("{}"))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GitHub merge failed: " + response.statusCode());
			}
			return "Merged PR #" + number;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GitHub merge failed: " + ex.getMessage());
		}
	}

	private List<PullRequest> listGitLab(Repo repo, String token) {
		try {
			String project = encode(repo.owner() + "/" + repo.name());
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://gitlab.com/api/v4/projects/" + project + "/merge_requests?state=opened"))
				.header("PRIVATE-TOKEN", token)
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GitLab API returned " + response.statusCode());
			}
			List<PullRequest> items = new ArrayList<>();
			for (String chunk : response.body().split("\\{\"id\":")) {
				if (!chunk.contains("\"iid\"")) {
					continue;
				}
				items.add(new PullRequest(
					extractInt(chunk, "iid"),
					extractString(chunk, "title"),
					extractString(chunk, "web_url"),
					extractString(chunk, "source_branch"),
					extractString(chunk, "target_branch"),
					extractString(chunk, "state")
				));
			}
			return items;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GitLab MR list failed: " + ex.getMessage());
		}
	}

	private PullRequest createGitLab(Repo repo, String token, String title, String body, String head, String base) {
		String project = encode(repo.owner() + "/" + repo.name());
		String payload = "title=" + encode(title) + "&description=" + encode(body == null ? "" : body)
			+ "&source_branch=" + encode(head == null || head.isBlank() ? "feature" : head)
			+ "&target_branch=" + encode(base == null || base.isBlank() ? "main" : base);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://gitlab.com/api/v4/projects/" + project + "/merge_requests"))
				.header("PRIVATE-TOKEN", token)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GitLab API returned " + response.statusCode() + ": " + response.body());
			}
			String chunk = response.body();
			return new PullRequest(
				extractInt(chunk, "iid"),
				extractString(chunk, "title"),
				extractString(chunk, "web_url"),
				extractString(chunk, "source_branch"),
				extractString(chunk, "target_branch"),
				extractString(chunk, "state")
			);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GitLab MR create failed: " + ex.getMessage());
		}
	}

	private String mergeGitLab(Repo repo, String token, int number) {
		String project = encode(repo.owner() + "/" + repo.name());
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://gitlab.com/api/v4/projects/" + project + "/merge_requests/" + number + "/merge"))
				.header("PRIVATE-TOKEN", token)
				.PUT(HttpRequest.BodyPublishers.noBody())
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("GitLab merge failed: " + response.statusCode());
			}
			return "Merged MR !" + number;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("GitLab merge failed: " + ex.getMessage());
		}
	}

	private List<PullRequest> listBitbucket(Repo repo, String token) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.bitbucket.org/2.0/repositories/" + repo.owner() + "/" + repo.name() + "/pullrequests?state=OPEN"))
				.header("Authorization", "Bearer " + token)
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Bitbucket API returned " + response.statusCode());
			}
			List<PullRequest> items = new ArrayList<>();
			for (String chunk : response.body().split("\\{\"comment_count\"")) {
				if (!chunk.contains("\"id\"")) {
					continue;
				}
				items.add(new PullRequest(
					extractInt(chunk, "id"),
					extractString(chunk, "title"),
					extractNested(chunk, "links", "html"),
					extractNested(chunk, "source", "branch"),
					extractNested(chunk, "destination", "branch"),
					"open"
				));
			}
			return items;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Bitbucket PR list failed: " + ex.getMessage());
		}
	}

	private PullRequest createBitbucket(Repo repo, String token, String title, String body, String head, String base) {
		String payload = """
				{"title":%s,"description":%s,"source":{"branch":{"name":%s}},"destination":{"branch":{"name":%s}}}
				""".formatted(json(title), json(body == null ? "" : body), json(head), json(base == null || base.isBlank() ? "main" : base));
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.bitbucket.org/2.0/repositories/" + repo.owner() + "/" + repo.name() + "/pullrequests"))
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Bitbucket API returned " + response.statusCode() + ": " + response.body());
			}
			String chunk = response.body();
			return new PullRequest(
				extractInt(chunk, "id"),
				extractString(chunk, "title"),
				extractString(chunk, "html"),
				head,
				base,
				"open"
			);
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Bitbucket PR create failed: " + ex.getMessage());
		}
	}

	private String mergeBitbucket(Repo repo, String token, int number) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.bitbucket.org/2.0/repositories/" + repo.owner() + "/" + repo.name() + "/pullrequests/" + number + "/merge"))
				.header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalArgumentException("Bitbucket merge failed: " + response.statusCode());
			}
			return "Merged PR #" + number;
		}
		catch (IllegalArgumentException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Bitbucket merge failed: " + ex.getMessage());
		}
	}

	private static Repo parse(String remoteUrl) {
		if (remoteUrl == null || remoteUrl.isBlank()) {
			return null;
		}
		Matcher github = GITHUB.matcher(remoteUrl);
		if (github.find()) {
			return new Repo("github", github.group(1), github.group(2).replace(".git", ""));
		}
		Matcher gitlab = GITLAB.matcher(remoteUrl);
		if (gitlab.find()) {
			return new Repo("gitlab", gitlab.group(1), gitlab.group(2).replace(".git", ""));
		}
		Matcher bitbucket = BITBUCKET.matcher(remoteUrl);
		if (bitbucket.find()) {
			return new Repo("bitbucket", bitbucket.group(1), bitbucket.group(2).replace(".git", ""));
		}
		return null;
	}

	private static String json(String value) {
		return "\"" + (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String encode(String value) {
		return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static int extractInt(String json, String key) {
		String value = extractString(json, key);
		try {
			return Integer.parseInt(value);
		}
		catch (Exception ex) {
			return 0;
		}
	}

	private static String extractString(String json, String key) {
		int index = json.indexOf("\"" + key + "\":");
		if (index < 0) {
			return "";
		}
		index = json.indexOf('"', index + key.length() + 3);
		if (index < 0) {
			return "";
		}
		index += 1;
		StringBuilder out = new StringBuilder();
		boolean escaped = false;
		for (int pos = index; pos < json.length(); pos++) {
			char ch = json.charAt(pos);
			if (escaped) {
				out.append(ch);
				escaped = false;
				continue;
			}
			if (ch == '\\') {
				escaped = true;
				continue;
			}
			if (ch == '"') {
				return out.toString();
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static String extractNested(String json, String objectKey, String fieldKey) {
		int index = json.indexOf("\"" + objectKey + "\":");
		if (index < 0) {
			return "";
		}
		int end = json.indexOf('}', index);
		if (end < 0) {
			return "";
		}
		return extractString(json.substring(index, end), fieldKey);
	}

	public record Repo(String provider, String owner, String name) {
	}

	public record PullRequest(int number, String title, String url, String head, String base, String state) {
	}

}
