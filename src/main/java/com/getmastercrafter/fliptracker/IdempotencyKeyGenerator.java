/*
 * Copyright (c) 2026, GetMasterCrafter
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Builds a deterministic idempotencyKey for a single completed/cancelled GE
 * offer. The server's uniqueness constraint is UNIQUE(user_id, idempotencyKey)
 * - scoped per GMC account, not per character or per plugin token - so a key
 * built only from GE-local data (slot/item/qty/price) could collide between
 * two characters on the same account. The character name is therefore always
 * the first component of the key.
 *
 * <p>The caller supplies completionMillis: a timestamp captured once, the
 * first time a given terminal offer snapshot is observed (see
 * GmcFlipTrackerPlugin's per-slot memory). Reusing that same captured value
 * across retries keeps the key stable for a given real trade within a plugin
 * session; a fresh plugin session re-observing an already-completed,
 * uncollected offer will mint a new key (a known, documented limitation - see
 * README "Known limitations").
 */
final class IdempotencyKeyGenerator
{
	// A NUL-code field separator: it cannot appear in a RuneLite character
	// name or in any of the other (numeric/fixed-enum) fields, so two
	// different logical inputs can never concatenate into the same string.
	private static final String SEP = new String(new char[] { 0 });

	private IdempotencyKeyGenerator()
	{
	}

	static String generate(String characterName, int itemId, int slot, String type, String status,
		int filledQuantity, int unitPrice, long completionMillis)
	{
		String canonical = characterName + SEP + itemId + SEP + slot + SEP + type + SEP + status
			+ SEP + filledQuantity + SEP + unitPrice + SEP + completionMillis;
		return sha256Hex(canonical);
	}

	private static String sha256Hex(String input)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			// SHA-256 is guaranteed available on every JDK implementation (JLS).
			throw new AssertionError("SHA-256 not available", e);
		}
	}
}
