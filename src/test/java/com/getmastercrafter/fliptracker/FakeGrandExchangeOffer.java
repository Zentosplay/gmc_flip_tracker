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

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Hand-written test double for {@link GrandExchangeOffer} - deliberately not
 * using a mocking library, to keep test dependencies at exactly what the
 * RuneLite example-plugin template already ships (JUnit 4 only).
 */
final class FakeGrandExchangeOffer implements GrandExchangeOffer
{
	private final int quantitySold;
	private final int itemId;
	private final int totalQuantity;
	private final int price;
	private final int spent;
	private final GrandExchangeOfferState state;

	FakeGrandExchangeOffer(GrandExchangeOfferState state, int itemId, int totalQuantity, int quantitySold,
		int price, int spent)
	{
		this.state = state;
		this.itemId = itemId;
		this.totalQuantity = totalQuantity;
		this.quantitySold = quantitySold;
		this.price = price;
		this.spent = spent;
	}

	@Override
	public int getQuantitySold()
	{
		return quantitySold;
	}

	@Override
	public int getItemId()
	{
		return itemId;
	}

	@Override
	public int getTotalQuantity()
	{
		return totalQuantity;
	}

	@Override
	public int getPrice()
	{
		return price;
	}

	@Override
	public int getSpent()
	{
		return spent;
	}

	@Override
	public GrandExchangeOfferState getState()
	{
		return state;
	}
}
