/*
 * Copyright (c) 2026, Zentosplay
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.getmastercrafter.fliptracker;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Panel/status text in English, Portuguese, or Spanish, picked from the JVM's
 * default locale ({@code messages_pt.properties} / {@code messages_es.properties}
 * / the base {@code messages.properties} for anything else, including plain
 * English) - this is exactly {@link ResourceBundle}'s built-in fallback chain,
 * no custom locale-matching logic needed. The plugin config screen (RuneLite
 * {@code @ConfigItem} annotations) is intentionally not part of this: annotation
 * values are compile-time constants and can't be swapped at runtime, so that
 * screen stays English-only by design.
 */
final class Messages
{
	private static final String BUNDLE_NAME = "com.getmastercrafter.fliptracker.messages";
	private static final ResourceBundle BUNDLE =
		ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault(), Messages.class.getClassLoader());

	private Messages()
	{
	}

	static String get(String key)
	{
		return BUNDLE.getString(key);
	}

	static String get(String key, Object... args)
	{
		return new MessageFormat(BUNDLE.getString(key), Locale.getDefault()).format(args);
	}
}
