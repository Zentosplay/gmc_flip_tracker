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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Posts a single {@link PluginTradeEvent} to the GMC ingestion endpoint
 * (POST https://getmastercrafter.com/api/osrs/plugin/trades) and retries
 * transient failures with backoff. The endpoint is idempotent per
 * idempotencyKey, so retrying the exact same event object is always safe -
 * it either inserts once or becomes a no-op "duplicate" server-side.
 *
 * <p>Never runs on the client thread: OkHttp calls are always {@code enqueue}d
 * (never {@code execute}d), and retries are scheduled on this class's own
 * single-thread executor, per RuneLite plugin threading rules.
 */
@Slf4j
class GmcApiClient
{
	private static final String ENDPOINT_URL = "https://getmastercrafter.com/api/osrs/plugin/trades";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	// Bounded retry schedule for transient failures (network errors, 429, 503).
	// Index i is the delay before retry attempt i+1. After the last entry is
	// exhausted, the event is dropped and logged - there is no on-disk queue,
	// so a trade that fails every retry within this window is lost. This is a
	// deliberate "magro" trade-off; see README "Known limitations".
	private static final long[] RETRY_DELAYS_SECONDS = {5, 20, 60};

	interface ResultListener
	{
		void onSuccess(PluginTradeEvent event, int inserted, int duplicates);

		void onAuthError(PluginTradeEvent event, String message);

		void onRejected(PluginTradeEvent event, String message);

		void onGaveUp(PluginTradeEvent event, String message);
	}

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final GmcFlipTrackerConfig config;
	private final ScheduledExecutorService retryExecutor;

	@Inject
	GmcApiClient(OkHttpClient httpClient, Gson gson, GmcFlipTrackerConfig config)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.config = config;

		ThreadFactory threadFactory = runnable ->
		{
			Thread thread = new Thread(runnable, "gmc-flip-tracker-retry");
			thread.setDaemon(true);
			return thread;
		};
		this.retryExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
	}

	void shutdown()
	{
		retryExecutor.shutdownNow();
	}

	void submit(PluginTradeEvent event, ResultListener listener)
	{
		attempt(event, listener, new AtomicInteger(0));
	}

	private void attempt(PluginTradeEvent event, ResultListener listener, AtomicInteger attemptCount)
	{
		String token = config.apiToken().trim();
		Request request = new Request.Builder()
			.url(ENDPOINT_URL)
			.header("Authorization", "Bearer " + token)
			.post(RequestBody.create(JSON, gson.toJson(event)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("gmc-flip-tracker: network failure submitting trade", e);
				scheduleRetryOrGiveUp(event, listener, attemptCount, Messages.get("fallback.noConnection"));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response r = response)
				{
					handleResponse(r, event, listener, attemptCount);
				}
			}
		});
	}

	private void handleResponse(Response response, PluginTradeEvent event, ResultListener listener,
		AtomicInteger attemptCount)
	{
		int code = response.code();
		String bodyString;
		try
		{
			bodyString = response.body() != null ? response.body().string() : "";
		}
		catch (IOException e)
		{
			log.debug("gmc-flip-tracker: failed reading response body", e);
			scheduleRetryOrGiveUp(event, listener, attemptCount, Messages.get("fallback.invalidResponse"));
			return;
		}

		if (code == 200)
		{
			int inserted = 0;
			int duplicates = 0;
			try
			{
				JsonObject json = gson.fromJson(bodyString, JsonObject.class);
				if (json != null)
				{
					inserted = json.has("inserted") ? json.get("inserted").getAsInt() : 0;
					duplicates = json.has("duplicates") ? json.get("duplicates").getAsInt() : 0;
				}
			}
			catch (JsonParseException e)
			{
				log.debug("gmc-flip-tracker: could not parse success body: {}", bodyString);
			}
			listener.onSuccess(event, inserted, duplicates);
			return;
		}

		if (code == 401)
		{
			// Format/absent token, or a token that's valid-shaped but revoked
			// or unknown - retrying will never succeed without user action.
			listener.onAuthError(event, extractError(bodyString, Messages.get("fallback.invalidToken")));
			return;
		}

		if (code == 429 || code == 503)
		{
			scheduleRetryOrGiveUp(event, listener, attemptCount,
				extractError(bodyString, Messages.get("fallback.serviceUnavailable")));
			return;
		}

		// 400 (payload/validation problem) or anything else unexpected: this
		// is a plugin bug, not a transient condition - retrying the same
		// event would just fail the same way, so log and surface it without
		// consuming a retry attempt.
		log.warn("gmc-flip-tracker: rejected event (HTTP {}): {}", code, bodyString);
		listener.onRejected(event, extractError(bodyString, Messages.get("fallback.eventsInvalid")));
	}

	private void scheduleRetryOrGiveUp(PluginTradeEvent event, ResultListener listener, AtomicInteger attemptCount,
		String lastErrorMessage)
	{
		int nextAttemptIndex = attemptCount.getAndIncrement();
		if (nextAttemptIndex >= RETRY_DELAYS_SECONDS.length)
		{
			log.warn("gmc-flip-tracker: giving up on trade event after {} attempts: {}", nextAttemptIndex,
				lastErrorMessage);
			listener.onGaveUp(event, lastErrorMessage);
			return;
		}

		long delaySeconds = RETRY_DELAYS_SECONDS[nextAttemptIndex];
		try
		{
			retryExecutor.schedule(() -> attempt(event, listener, attemptCount), delaySeconds, TimeUnit.SECONDS);
		}
		catch (java.util.concurrent.RejectedExecutionException e)
		{
			// Executor was shut down (plugin is stopping) - drop silently.
			log.debug("gmc-flip-tracker: retry executor shut down, dropping retry");
		}
	}

	private String extractError(String bodyString, String fallback)
	{
		if (bodyString == null || bodyString.isEmpty())
		{
			return fallback;
		}
		try
		{
			JsonObject json = gson.fromJson(bodyString, JsonObject.class);
			if (json != null && json.has("error"))
			{
				return json.get("error").getAsString();
			}
		}
		catch (JsonParseException e)
		{
			// fall through to fallback
		}
		return fallback;
	}
}
