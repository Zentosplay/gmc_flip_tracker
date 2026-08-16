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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

public class TradeEventTranslatorTest
{
	private static final String CHARACTER = "Iron Bob";
	private static final int SLOT = 3;
	private static final long COMPLETION_MILLIS = 1_700_000_000_000L;

	@Test
	public void inProgressBuyingProducesNoEvent()
	{
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.BUYING, 314, 100, 40, 200, 8_000);
		assertFalse(translate(offer).isPresent());
	}

	@Test
	public void inProgressSellingProducesNoEvent()
	{
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.SELLING, 314, 100, 40, 200, 8_000);
		assertFalse(translate(offer).isPresent());
	}

	@Test
	public void emptyProducesNoEvent()
	{
		FakeGrandExchangeOffer offer = new FakeGrandExchangeOffer(GrandExchangeOfferState.EMPTY, 0, 0, 0, 0, 0);
		assertFalse(translate(offer).isPresent());
	}

	@Test
	public void boughtProducesCompletedBuyWithAveragedPrice()
	{
		// 100 units, offer price 200/ea, but actually cleared at an average
		// of 190/ea (spent 19000 for 100 units) - unitPrice must reflect
		// what actually left the wallet, not the listed offer price.
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.BOUGHT, 314, 100, 100, 200, 19_000);
		PluginTradeEvent event = translate(offer).orElseThrow(AssertionError::new);

		assertEquals(314, event.getItemId());
		assertEquals("buy", event.getType());
		assertEquals("completed", event.getStatus());
		assertEquals(100, event.getFilledQuantity());
		assertEquals(190, event.getUnitPrice());
		assertEquals(COMPLETION_MILLIS, event.getOccurredAt());
	}

	@Test
	public void soldProducesCompletedSell()
	{
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.SOLD, 8007, 50, 50, 1_000, 50_000);
		PluginTradeEvent event = translate(offer).orElseThrow(AssertionError::new);

		assertEquals(8007, event.getItemId());
		assertEquals("sell", event.getType());
		assertEquals("completed", event.getStatus());
		assertEquals(50, event.getFilledQuantity());
		assertEquals(1_000, event.getUnitPrice());
	}

	@Test
	public void cancelledBuyWithNoFillFallsBackToListedPrice()
	{
		// Cancelled before anything filled: spent/quantitySold would be a
		// division by zero, and the contract requires unitPrice > 0 even
		// here, so the listed offer price is used instead.
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.CANCELLED_BUY, 1511, 28, 0, 120, 0);
		PluginTradeEvent event = translate(offer).orElseThrow(AssertionError::new);

		assertEquals("buy", event.getType());
		assertEquals("cancelled", event.getStatus());
		assertEquals(0, event.getFilledQuantity());
		assertEquals(120, event.getUnitPrice());
	}

	@Test
	public void cancelledSellWithPartialFillUsesAveragedPrice()
	{
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.CANCELLED_SELL, 4151, 10, 3, 500, 1_530);
		PluginTradeEvent event = translate(offer).orElseThrow(AssertionError::new);

		assertEquals("sell", event.getType());
		assertEquals("cancelled", event.getStatus());
		assertEquals(3, event.getFilledQuantity());
		assertEquals(510, event.getUnitPrice());
	}

	@Test
	public void idempotencyKeyIsNonEmptyAndStable()
	{
		FakeGrandExchangeOffer offer =
			new FakeGrandExchangeOffer(GrandExchangeOfferState.BOUGHT, 314, 100, 100, 200, 19_000);
		PluginTradeEvent first = translate(offer).orElseThrow(AssertionError::new);
		PluginTradeEvent second = translate(offer).orElseThrow(AssertionError::new);

		assertTrue(first.getIdempotencyKey().length() >= 8);
		assertEquals(first.getIdempotencyKey(), second.getIdempotencyKey());
	}

	private static Optional<PluginTradeEvent> translate(FakeGrandExchangeOffer offer)
	{
		return TradeEventTranslator.translate(offer, SLOT, CHARACTER, COMPLETION_MILLIS);
	}
}
