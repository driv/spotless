/*
 * Copyright 2016-2020 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.spotless.maven.generic;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.maven.plugins.annotations.Parameter;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.maven.FormatterStepConfig;
import com.diffplug.spotless.maven.FormatterStepFactory;
import com.diffplug.spotless.npm.NpmPathResolver;
import com.diffplug.spotless.npm.PrettierConfig;
import com.diffplug.spotless.npm.PrettierFormatterStep;

public class Prettier implements FormatterStepFactory {

	public static final String ERROR_MESSAGE_ONLY_ONE_CONFIG = "must specify exactly one prettierVersion, devDependencies or devDependencyProperties";

	@Parameter
	private String prettierVersion;

	@Parameter
	private Map<String, String> devDependencies;

	@Parameter
	private Properties devDependencyProperties;

	@Parameter
	private Map<String, String> config;

	@Parameter
	private String configFile;

	@Parameter
	private String npmExecutable;

	@Parameter
	private String npmrc;

	@Override
	public FormatterStep newFormatterStep(FormatterStepConfig stepConfig) {

		// check if config is only setup in one way
		if (moreThanOneNonNull(this.prettierVersion, this.devDependencies, this.devDependencyProperties)) {
			throw onlyOneConfig();
		}
		if (devDependencies == null) {
			devDependencies = PrettierFormatterStep.defaultDevDependencies(); // fallback
		}

		if (prettierVersion != null && !prettierVersion.isEmpty()) {
			this.devDependencies = PrettierFormatterStep.defaultDevDependenciesWithPrettier(prettierVersion);
		} else if (devDependencyProperties != null) {
			this.devDependencies = dependencyPropertiesAsMap();
		}

		File npm = npmExecutable != null ? stepConfig.getFileLocator().locateFile(npmExecutable) : null;

		File npmrcFile = npmrc != null ? stepConfig.getFileLocator().locateFile(npmrc) : null;

		// process config file or inline config
		File configFileHandler;
		if (this.configFile != null) {
			configFileHandler = stepConfig.getFileLocator().locateFile(this.configFile);
		} else {
			configFileHandler = null;
		}

		Map<String, Object> configInline;
		if (config != null) {
			configInline = config.entrySet().stream()
					.map(entry -> {
						try {
							Integer value = Integer.parseInt(entry.getValue());
							return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
						} catch (NumberFormatException ignore) {
							// ignored
						}
						if (Boolean.TRUE.toString().equalsIgnoreCase(entry.getValue()) || Boolean.FALSE.toString().equalsIgnoreCase(entry.getValue())) {
							return new AbstractMap.SimpleEntry<>(entry.getKey(), Boolean.parseBoolean(entry.getValue()));
						}
						return entry;
					})
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
		} else {
			configInline = null;
		}

		// create the format step
		PrettierConfig prettierConfig = new PrettierConfig(configFileHandler, configInline);
		File buildDir = stepConfig.getFileLocator().getBuildDir();
		NpmPathResolver npmPathResolver = new NpmPathResolver(npm, npmrcFile, stepConfig.getFileLocator().getBaseDir());
		return PrettierFormatterStep.create(devDependencies, stepConfig.getProvisioner(), buildDir, npmPathResolver, prettierConfig);
	}

	private boolean moreThanOneNonNull(Object... objects) {
		return Arrays.stream(objects)
				.filter(Objects::nonNull)
				.filter(o -> !(o instanceof String) || !((String) o).isEmpty()) // if it is a string, it should not be empty
				.count() > 1;
	}

	private Map<String, String> dependencyPropertiesAsMap() {
		return this.devDependencyProperties.stringPropertyNames()
				.stream()
				.map(name -> new AbstractMap.SimpleEntry<>(name, this.devDependencyProperties.getProperty(name)))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private static IllegalArgumentException onlyOneConfig() {
		return new IllegalArgumentException(ERROR_MESSAGE_ONLY_ONE_CONFIG);
	}
}
