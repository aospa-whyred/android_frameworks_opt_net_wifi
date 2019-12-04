/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import static android.net.wifi.WifiManager.DEVICE_MOBILITY_STATE_STATIONARY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.wifi.WifiChannelUtilization.CACHE_UPDATE_INTERVAL_MIN_MS;
import static com.android.server.wifi.WifiChannelUtilization.CHANNEL_STATS_CACHE_SIZE;
import static com.android.server.wifi.WifiChannelUtilization.RADIO_ON_TIME_DIFF_MIN_MS;
import static com.android.server.wifi.util.InformationElementUtil.BssLoad.INVALID;
import static com.android.server.wifi.util.InformationElementUtil.BssLoad.MAX_CHANNEL_UTILIZATION;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.validateMockitoUsage;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiLinkLayerStats.ChannelStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.WifiChannelUtilization}.
 */
@SmallTest
public class WifiChannelUtilizationTest extends WifiBaseTest {
    private WifiChannelUtilization mWifiChannelUtilization;
    @Mock private Clock mClock;
    /**
     * Called before each test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiChannelUtilization = new WifiChannelUtilization(mClock);
        mWifiChannelUtilization.init(null);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void verifyEmptyLinkLayerStats() throws Exception {
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(null);
        assertEquals(INVALID, mWifiChannelUtilization.getUtilizationRatio(5180));
    }

    @Test
    public void verifyEmptyChanStatsMap() throws Exception {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats);
        assertEquals(INVALID, mWifiChannelUtilization.getUtilizationRatio(5180));
    }

    @Test
    public void verifyOneReadChanStatsWithShortRadioOnTime() throws Exception {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs = new ChannelStats();
        cs.frequency = freq;
        cs.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 2;
        cs.ccaBusyTimeMs = 2;
        llstats.channelStatsMap.put(freq, cs);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats);
        assertEquals(INVALID, mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyOneReadChanStatsWithLongRadioOnTime() throws Exception {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs = new ChannelStats();
        cs.frequency = freq;
        cs.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS + 1;
        cs.ccaBusyTimeMs = 20;
        llstats.channelStatsMap.put(freq, cs);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats);
        assertEquals(cs.ccaBusyTimeMs * MAX_CHANNEL_UTILIZATION / cs.radioOnTimeMs,
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyTwoReadChanStatsRefWithLast() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);
        long currentTimeStamp = 1 + CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        WifiLinkLayerStats llstats2 = new WifiLinkLayerStats();
        ChannelStats cs2 = new ChannelStats();
        cs2.frequency = freq;
        cs2.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 2 + 1;
        cs2.ccaBusyTimeMs = 30;
        llstats2.channelStatsMap.put(freq, cs2);
        currentTimeStamp = 1 + CACHE_UPDATE_INTERVAL_MIN_MS * 2;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats2);

        assertEquals((cs2.ccaBusyTimeMs - cs1.ccaBusyTimeMs) * MAX_CHANNEL_UTILIZATION
                / (cs2.radioOnTimeMs - cs1.radioOnTimeMs),
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyTwoReadChanStatsRefWithZero() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS + 1;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);
        long currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS / 2;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        WifiLinkLayerStats llstats2 = new WifiLinkLayerStats();
        ChannelStats cs2 = new ChannelStats();
        cs2.frequency = freq;
        cs2.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 2 + 2;
        cs2.ccaBusyTimeMs = 30;
        llstats2.channelStatsMap.put(freq, cs2);
        currentTimeStamp = 1 + CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats2);

        assertEquals(cs2.ccaBusyTimeMs * MAX_CHANNEL_UTILIZATION / cs2.radioOnTimeMs,
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyThreeReadChanStatsRefWithSecondLast() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 2;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);
        long currentTimeStamp = 1 + CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        WifiLinkLayerStats llstats2 = new WifiLinkLayerStats();
        ChannelStats cs2 = new ChannelStats();
        cs2.frequency = freq;
        cs2.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS + 4;
        cs2.ccaBusyTimeMs = 30;
        llstats2.channelStatsMap.put(freq, cs2);
        currentTimeStamp = 2 + CACHE_UPDATE_INTERVAL_MIN_MS * 2;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats2);

        WifiLinkLayerStats llstats3 = new WifiLinkLayerStats();
        ChannelStats cs3 = new ChannelStats();
        cs3.frequency = freq;
        cs3.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 3 / 2 + 4;
        cs3.ccaBusyTimeMs = 70;
        llstats3.channelStatsMap.put(freq, cs3);
        currentTimeStamp = 3 + CACHE_UPDATE_INTERVAL_MIN_MS * 3;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats3);

        if (CHANNEL_STATS_CACHE_SIZE > 1) {
            assertEquals((cs3.ccaBusyTimeMs - cs1.ccaBusyTimeMs) * MAX_CHANNEL_UTILIZATION
                    / (cs3.radioOnTimeMs - cs1.radioOnTimeMs),
                    mWifiChannelUtilization.getUtilizationRatio(freq));
        } else {
            assertEquals(cs3.ccaBusyTimeMs * MAX_CHANNEL_UTILIZATION / cs3.radioOnTimeMs,
                    mWifiChannelUtilization.getUtilizationRatio(freq));
        }
    }

    @Test
    public void verifyThreeReadChanStatsRefWithLast() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 4;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);
        long currentTimeStamp = 1 + CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        WifiLinkLayerStats llstats2 = new WifiLinkLayerStats();
        ChannelStats cs2 = new ChannelStats();
        cs2.frequency = freq;
        cs2.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 2;
        cs2.ccaBusyTimeMs = 30;
        llstats2.channelStatsMap.put(freq, cs2);
        currentTimeStamp = 2 + CACHE_UPDATE_INTERVAL_MIN_MS * 2;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats2);

        WifiLinkLayerStats llstats3 = new WifiLinkLayerStats();
        ChannelStats cs3 = new ChannelStats();
        cs3.frequency = freq;
        cs3.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 3;
        cs3.ccaBusyTimeMs = 70;
        llstats3.channelStatsMap.put(freq, cs3);
        currentTimeStamp = 3 + CACHE_UPDATE_INTERVAL_MIN_MS * 3;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats3);

        assertEquals((cs3.ccaBusyTimeMs - cs2.ccaBusyTimeMs) * MAX_CHANNEL_UTILIZATION
                / (cs3.radioOnTimeMs - cs2.radioOnTimeMs),
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyThreeReadChanStatsFirstTwoReadAreTooRecent() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 4;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);

        long currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS / 4;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS / 2;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        WifiLinkLayerStats llstats3 = new WifiLinkLayerStats();
        ChannelStats cs3 = new ChannelStats();
        cs3.frequency = freq;
        cs3.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 3;
        cs3.ccaBusyTimeMs = 70;
        llstats3.channelStatsMap.put(freq, cs3);
        currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats3);

        assertEquals(cs3.ccaBusyTimeMs * MAX_CHANNEL_UTILIZATION / cs3.radioOnTimeMs,
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyThreeReadChanStatsInitAfterOneRead() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 4;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);

        long currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS + 1;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        mWifiChannelUtilization.init(llstats1);
        WifiLinkLayerStats llstats2 = new WifiLinkLayerStats();
        ChannelStats cs2 = new ChannelStats();
        cs2.frequency = freq;
        cs2.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 2 + 1;
        cs2.ccaBusyTimeMs = 40;
        currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS * 2 + 1;
        llstats2.channelStatsMap.put(freq, cs2);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats2);

        assertEquals(INVALID, mWifiChannelUtilization.getUtilizationRatio(freq));

        WifiLinkLayerStats llstats3 = new WifiLinkLayerStats();
        ChannelStats cs3 = new ChannelStats();
        cs3.frequency = freq;
        cs3.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 3 + 1;
        cs3.ccaBusyTimeMs = 70;
        llstats3.channelStatsMap.put(freq, cs3);
        currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats3);

        assertEquals((cs3.ccaBusyTimeMs - cs2.ccaBusyTimeMs) * MAX_CHANNEL_UTILIZATION
                / (cs3.radioOnTimeMs - cs2.radioOnTimeMs),
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyThreeReadChanStatsAlwaysStationary() throws Exception {
        mWifiChannelUtilization.setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        cs1.frequency = freq;
        cs1.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 4;
        cs1.ccaBusyTimeMs = 20;
        llstats1.channelStatsMap.put(freq, cs1);

        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        WifiLinkLayerStats llstats3 = new WifiLinkLayerStats();
        ChannelStats cs3 = new ChannelStats();
        cs3.frequency = freq;
        cs3.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 2;
        cs3.ccaBusyTimeMs = 70;
        llstats3.channelStatsMap.put(freq, cs3);
        long currentTimeStamp = CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats3);

        assertEquals(cs3.ccaBusyTimeMs * MAX_CHANNEL_UTILIZATION / cs3.radioOnTimeMs,
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifyThreeReadChanStatsStationaryAfterFirstRead() throws Exception {
        WifiLinkLayerStats llstats1 = new WifiLinkLayerStats();
        int freq = 5180;
        ChannelStats cs1 = new ChannelStats();
        llstats1.channelStatsMap.put(freq, cs1);
        long currentTimeStamp = 0;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats1);

        mWifiChannelUtilization.setDeviceMobilityState(DEVICE_MOBILITY_STATE_STATIONARY);

        WifiLinkLayerStats llstats2 = new WifiLinkLayerStats();
        ChannelStats cs2 = new ChannelStats();
        cs2.frequency = freq;
        cs2.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS / 4;
        cs2.ccaBusyTimeMs = 20;
        llstats2.channelStatsMap.put(freq, cs2);
        currentTimeStamp = 1 + CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats2);

        WifiLinkLayerStats llstats3 = new WifiLinkLayerStats();
        ChannelStats cs3 = new ChannelStats();
        cs3.frequency = freq;
        cs3.radioOnTimeMs = RADIO_ON_TIME_DIFF_MIN_MS * 2;
        cs3.ccaBusyTimeMs = 70;
        llstats3.channelStatsMap.put(freq, cs3);
        currentTimeStamp = 5 + CACHE_UPDATE_INTERVAL_MIN_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiChannelUtilization.refreshChannelStatsAndChannelUtilization(llstats3);

        assertEquals((cs3.ccaBusyTimeMs - cs2.ccaBusyTimeMs) * MAX_CHANNEL_UTILIZATION
                / (cs3.radioOnTimeMs - cs2.radioOnTimeMs),
                mWifiChannelUtilization.getUtilizationRatio(freq));
    }

    @Test
    public void verifySetGetUtilizationRatio() throws Exception {
        int freq = 5180;
        int utilizationRatio = 24;
        mWifiChannelUtilization.setUtilizationRatio(freq, utilizationRatio);
        assertEquals(utilizationRatio, mWifiChannelUtilization.getUtilizationRatio(freq));
    }
}