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

package net.fabricmc.loom.decompilers.fernflower;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;

public class FabricForkedFFExecutor extends AbstractForkedFFExecutor {
	public static void main(String[] args) {
		AbstractForkedFFExecutor.decompile(args, new FabricForkedFFExecutor());
	}

	@Override
	public void runFF(Map<String, Object> options, List<File> libraries, File input, File output, File lineMap, File mappings) {
		if (mappings.exists()) options.put(IFabricJavadocProvider.PROPERTY_NAME, new JavadocProvider(mappings));

		IResultSaver saver = new ThreadSafeResultSaver(() -> output, () -> lineMap);
		IFernflowerLogger logger = new ThreadIDFFLogger(System.out, System.err, false);
		Fernflower ff = new Fernflower(FernFlowerUtils::getBytecode, saver, options, logger, getThreads(options));

		for (File library : libraries) {
			ff.addLibrary(library);
		}

		ff.addSource(input);
		logger.writeMessage("Decompiling jar...", Severity.INFO);
		ff.decompileContext();
	}

	private int getThreads(Map<String, Object> options) {
		Object threads = options.get(IFernflowerPreferences.THREADS);

		if (threads instanceof String) {
			try {
				return Integer.parseInt((String) threads);
			} catch (NumberFormatException e) {
				System.err.println("Invalid number of threads specified: " + threads);
			}
		} else if (threads instanceof Number) {
			return ((Number) threads).intValue();
		}

		return 0;
	}
}
