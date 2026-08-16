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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * Sidebar panel showing connection/config state, the outcome of the last
 * send attempt, and a permanent data-disclosure notice (required by the
 * RuneLite Plugin Hub for any plugin that talks to a third-party server).
 * Read-only: this panel never triggers a game action - the only clickable
 * elements are the Discord/website links, which open the system browser via
 * {@link LinkBrowser}, same as every other RuneLite plugin.
 *
 * <p>Text comes from {@link Messages} (English/Portuguese/Spanish, picked
 * from the JVM's default locale). Colors are the GMC site's own Grand
 * Exchange trade-status palette (see {@code OsrsTradeStatusBadge.tsx} in the
 * getmastercrafter repo), reused here as accents against RuneLite's own
 * {@link ColorScheme} dark background - the panel lives inside the client's
 * theme, not the website's, so the brand color is an accent, never the
 * background.
 */
class GmcStatusPanel extends PluginPanel
{
	private static final Color ACTIVE_COLOR = new Color(0x7e, 0xd9, 0x57);
	private static final Color ALERT_COLOR = new Color(0xf8, 0x71, 0x71);
	private static final Color NEUTRAL_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color BRAND_ACCENT = new Color(0xf0, 0xc2, 0x6a);

	private static final String DISCORD_URL = "https://discord.gg/QuXyhtedRW";
	private static final String WEBSITE_URL = "https://getmastercrafter.com";

	private final StatusDot statusDot = new StatusDot();
	private final JLabel statusLabel = new JLabel();
	private final JLabel lastSendLabel = new JLabel();
	private final JLabel lastErrorLabel = new JLabel();
	private final Timer relativeTimeTimer;

	private boolean tokenConfigured;
	private boolean sendEnabled;
	private boolean hasActiveError;
	private Instant lastSuccessAt;
	private String lastSuccessSummary = Messages.get("lastSend.none");

	GmcStatusPanel()
	{
		super(false);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		lastSendLabel.setFont(FontManager.getRunescapeSmallFont());
		lastSendLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lastSendLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		lastErrorLabel.setFont(FontManager.getRunescapeSmallFont());
		lastErrorLabel.setForeground(ALERT_COLOR);
		lastErrorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.add(buildHeader());
		content.add(Box.createVerticalStrut(6));
		content.add(buildLinksRow());
		content.add(Box.createVerticalStrut(8));
		content.add(buildSeparator());
		content.add(Box.createVerticalStrut(8));
		content.add(buildStatusRow());
		content.add(Box.createVerticalStrut(6));
		content.add(withLeftMargin(lastSendLabel));
		content.add(Box.createVerticalStrut(2));
		content.add(withLeftMargin(lastErrorLabel));
		content.add(Box.createVerticalStrut(10));
		content.add(buildSeparator());
		content.add(Box.createVerticalStrut(6));
		content.add(buildDisclosure());

		add(content, BorderLayout.NORTH);

		refreshConfigState(false, false);
		refreshLastSendLabel();

		// Purely cosmetic: keeps the relative "last submission" time fresh
		// while the panel is open. Not tied to any game state or the client
		// thread.
		relativeTimeTimer = new Timer(15_000, e -> refreshLastSendLabel());
		relativeTimeTimer.start();
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		BufferedImage logo = ImageUtil.loadImageResource(GmcStatusPanel.class, "logo_header.png");
		JLabel logoLabel = new JLabel(new ImageIcon(logo));
		logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		header.add(logoLabel);

		JLabel subtitle = new JLabel("Flip Tracker");
		subtitle.setFont(FontManager.getRunescapeBoldFont());
		subtitle.setForeground(BRAND_ACCENT);
		subtitle.setHorizontalAlignment(SwingConstants.CENTER);
		subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		header.add(subtitle);

		return header;
	}

	private JPanel buildLinksRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.CENTER_ALIGNMENT);
		row.add(buildLinkIcon("discord.png", DISCORD_URL, Messages.get("tooltip.discord")));
		row.add(buildLinkIcon("website.png", WEBSITE_URL, Messages.get("tooltip.website")));
		return row;
	}

	private static JLabel buildLinkIcon(String resourceName, String url, String tooltip)
	{
		BufferedImage image = ImageUtil.loadImageResource(GmcStatusPanel.class, resourceName);
		JLabel label = new JLabel(new ImageIcon(image));
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}
		});
		return label;
	}

	private JPanel buildStatusRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		statusDot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		row.add(statusDot);

		statusLabel.setFont(FontManager.getRunescapeBoldFont());
		row.add(statusLabel);

		return row;
	}

	private static JPanel withLeftMargin(JLabel label)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.add(label, BorderLayout.CENTER);
		return wrapper;
	}

	private static JSeparator buildSeparator()
	{
		JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
		separator.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		separator.setBackground(ColorScheme.DARK_GRAY_COLOR);
		separator.setAlignmentX(Component.LEFT_ALIGNMENT);
		separator.setMaximumSize(new Dimension(PANEL_WIDTH, 1));
		return separator;
	}

	private static JLabel buildDisclosure()
	{
		JLabel disclosure = new JLabel("<html>" + Messages.get("disclosure") + "</html>");
		disclosure.setFont(FontManager.getRunescapeSmallFont());
		disclosure.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		disclosure.setVerticalAlignment(SwingConstants.TOP);
		disclosure.setAlignmentX(Component.LEFT_ALIGNMENT);
		disclosure.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 60));
		disclosure.setMaximumSize(new Dimension(PANEL_WIDTH - 20, 60));
		return disclosure;
	}

	void shutdown()
	{
		relativeTimeTimer.stop();
	}

	void refreshConfigState(boolean tokenConfigured, boolean sendEnabled)
	{
		this.tokenConfigured = tokenConfigured;
		this.sendEnabled = sendEnabled;
		// A config change (new token pasted, toggle flipped) supersedes any
		// stale error from before the change - the next send attempt will
		// re-report a fresh error if the problem persists.
		this.hasActiveError = false;
		lastErrorLabel.setText("");

		if (!tokenConfigured)
		{
			statusLabel.setText(Messages.get("status.tokenNotConfigured"));
		}
		else if (!sendEnabled)
		{
			statusLabel.setText(Messages.get("status.sendOff"));
		}
		else
		{
			statusLabel.setText(Messages.get("status.sendActive"));
		}
		updateDot();
	}

	void onSuccess(int inserted, int duplicates)
	{
		hasActiveError = false;
		lastErrorLabel.setText("");
		lastSuccessAt = Instant.now();
		lastSuccessSummary = inserted > 0
			? Messages.get("lastSend.summaryNew", inserted, duplicates)
			: Messages.get("lastSend.summaryDuplicate");
		refreshLastSendLabel();
		updateDot();
	}

	void onError(String message)
	{
		hasActiveError = true;
		lastErrorLabel.setText("<html>" + escapeHtml(message) + "</html>");
		updateDot();
	}

	private void updateDot()
	{
		Color color;
		if (hasActiveError || !tokenConfigured)
		{
			color = ALERT_COLOR;
		}
		else if (!sendEnabled)
		{
			color = NEUTRAL_COLOR;
		}
		else
		{
			color = ACTIVE_COLOR;
		}
		statusDot.setColor(color);
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
		lastSendLabel.setText("<html>" + Messages.get("lastSend.summary", relative, lastSuccessSummary) + "</html>");
	}

	private static String formatRelative(long seconds)
	{
		if (seconds < 5)
		{
			return Messages.get("relative.now");
		}
		if (seconds < 60)
		{
			return Messages.get("relative.seconds", seconds);
		}
		long minutes = seconds / 60;
		if (minutes < 60)
		{
			return Messages.get("relative.minutes", minutes);
		}
		long hours = minutes / 60;
		return Messages.get("relative.hours", hours);
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Small filled-circle status light. Custom-painted rather than a font
	 * glyph: verified via Font.canDisplay against the actual bundled
	 * RuneScape .ttf files that none of them contain a filled-circle or
	 * bullet glyph.
	 */
	private static final class StatusDot extends JComponent
	{
		private static final int DIAMETER = 10;
		private Color color = ColorScheme.LIGHT_GRAY_COLOR;

		StatusDot()
		{
			setPreferredSize(new Dimension(DIAMETER, DIAMETER));
			setMinimumSize(new Dimension(DIAMETER, DIAMETER));
			setMaximumSize(new Dimension(DIAMETER, DIAMETER));
			setOpaque(false);
		}

		void setColor(Color color)
		{
			this.color = color;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				g2.fillOval(0, (getHeight() - DIAMETER) / 2, DIAMETER, DIAMETER);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
