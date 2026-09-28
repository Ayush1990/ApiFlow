package com.apiflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import com.apiflow.store.FileStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ScriptModuleLoader {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ScriptModuleLoader() {
	}

	public static void install(Context context, FileStore store, String collectionId) {
		install(context, store, collectionId, true);
	}

	public static void install(Context context, FileStore store, String collectionId, boolean developerMode) {
		Map<String, ModuleSource> modules = load(store, collectionId, developerMode);
		Value bindings = context.getBindings("js");
		Map<String, Value> cache = new LinkedHashMap<>();
		context.eval("js", """
				var __module = { exports: {} };
				var __exports = __module.exports;
				var __cache = {};
				function __resolve(name, modules) {
				  if (__cache[name]) return __cache[name];
				  if (!modules[name]) throw new Error('Module not found: ' + name);
				  __module = { exports: {} };
				  __exports = __module.exports;
				  modules[name]();
				  __cache[name] = __module.exports;
				  return __cache[name];
				}
				function require(name) {
				  if (typeof name !== 'string') throw new Error('Module name required');
				  var key = name;
				  if (key.startsWith('./') || key.startsWith('../')) key = key.split('/').pop().replace(/\\.js$/, '');
				  if (key.includes('/')) key = key.split('/')[0];
				  return __resolve(key, __modules);
				}
				var __modules = {};
				""");
		for (Map.Entry<String, ModuleSource> entry : modules.entrySet()) {
			String wrapped = wrapCommonJs(entry.getValue().source());
			context.eval("js", "__modules['" + entry.getKey() + "'] = function() {\n" + wrapped + "\n};");
		}
	}

	private static Map<String, ModuleSource> load(FileStore store, String collectionId, boolean developerMode) {
		Map<String, ModuleSource> modules = new LinkedHashMap<>();
		for (Map.Entry<String, String> builtin : ScriptBuiltins.modules().entrySet()) {
			modules.put(builtin.getKey(), new ModuleSource(builtin.getValue()));
		}
		loadDir(store.root().resolve("scripts"), modules, "");
		if (developerMode) {
			loadNodeModules(store.root().resolve("scripts").resolve("node_modules"), modules);
		}
		if (collectionId != null && !collectionId.isBlank()) {
			Path scriptsRoot = store.root().resolve("collections").resolve(collectionId).resolve("scripts");
			loadDir(scriptsRoot, modules, "");
			loadTree(scriptsRoot, modules, "");
		}
		return modules;
	}

	private static void loadDir(Path dir, Map<String, ModuleSource> modules, String prefix) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.js")) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (name.endsWith(".js")) {
					String key = prefix.isBlank() ? name.substring(0, name.length() - 3) : prefix + "/" + name.substring(0, name.length() - 3);
					modules.put(key, new ModuleSource(Files.readString(file, StandardCharsets.UTF_8)));
					if (prefix.isBlank()) {
						modules.putIfAbsent(name.substring(0, name.length() - 3), modules.get(key));
					}
				}
			}
		}
		catch (IOException ignored) {
			// Ignore unreadable script directories.
		}
	}

	private static void loadTree(Path root, Map<String, ModuleSource> modules, String prefix) {
		if (!Files.isDirectory(root)) {
			return;
		}
		try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
			for (Path child : children) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				String folder = child.getFileName().toString();
				String nextPrefix = prefix.isBlank() ? folder : prefix + "/" + folder;
				loadDir(child, modules, nextPrefix);
				loadTree(child, modules, nextPrefix);
			}
		}
		catch (IOException ignored) {
			// Ignore unreadable script directories.
		}
	}

	private static void loadNodeModules(Path nodeModules, Map<String, ModuleSource> modules) {
		if (!Files.isDirectory(nodeModules)) {
			return;
		}
		try (DirectoryStream<Path> packages = Files.newDirectoryStream(nodeModules)) {
			for (Path packageDir : packages) {
				if (!Files.isDirectory(packageDir) || packageDir.getFileName().toString().startsWith(".")) {
					continue;
				}
				String name = packageDir.getFileName().toString();
				Path entry = resolvePackageEntry(packageDir);
				if (entry != null && Files.exists(entry)) {
					modules.put(name, new ModuleSource(Files.readString(entry, StandardCharsets.UTF_8)));
				}
			}
		}
		catch (IOException ignored) {
			// Ignore unreadable node_modules.
		}
	}

	private static Path resolvePackageEntry(Path packageDir) throws IOException {
		Path packageJson = packageDir.resolve("package.json");
		if (Files.exists(packageJson)) {
			JsonNode root = MAPPER.readTree(packageJson.toFile());
			String main = root.path("main").asText("");
			if (!main.isBlank()) {
				Path candidate = packageDir.resolve(main);
				if (Files.exists(candidate)) {
					return candidate;
				}
			}
			String module = root.path("module").asText("");
			if (!module.isBlank()) {
				Path candidate = packageDir.resolve(module);
				if (Files.exists(candidate)) {
					return candidate;
				}
			}
		}
		for (String candidate : new String[] { "index.js", "main.js", "dist/index.js", "lib/index.js" }) {
			Path file = packageDir.resolve(candidate);
			if (Files.exists(file)) {
				return file;
			}
		}
		return null;
	}

	private static String wrapCommonJs(String source) {
		return source + "\n;if (typeof module !== 'undefined') { __module.exports = module.exports; }";
	}

	private record ModuleSource(String source) {
	}

}
