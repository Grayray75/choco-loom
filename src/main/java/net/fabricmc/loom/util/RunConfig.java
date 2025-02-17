/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.gradle.api.Project;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import net.fabricmc.loom.AbstractPlugin;
import net.fabricmc.loom.LoomGradleExtension;

public class RunConfig {
	public String configName;
	public String eclipseProjectName;
	public String ideaModuleName;
	public String mainClass;
	public String runDir;
	public String vmArgs;
	public String programArgs;

	public Element genRuns(Element doc) throws IOException, ParserConfigurationException, TransformerException {
		Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
		root = addXml(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

		this.addXml(root, "module", ImmutableMap.of("name", ideaModuleName));
		this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
		this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDir));

		if (!Strings.isNullOrEmpty(vmArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", vmArgs));
		}

		if (!Strings.isNullOrEmpty(programArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", programArgs));
		}

		return root;
	}

	public Element addXml(Node parent, String name, Map<String, String> values) {
		Document doc = parent.getOwnerDocument();

		if (doc == null) {
			doc = (Document) parent;
		}

		Element e = doc.createElement(name);

		for (Map.Entry<String, String> entry : values.entrySet()) {
			e.setAttribute(entry.getKey(), entry.getValue());
		}

		parent.appendChild(e);
		return e;
	}

	private static String getIdeaModuleName(Project project) {
		String module = project.getName() + ".main";

		while ((project = project.getParent()) != null) {
			module = project.getName() + "." + module;
		}

		return module;
	}

	private static void populate(Project project, LoomGradleExtension extension, RunConfig runConfig, String mode) {
		if (!AbstractPlugin.isRootProject(project)) runConfig.configName += " (" + project.getPath() + ')';
		runConfig.eclipseProjectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();
		runConfig.ideaModuleName = getIdeaModuleName(project);
		runConfig.runDir = "file://$PROJECT_DIR$/" + extension.runDir;

		switch (extension.getLoaderLaunchMethod()) {
		case "launchwrapper": {
			runConfig.mainClass = "net.minecraft.launchwrapper.Launch";
			StringBuilder programArgs = new StringBuilder("--tweakClass ").append("client".equals(mode) ? Constants.DEFAULT_FABRIC_CLIENT_TWEAKER : Constants.DEFAULT_FABRIC_SERVER_TWEAKER);

			// if installer.json found...
			JsonObject installerJson = extension.getInstallerJson();
			if (installerJson != null && installerJson.has("launchwrapper")) {// copy launchwrapper tweakers
				JsonObject launchwrapperJson = installerJson.getAsJsonObject("launchwrapper");

				if (launchwrapperJson.has("tweakers")) {
					JsonObject tweakersJson = launchwrapperJson.getAsJsonObject("tweakers");

					for (String s : Arrays.asList(mode, "common")) {
						if (tweakersJson.has(s)) {
							for (JsonElement element : tweakersJson.getAsJsonArray(s)) {
								programArgs.append(" --tweakClass ").append(element.getAsString());
							}
						}
					}
				}
			}

			programArgs.append(" --assetIndex \"").append(extension.getMinecraftProvider().getAssetIndex().getFabricId(extension.getMinecraftProvider().minecraftVersion)).append('"');
			programArgs.append(" --assetsDir \"").append(new File(extension.getUserCache(), "assets").getAbsolutePath()).append('"');
			runConfig.programArgs = programArgs.toString();
			runConfig.vmArgs = "-Dfabric.development=true";
			break;
		}
		case "knot":
			runConfig.mainClass = getMainClass(mode, extension);
			runConfig.programArgs = "--assetIndex \"" + extension.getMinecraftProvider().getAssetIndex().getFabricId(extension.getMinecraftProvider().minecraftVersion) + '"'
								+ " --assetsDir \"" + new File(extension.getUserCache(), "assets").getAbsolutePath() + '"';
			runConfig.vmArgs = "-Dfabric.development=true";
			break;
		default:
			runConfig.mainClass = "net.fabricmc.devlaunchinjector.Main";
			runConfig.programArgs = "";
			runConfig.vmArgs = "-Dfabric.dli.config=" + encodeEscaped(extension.getDevLauncherConfig().getAbsolutePath()) + " -Dfabric.dli.env=" + mode.toLowerCase() + " -Dfabric.dli.main=" + getMainClass(mode, extension);
			break;
		}
	}

	public static RunConfig clientRunConfig(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		RunConfig ideaClient = new RunConfig();
		ideaClient.configName = "Minecraft Client";
		populate(project, extension, ideaClient, "client");
		ideaClient.vmArgs += getOSClientJVMArgs(extension);

		return ideaClient;
	}

	public static RunConfig serverRunConfig(Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		RunConfig ideaServer = new RunConfig();
		ideaServer.configName = "Minecraft Server";
		populate(project, extension, ideaServer, "server");
		ideaServer.vmArgs += " -Dfabric.dli.main=" + getMainClass("server", extension);

		return ideaServer;
	}

	//This can be removed at somepoint, its not ideal but its the best solution I could thing of
	public static boolean needsUpgrade(File file) throws IOException {
		String contents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		return !contents.contains("net.fabricmc.devlaunchinjector.Main");
	}

	public String fromDummy(String dummy) throws IOException {
		String dummyConfig;

		try (InputStream input = SetupIntelijRunConfigs.class.getClassLoader().getResourceAsStream(dummy)) {
			dummyConfig = IOUtils.toString(input, StandardCharsets.UTF_8);
		}

		dummyConfig = dummyConfig.replace("%NAME%", configName);
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
		dummyConfig = dummyConfig.replace("%ECLIPSE_PROJECT%", eclipseProjectName);
		dummyConfig = dummyConfig.replace("%IDEA_MODULE%", ideaModuleName);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", programArgs.replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", vmArgs.replaceAll("\"", "&quot;"));

		return dummyConfig;
	}

	public static String getOSClientJVMArgs(LoomGradleExtension extension) {
		if (OperatingSystem.ACTIVE == OperatingSystem.OSX && !extension.hasLWJGL2()) {
			return " -XstartOnFirstThread";
		}

		return "";
	}

	private static String getMainClass(String side, LoomGradleExtension extension) {
		JsonObject installerJson = extension.getInstallerJson();

		if (installerJson != null && installerJson.has("mainClass")) {
			JsonElement mainClassJson = installerJson.get("mainClass");

			String mainClassName = "";

			if (mainClassJson.isJsonObject()) {
				JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

				if (mainClassesJson.has(side)) {
					mainClassName = mainClassesJson.get(side).getAsString();
				}
			} else {
				mainClassName = mainClassJson.getAsString();
			}

			return mainClassName;
		}

		// Fallback to default class names, happens when in a loader dev env
		if ("launchwrapper".equals(extension.getLoaderLaunchMethod())) {
			return "net.minecraft.launchwrapper.Launch";
		}

		return "net.fabricmc.loader.launch.knot.Knot" + side.substring(0, 1).toUpperCase(Locale.ROOT) + side.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String encodeEscaped(String s) {
		StringBuilder ret = new StringBuilder();

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == '@' && i > 0 && s.charAt(i - 1) == '@' || c == ' ') {
				ret.append(String.format("@@%04x", (int) c));
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}
}
