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

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Reads the local player's own Grand Exchange offers and reports completed
 * or cancelled trades to the GetMasterCrafter Profit Tracker. Strictly
 * passive: never clicks, types, or otherwise acts on the player's behalf -
 * it only reads {@link GrandExchangeOfferChanged} and the local player's
 * name, and makes outbound HTTP calls.
 */
@Slf4j
@PluginDescriptor(
	name = "GMC Flip Tracker",
	description = "Envia suas transacoes da Grand Exchange ao GetMasterCrafter para o seu Profit Tracker",
	tags = {"grand exchange", "flipping", "tracker", "getmastercrafter", "profit"}
)
public class GmcFlipTrackerPlugin extends Plugin
{
	// RuneLite display names can carry a non-breaking space instead of a
	// regular one - normalized in normalizedPlayerName() so the
	// idempotencyKey is stable regardless of which one the client reports.
	private static final char NBSP = '\u00A0';

	@Inject
	private Client client;

	@Inject
	private GmcFlipTrackerConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private GmcApiClient apiClient;

	private GmcStatusPanel panel;
	private NavigationButton navButton;

	/**
	 * Per-slot memory of the last terminal offer snapshot already handled,
	 * so that repeated GrandExchangeOfferChanged fires for an unchanged
	 * completed/cancelled offer (RuneLite re-announces uncollected offers on
	 * login, for example) don't re-translate or re-submit. Session-scoped
	 * only - never persisted to disk. See TradeEventTranslator and
	 * IdempotencyKeyGenerator javadoc for the full strategy and its
	 * documented restart-edge-case limitation.
	 */
	private final Map<Integer, SlotSnapshot> slotMemory = new HashMap<>();

	private final GmcApiClient.ResultListener resultListener = new GmcApiClient.ResultListener()
	{
		@Override
		public void onSuccess(PluginTradeEvent event, int inserted, int duplicates)
		{
			log.debug("gmc-flip-tracker: submitted trade itemId={} inserted={} duplicates={}",
				event.getItemId(), inserted, duplicates);
			SwingUtilities.invokeLater(() -> panel.onSuccess(inserted, duplicates));
		}

		@Override
		public void onAuthError(PluginTradeEvent event, String message)
		{
			log.warn("gmc-flip-tracker: auth error submitting trade: {}", message);
			SwingUtilities.invokeLater(() -> panel.onError("Token invalido ou revogado - gere um novo no site."));
		}

		@Override
		public void onRejected(PluginTradeEvent event, String message)
		{
			SwingUtilities.invokeLater(() -> panel.onError("Erro ao enviar (ver logs do RuneLite): " + message));
		}

		@Override
		public void onGaveUp(PluginTradeEvent event, String message)
		{
			SwingUtilities.invokeLater(() -> panel.onError("Sem conexao com o GetMasterCrafter: " + message));
		}
	};

	@Override
	protected void startUp() throws Exception
	{
		slotMemory.clear();

		panel = new GmcStatusPanel();
		panel.refreshConfigState(isTokenConfigured(), config.sendEnabled());

		BufferedImage icon = ImageUtil.loadImageResource(GmcFlipTrackerPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GMC Flip Tracker")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		panel.shutdown();
		apiClient.shutdown();
		slotMemory.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"gmc-flip-tracker".equals(event.getGroup()))
		{
			return;
		}
		panel.refreshConfigState(isTokenConfigured(), config.sendEnabled());
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		int slot = event.getSlot();
		GrandExchangeOfferState state = offer.getState();

		if (state == GrandExchangeOfferState.EMPTY)
		{
			// Offer collected/reset - forget this slot so a future, genuinely
			// different trade in the same slot is treated as new.
			slotMemory.remove(slot);
			return;
		}

		if (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING)
		{
			// In progress - GrandExchangeOfferChanged fires repeatedly as the
			// offer fills; the contract has no "partial"/"pending" status, so
			// there is nothing to report until a terminal state is reached.
			slotMemory.put(slot, SlotSnapshot.inProgress());
			return;
		}

		// Terminal state: BOUGHT, SOLD, CANCELLED_BUY, or CANCELLED_SELL.
		SlotSnapshot previous = slotMemory.get(slot);
		SlotSnapshot current = SlotSnapshot.terminal(state, offer.getQuantitySold(), offer.getPrice());
		if (current.equals(previous))
		{
			// Same terminal snapshot already handled for this slot (e.g. a
			// redundant re-announcement) - skip, no network call.
			return;
		}

		// New terminal snapshot: capture the completion moment once, here,
		// so retries of this same event reuse it and produce the same
		// idempotencyKey. See IdempotencyKeyGenerator javadoc.
		long completionMillis = System.currentTimeMillis();
		slotMemory.put(slot, current);

		if (!config.sendEnabled() || !isTokenConfigured())
		{
			return;
		}

		String characterName = normalizedPlayerName();
		if (characterName == null)
		{
			log.debug("gmc-flip-tracker: no local player name available, skipping trade");
			return;
		}

		Optional<PluginTradeEvent> translated =
			TradeEventTranslator.translate(offer, slot, characterName, completionMillis);
		translated.ifPresent(tradeEvent -> apiClient.submit(tradeEvent, resultListener));
	}

	private boolean isTokenConfigured()
	{
		String token = config.apiToken();
		return token != null && !token.trim().isEmpty();
	}

	private String normalizedPlayerName()
	{
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			return null;
		}
		String rawName = client.getLocalPlayer().getName();
		String name = rawName.replace(NBSP, ' ').trim();
		return name.isEmpty() ? null : name;
	}

	@Provides
	GmcFlipTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GmcFlipTrackerConfig.class);
	}

	/**
	 * Immutable snapshot used to detect whether a terminal
	 * GrandExchangeOfferChanged fire represents genuinely new information for
	 * a slot, or a redundant re-announcement of an already-handled offer.
	 */
	private static final class SlotSnapshot
	{
		private final GrandExchangeOfferState state;
		private final int quantitySold;
		private final int price;

		private SlotSnapshot(GrandExchangeOfferState state, int quantitySold, int price)
		{
			this.state = state;
			this.quantitySold = quantitySold;
			this.price = price;
		}

		static SlotSnapshot inProgress()
		{
			return new SlotSnapshot(null, -1, -1);
		}

		static SlotSnapshot terminal(GrandExchangeOfferState state, int quantitySold, int price)
		{
			return new SlotSnapshot(state, quantitySold, price);
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof SlotSnapshot))
			{
				return false;
			}
			SlotSnapshot other = (SlotSnapshot) o;
			return quantitySold == other.quantitySold && price == other.price && state == other.state;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(state, quantitySold, price);
		}
	}
}
