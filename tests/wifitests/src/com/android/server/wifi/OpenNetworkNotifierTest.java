/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.server.wifi.OpenNetworkNotifier.DEFAULT_REPEAT_DELAY_SEC;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.util.ArraySet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link OpenNetworkNotifier}.
 */
public class OpenNetworkNotifierTest {

    private static final String TEST_SSID_1 = "Test SSID 1";
    private static final int MIN_RSSI_LEVEL = -127;

    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private Clock mClock;
    @Mock private WifiConfigStore mWifiConfigStore;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private NotificationManager mNotificationManager;
    @Mock private WifiStateMachine mWifiStateMachine;
    @Mock private OpenNetworkRecommender mOpenNetworkRecommender;
    @Mock private ConnectToNetworkNotificationBuilder mNotificationBuilder;
    @Mock private UserManager mUserManager;
    private OpenNetworkNotifier mNotificationController;
    private TestLooper mLooper;
    private BroadcastReceiver mBroadcastReceiver;
    private ScanResult mDummyNetwork;
    private List<ScanDetail> mOpenNetworks;
    private Set<String> mBlacklistedSsids;


    /** Initialize objects before each test run. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(Context.NOTIFICATION_SERVICE))
                .thenReturn(mNotificationManager);
        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 1)).thenReturn(1);
        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY, DEFAULT_REPEAT_DELAY_SEC))
                .thenReturn(DEFAULT_REPEAT_DELAY_SEC);
        when(mContext.getSystemService(Context.USER_SERVICE))
                .thenReturn(mUserManager);
        when(mContext.getResources()).thenReturn(mResources);
        mDummyNetwork = new ScanResult();
        mDummyNetwork.SSID = TEST_SSID_1;
        mDummyNetwork.capabilities = "[ESS]";
        mDummyNetwork.level = MIN_RSSI_LEVEL;
        when(mOpenNetworkRecommender.recommendNetwork(any(), any())).thenReturn(mDummyNetwork);
        mOpenNetworks = new ArrayList<>();
        mOpenNetworks.add(new ScanDetail(mDummyNetwork, null /* networkDetail */));
        mBlacklistedSsids = new ArraySet<>();

        mLooper = new TestLooper();
        mNotificationController = new OpenNetworkNotifier(
                mContext, mLooper.getLooper(), mFrameworkFacade, mClock, mWifiConfigManager,
                mWifiConfigStore, mWifiStateMachine, mOpenNetworkRecommender, mNotificationBuilder);
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(broadcastReceiverCaptor.capture(), any(), any(), any());
        mBroadcastReceiver = broadcastReceiverCaptor.getValue();
        mNotificationController.handleScreenStateChanged(true);
    }

    /**
     * When scan results with open networks are handled, a notification is posted.
     */
    @Test
    public void handleScanResults_hasOpenNetworks_notificationDisplayed() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());
    }

    /**
     * When scan results with no open networks are handled, a notification is not posted.
     */
    @Test
    public void handleScanResults_emptyList_notificationNotDisplayed() {
        mNotificationController.handleScanResults(new ArrayList<>());

        verify(mOpenNetworkRecommender, never()).recommendNetwork(any(), any());
        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    /**
     * When a notification is showing and scan results with no open networks are handled, the
     * notification is cleared.
     */
    @Test
    public void handleScanResults_notificationShown_emptyList_notificationCleared() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.handleScanResults(new ArrayList<>());

        verify(mNotificationManager).cancel(anyInt());
    }

    /**
     * When a notification is showing and no recommendation is made for the new scan results, the
     * notification is cleared.
     */
    @Test
    public void handleScanResults_notificationShown_noRecommendation_notificationCleared() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        when(mOpenNetworkRecommender.recommendNetwork(any(), any())).thenReturn(null);
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mNotificationManager).cancel(anyInt());
    }

    /**
     * When a notification is showing, screen is off, and scan results with no open networks are
     * handled, the notification is cleared.
     */
    @Test
    public void handleScanResults_notificationShown_screenOff_emptyList_notificationCleared() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.handleScreenStateChanged(false);
        mNotificationController.handleScanResults(new ArrayList<>());

        verify(mNotificationManager).cancel(anyInt());
    }

    /**
     * When {@link OpenNetworkNotifier#clearPendingNotification(boolean)} is called and a
     * notification is shown, clear the notification.
     */
    @Test
    public void clearPendingNotification_clearsNotificationIfOneIsShowing() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.clearPendingNotification(true);

        verify(mNotificationManager).cancel(anyInt());
    }

    /**
     * When {@link OpenNetworkNotifier#clearPendingNotification(boolean)} is called and a
     * notification was not previously shown, do not clear the notification.
     */
    @Test
    public void clearPendingNotification_doesNotClearNotificationIfNoneShowing() {
        mNotificationController.clearPendingNotification(true);

        verify(mNotificationManager, never()).cancel(anyInt());
    }

    /**
     * When screen is off and notification is not displayed, notification is not posted on handling
     * new scan results with open networks.
     */
    @Test
    public void screenOff_notificationNotShowing_handleScanResults_notificationNotDisplayed() {
        mNotificationController.handleScreenStateChanged(false);
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender, never()).recommendNetwork(any(), any());
        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    /**
     * When screen is off and notification is displayed, the notification can be updated with a new
     * recommendation.
     */
    @Test
    public void screenOff_notificationShowing_handleScanResults_recommendationCanBeUpdated() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.handleScreenStateChanged(false);
        mNotificationController.handleScanResults(mOpenNetworks);

        // Recommendation made twice
        verify(mOpenNetworkRecommender, times(2)).recommendNetwork(
                mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder, times(2)).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());
    }

    /**
     * When a notification is posted and cleared without resetting delay, the next scan with open
     * networks should not post another notification.
     */
    @Test
    public void postNotification_clearNotificationWithoutDelayReset_shouldNotPostNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.clearPendingNotification(false);

        verify(mNotificationManager).cancel(anyInt());

        mNotificationController.handleScanResults(mOpenNetworks);

        // no new notification posted
        verify(mNotificationManager).notify(anyInt(), any());
    }

    /**
     * When a notification is posted and cleared without resetting delay, the next scan with open
     * networks should post a notification.
     */
    @Test
    public void postNotification_clearNotificationWithDelayReset_shouldPostNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.clearPendingNotification(true);

        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender, times(2)).recommendNetwork(
                mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder, times(2)).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());
    }

    /**
     * When user dismissed notification and there is a recommended network, network ssid should be
     * blacklisted.
     */
    @Test
    public void userDismissedNotification_shouldBlacklistNetwork() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mBroadcastReceiver.onReceive(
                mContext,
                new Intent(ConnectToNetworkNotificationBuilder.ACTION_USER_DISMISSED_NOTIFICATION));

        verify(mWifiConfigManager).saveToStore(false /* forceWrite */);

        mNotificationController.clearPendingNotification(true);
        List<ScanDetail> scanResults = mOpenNetworks;
        mNotificationController.handleScanResults(scanResults);

        Set<String> expectedBlacklist = new ArraySet<>();
        expectedBlacklist.add(mDummyNetwork.SSID);
        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, expectedBlacklist);
    }

    /**
     * When a notification is posted and cleared without resetting delay, after the delay has passed
     * the next scan with open networks should post a notification.
     */
    @Test
    public void delaySet_delayPassed_shouldPostNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.clearPendingNotification(false);

        // twice the delay time passed
        when(mClock.getWallClockMillis()).thenReturn(DEFAULT_REPEAT_DELAY_SEC * 1000L * 2);

        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender, times(2)).recommendNetwork(
                mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder, times(2)).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());
    }

    /** Verifies that {@link UserManager#DISALLOW_CONFIG_WIFI} disables the feature. */
    @Test
    public void userHasDisallowConfigWifiRestriction_notificationNotDisplayed() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, UserHandle.CURRENT))
                .thenReturn(true);

        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender, never()).recommendNetwork(any(), any());
        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    /** Verifies that {@link UserManager#DISALLOW_CONFIG_WIFI} clears the showing notification. */
    @Test
    public void userHasDisallowConfigWifiRestriction_showingNotificationIsCleared() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, UserHandle.CURRENT))
                .thenReturn(true);

        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mNotificationManager).cancel(anyInt());
    }

    /**
     * {@link ConnectToNetworkNotificationBuilder#ACTION_CONNECT_TO_NETWORK} does not connect to
     * any network if the initial notification is not showing.
     */
    @Test
    public void actionConnectToNetwork_notificationNotShowing_doesNothing() {
        mBroadcastReceiver.onReceive(mContext,
                new Intent(ConnectToNetworkNotificationBuilder.ACTION_CONNECT_TO_NETWORK));

        verify(mWifiStateMachine, never()).sendMessage(any(Message.class));
    }

    /**
     * {@link ConnectToNetworkNotificationBuilder#ACTION_CONNECT_TO_NETWORK} connects to the
     * currently recommended network if it exists.
     */
    @Test
    public void actionConnectToNetwork_currentRecommendationExists_connectsAndPostsNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        // Initial Notification
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mBroadcastReceiver.onReceive(mContext,
                new Intent(ConnectToNetworkNotificationBuilder.ACTION_CONNECT_TO_NETWORK));

        verify(mWifiStateMachine).sendMessage(any(Message.class));
        // Connecting Notification
        verify(mNotificationBuilder).createNetworkConnectingNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());
    }

    /**
     * {@link OpenNetworkNotifier#handleWifiConnected()} does not post connected notification if
     * the connecting notification is not showing
     */
    @Test
    public void networkConnectionSuccess_wasNotInConnectingFlow_doesNothing() {
        mNotificationController.handleWifiConnected();

        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    /**
     * {@link OpenNetworkNotifier#handleWifiConnected()} clears notification that is not connecting.
     */
    @Test
    public void networkConnectionSuccess_wasShowingNotification_clearsNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        // Initial Notification
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mNotificationController.handleWifiConnected();

        verify(mNotificationManager).cancel(anyInt());
    }

    /**
     * {@link OpenNetworkNotifier#handleWifiConnected()} posts the connected notification if
     * the connecting notification is showing.
     */
    @Test
    public void networkConnectionSuccess_wasInConnectingFlow_postsConnectedNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        // Initial Notification
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mBroadcastReceiver.onReceive(mContext,
                new Intent(ConnectToNetworkNotificationBuilder.ACTION_CONNECT_TO_NETWORK));

        // Connecting Notification
        verify(mNotificationBuilder).createNetworkConnectingNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());

        mNotificationController.handleWifiConnected();

        // Connected Notification
        verify(mNotificationBuilder).createNetworkConnectedNotification(mDummyNetwork);
        verify(mNotificationManager, times(3)).notify(anyInt(), any());
    }

    /**
     * {@link OpenNetworkNotifier#handleConnectionFailure()} posts the Failed to Connect
     * notification if the connecting notification is showing.
     */
    @Test
    public void networkConnectionFailure_wasNotInConnectingFlow_doesNothing() {
        mNotificationController.handleConnectionFailure();

        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    /**
     * {@link OpenNetworkNotifier#handleConnectionFailure()} posts the Failed to Connect
     * notification if the connecting notification is showing.
     */
    @Test
    public void networkConnectionFailure_wasInConnectingFlow_postsFailedToConnectNotification() {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        // Initial Notification
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mBroadcastReceiver.onReceive(mContext,
                new Intent(ConnectToNetworkNotificationBuilder.ACTION_CONNECT_TO_NETWORK));

        // Connecting Notification
        verify(mNotificationBuilder).createNetworkConnectingNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());

        mNotificationController.handleConnectionFailure();

        // Failed to Connect Notification
        verify(mNotificationBuilder).createNetworkFailedNotification();
        verify(mNotificationManager, times(3)).notify(anyInt(), any());
    }

    /**
     * When a {@link WifiManager#CONNECT_NETWORK_FAILED} is received from the connection callback
     * of {@link WifiStateMachine#sendMessage(Message)}, a Failed to Connect notification should
     * be posted. On tapping this notification, Wi-Fi Settings should be launched.
     */
    @Test
    public void connectionFailedCallback_postsFailedToConnectNotification() throws RemoteException {
        mNotificationController.handleScanResults(mOpenNetworks);

        verify(mOpenNetworkRecommender).recommendNetwork(mOpenNetworks, mBlacklistedSsids);
        // Initial Notification
        verify(mNotificationBuilder).createConnectToNetworkNotification(mDummyNetwork);
        verify(mNotificationManager).notify(anyInt(), any());

        mBroadcastReceiver.onReceive(mContext,
                new Intent(ConnectToNetworkNotificationBuilder.ACTION_CONNECT_TO_NETWORK));

        ArgumentCaptor<Message> connectMessageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mWifiStateMachine).sendMessage(connectMessageCaptor.capture());
        Message connectMessage = connectMessageCaptor.getValue();

        // Connecting Notification
        verify(mNotificationBuilder).createNetworkConnectingNotification(mDummyNetwork);
        verify(mNotificationManager, times(2)).notify(anyInt(), any());

        Message connectFailedMsg = Message.obtain();
        connectFailedMsg.what = WifiManager.CONNECT_NETWORK_FAILED;
        connectMessage.replyTo.send(connectFailedMsg);
        mLooper.dispatchAll();

        // Failed to Connect Notification
        verify(mNotificationBuilder).createNetworkFailedNotification();
        verify(mNotificationManager, times(3)).notify(anyInt(), any());

        mBroadcastReceiver.onReceive(mContext,
                new Intent(ConnectToNetworkNotificationBuilder
                        .ACTION_PICK_WIFI_NETWORK_AFTER_CONNECT_FAILURE));

        ArgumentCaptor<Intent> pickerIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(pickerIntentCaptor.capture());
        assertEquals(pickerIntentCaptor.getValue().getAction(), Settings.ACTION_WIFI_SETTINGS);
    }
}