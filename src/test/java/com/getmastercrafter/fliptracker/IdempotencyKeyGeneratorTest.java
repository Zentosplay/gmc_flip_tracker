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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdempotencyKeyGeneratorTest
{
	private static String baseline()
	{
		return IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "buy", "completed", 100, 190, 1_700_000_000_000L);
	}

	@Test
	public void sameInputsProduceSameKey()
	{
		String first = baseline();
		String second =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "buy", "completed", 100, 190, 1_700_000_000_000L);
		assertEquals(first, second);
	}

	@Test
	public void keyLooksLikeAHexDigestWithinContractBounds()
	{
		String key = baseline();
		assertTrue("key should be at least 8 chars per contract", key.length() >= 8);
		assertTrue("key should be at most 200 chars per contract", key.length() <= 200);
		assertTrue("key should be lowercase hex", key.matches("[0-9a-f]+"));
	}

	@Test
	public void differentCharacterNameProducesDifferentKey()
	{
		// The critical rule: two characters on the same GMC account must
		// never collide, since the server's uniqueness is per-account.
		String bob = IdempotencyKeyGenerator.generate(
			"Iron Bob", 314, 3, "buy", "completed", 100, 190, 1_700_000_000_000L);
		String alice = IdempotencyKeyGenerator.generate(
			"Iron Alice", 314, 3, "buy", "completed", 100, 190, 1_700_000_000_000L);
		assertNotEquals(bob, alice);
	}

	@Test
	public void differentItemIdProducesDifferentKey()
	{
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 999, 3, "buy", "completed", 100, 190, 1_700_000_000_000L);
		assertNotEquals(baseline(), changed);
	}

	@Test
	public void differentSlotProducesDifferentKey()
	{
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 7, "buy", "completed", 100, 190, 1_700_000_000_000L);
		assertNotEquals(baseline(), changed);
	}

	@Test
	public void differentTypeProducesDifferentKey()
	{
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "sell", "completed", 100, 190, 1_700_000_000_000L);
		assertNotEquals(baseline(), changed);
	}

	@Test
	public void differentStatusProducesDifferentKey()
	{
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "buy", "cancelled", 100, 190, 1_700_000_000_000L);
		assertNotEquals(baseline(), changed);
	}

	@Test
	public void differentFilledQuantityProducesDifferentKey()
	{
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "buy", "completed", 55, 190, 1_700_000_000_000L);
		assertNotEquals(baseline(), changed);
	}

	@Test
	public void differentUnitPriceProducesDifferentKey()
	{
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "buy", "completed", 100, 250, 1_700_000_000_000L);
		assertNotEquals(baseline(), changed);
	}

	@Test
	public void differentCompletionMomentProducesDifferentKey()
	{
		// Same character/item/slot/qty/price repeated twice in the same
		// session (e.g. two identical flips back to back) must still get
		// distinct keys, or the second would be silently dropped as a
		// "duplicate" of the first.
		String changed =
			IdempotencyKeyGenerator.generate("Iron Bob", 314, 3, "buy", "completed", 100, 190, 1_700_000_005_000L);
		assertNotEquals(baseline(), changed);
	}
}
