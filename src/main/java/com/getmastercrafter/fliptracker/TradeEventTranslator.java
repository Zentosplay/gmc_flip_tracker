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

import java.util.Optional;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Translates a RuneLite {@link GrandExchangeOffer} snapshot into the GMC
 * ingestion contract's event shape. Only ever produces an event for the four
 * terminal offer states (BOUGHT, SOLD, CANCELLED_BUY, CANCELLED_SELL) - never
 * for EMPTY or the in-progress BUYING/SELLING states, since the contract has
 * no concept of a "partial"/"pending" row (a partial fill is represented as a
 * cancelled offer with filledQuantity &gt; 0, captured at the moment of
 * cancellation, not at every incremental fill).
 *
 * <p>Deciding <em>whether</em> a given terminal snapshot has already been
 * reported (dedup across repeated event fires) is the caller's responsibility
 * - this class is a pure, stateless mapping and is unit tested as such.
 */
final class TradeEventTranslator
{
	private TradeEventTranslator()
	{
	}

	static Optional<PluginTradeEvent> translate(GrandExchangeOffer offer, int slot, String characterName,
		long completionMillis)
	{
		GrandExchangeOfferState state = offer.getState();
		String type;
		String status;
		switch (state)
		{
			case BOUGHT:
				type = "buy";
				status = "completed";
				break;
			case SOLD:
				type = "sell";
				status = "completed";
				break;
			case CANCELLED_BUY:
				type = "buy";
				status = "cancelled";
				break;
			case CANCELLED_SELL:
				type = "sell";
				status = "cancelled";
				break;
			case EMPTY:
			case BUYING:
			case SELLING:
			default:
				// Not a terminal state - nothing to report yet.
				return Optional.empty();
		}

		int filledQuantity = offer.getQuantitySold();
		// Average execution price (total gp actually exchanged / quantity
		// actually filled) reflects what really left/entered the wallet,
		// which is what a profit tracker needs - this can differ from the
		// offer's listed getPrice() when the GE clears at a better price
		// than requested. Falls back to the listed price only when nothing
		// filled at all (e.g. a buy cancelled before any match), where
		// spent/quantitySold would be a division by zero and the contract
		// still requires unitPrice > 0.
		int unitPrice = filledQuantity > 0
			? (int) Math.round(offer.getSpent() / (double) filledQuantity)
			: offer.getPrice();

		String idempotencyKey = IdempotencyKeyGenerator.generate(
			characterName, offer.getItemId(), slot, type, status, filledQuantity, unitPrice, completionMillis);

		return Optional.of(new PluginTradeEvent(
			offer.getItemId(), type, status, filledQuantity, unitPrice, completionMillis, idempotencyKey));
	}
}
