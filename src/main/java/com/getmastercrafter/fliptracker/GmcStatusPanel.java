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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel showing connection/config state, the outcome of the last
 * send attempt, and a permanent data-disclosure notice (required by the
 * RuneLite Plugin Hub for any plugin that talks to a third-party server).
 * Read-only: this panel never triggers a game action.
 */
class GmcStatusPanel extends PluginPanel
{
	private final JLabel tokenStateLabel = new JLabel();
	private final JLabel lastSendLabel = new JLabel();
	private final JLabel lastErrorLabel = new JLabel();
	private final Timer relativeTimeTimer;

	private Instant lastSuccessAt;
	private String lastSuccessSummary = "Nenhum envio nesta sessao ainda.";

	GmcStatusPanel()
	{
		super(false);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		JPanel content = new JPanel(new GridLayout(0, 1, 0, 8));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("GMC Flip Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		content.add(title);

		tokenStateLabel.setFont(FontManager.getRunescapeSmallFont());
		content.add(tokenStateLabel);

		lastSendLabel.setFont(FontManager.getRunescapeSmallFont());
		content.add(lastSendLabel);

		lastErrorLabel.setFont(FontManager.getRunescapeSmallFont());
		lastErrorLabel.setForeground(Color.ORANGE);
		content.add(lastErrorLabel);

		JLabel disclosure = new JLabel("<html>Este plugin envia suas transacoes da Grand Exchange "
			+ "ao GetMasterCrafter para o seu Profit Tracker.</html>");
		disclosure.setFont(FontManager.getRunescapeSmallFont());
		disclosure.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		disclosure.setVerticalAlignment(SwingConstants.TOP);
		disclosure.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 60));
		content.add(disclosure);

		add(content, BorderLayout.NORTH);

		refreshConfigState(false, false);
		refreshLastSendLabel();

		// Purely cosmetic: keeps "ha X atras" fresh while the panel is open.
		// Not tied to any game state or the client thread.
		relativeTimeTimer = new Timer(15_000, e -> refreshLastSendLabel());
		relativeTimeTimer.start();
	}

	void shutdown()
	{
		relativeTimeTimer.stop();
	}

	void refreshConfigState(boolean tokenConfigured, boolean sendEnabled)
	{
		if (!tokenConfigured)
		{
			tokenStateLabel.setText("<html>Token nao configurado.</html>");
		}
		else if (!sendEnabled)
		{
			tokenStateLabel.setText("<html>Token configurado. Envio desligado (ative nas configuracoes).</html>");
		}
		else
		{
			tokenStateLabel.setText("<html>Token configurado. Envio ativo.</html>");
		}
	}

	void onSuccess(int inserted, int duplicates)
	{
		lastSuccessAt = Instant.now();
		lastSuccessSummary = inserted > 0
			? String.format("%d nova(s), %d duplicada(s)", inserted, duplicates)
			: "sem novidade (duplicada)";
		lastErrorLabel.setText("");
		refreshLastSendLabel();
	}

	void onError(String message)
	{
		lastErrorLabel.setText("<html>" + escapeHtml(message) + "</html>");
	}

	private void refreshLastSendLabel()
	{
		if (lastSuccessAt == null)
		{
			lastSendLabel.setText("<html>" + lastSuccessSummary + "</html>");
			return;
		}
		long seconds = ChronoUnit.SECONDS.between(lastSuccessAt, Instant.now());
		String relative = formatRelative(seconds);
		lastSendLabel.setText(String.format("<html>Ultimo envio: %s (%s)</html>", relative, lastSuccessSummary));
	}

	private static String formatRelative(long seconds)
	{
		if (seconds < 5)
		{
			return "agora mesmo";
		}
		if (seconds < 60)
		{
			return seconds + "s atras";
		}
		long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + "min atras";
		}
		long hours = minutes / 60;
		return hours + "h atras";
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
