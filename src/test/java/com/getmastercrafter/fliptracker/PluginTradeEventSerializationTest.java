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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

/**
 * Direct guard against the contract's biggest silent-breakage risk: a wrong
 * JSON key name compiles fine, passes every other test, and only fails once
 * a real request hits the live server. Uses a plain {@code new Gson()}
 * (default field-name serialization) rather than the injected RuneLite
 * instance, since no DI container is available in a unit test and default
 * Gson behavior for a plain POJO with primitive/String fields is identical
 * to RuneLiteAPI.GSON's.
 */
public class PluginTradeEventSerializationTest
{
	@Test
	public void serializesWithExactContractKeyNames()
	{
		PluginTradeEvent event = new PluginTradeEvent(314, "buy", "completed", 100, 190, 1_700_000_000_000L, "abc123");

		JsonObject json = new Gson().toJsonTree(event).getAsJsonObject();

		assertEquals(7, json.size());
		assertTrue(json.has("itemId"));
		assertTrue(json.has("type"));
		assertTrue(json.has("status"));
		assertTrue(json.has("filledQuantity"));
		assertTrue(json.has("unitPrice"));
		assertTrue(json.has("occurredAt"));
		assertTrue(json.has("idempotencyKey"));

		assertEquals(314, json.get("itemId").getAsInt());
		assertEquals("buy", json.get("type").getAsString());
		assertEquals("completed", json.get("status").getAsString());
		assertEquals(100, json.get("filledQuantity").getAsInt());
		assertEquals(190, json.get("unitPrice").getAsInt());
		assertEquals(1_700_000_000_000L, json.get("occurredAt").getAsLong());
		assertEquals("abc123", json.get("idempotencyKey").getAsString());
	}
}
