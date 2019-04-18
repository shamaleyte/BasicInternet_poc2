package hypelabs.com.hypepubsub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private HypePubSub hps = HypePubSub.getInstance();
    private Network network = Network.getInstance();
    private HypeSdkInterface hypeSdk = HypeSdkInterface.getInstance();
    private UIData uiData = UIData.getInstance();

    private Button subscribeButton;
    private Button unsubscribeButton;
    private Button publishButton;
    private Button checkOwnIdButton;
    private Button checkHypeDevicesButton;
    private Button checkOwnSubscriptionsButton;
    private Button checkManagedServicesButton;


    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private List<String> cachedMessages = new ArrayList<>();
    private String[] messages = new String[]{"BasicInternet Messages"};
    private List<Map<String, List<String>>> channelMessages = new ArrayList<Map<String, List<String>>>();
    private Map<String, List<String>> channelMessenger = new HashMap<String, List<String>>();//This is one instance of the  map you want to store in the above list.

    private List<String> messagesList = new ArrayList<>(Arrays.asList(messages));
    ArrayAdapter<String> adapter;
    private ListView mListView;


    private static final String TAG = HypePubSub.class.getName();
    private static final String HYPE_PUB_SUB_LOG_PREFIX = HpsConstants.LOG_PREFIX + "<BasicInternet> ";
    private static final String CHANNEL_MESSAGES = "messages";
    private static final String SUBSCRIBED_CHANNELS = "channels";
    private static final String MESSAGE_HYPE_STARTED = "hypeStarted";
    private static final String BROADCAST_ALL_MSGS = "broadcast_all_msgs";
    private static final String MESSAGE_RECEIVED = "message";

    private static MainActivity instance; // Way of accessing the application context from other classes

    public MainActivity() {
        instance = this;
    }


    public static Context getContext() {
        return instance;
    }

    // To get "Published" messages from the HypePubSub Class.
    IntentFilter mIntentFilter;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, String.format("%s BroadcastReceiver()", HYPE_PUB_SUB_LOG_PREFIX));
            Bundle b = intent.getExtras();
            try {
                if (b.getString(MESSAGE_RECEIVED) != null) {

                    String res = b.getString(MESSAGE_RECEIVED);
                    Log.i(TAG, String.format("%s BroadCast1 Received for MESSAGE : " + res, HYPE_PUB_SUB_LOG_PREFIX));


                    /* If the received message is already in the list, do not keep it */
                    if (messagesList.contains(res))
                        Log.i(TAG, String.format("%s Received message already exists : " + res, HYPE_PUB_SUB_LOG_PREFIX));
                    else {
                        /*CachedMessages list holds both its own published messages + messages from other devices
                         * In order to not add the message that the device itself published twice, we are checking if the message for the channel already exists or not.
                         * If it does exist, then no need to add it once again.
                         */
                        processRawMessage(res); // IT adds the messages to the related channel stack

                        if (!cachedMessages.contains(res))
                            cachedMessages.add(res);
                        messagesList.add(res);
                        Log.i(TAG, String.format("%s messageList is updated at the code level.", HYPE_PUB_SUB_LOG_PREFIX));
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                            Log.i(TAG, String.format("%s Main ListView is being UPDATED!!! : ", HYPE_PUB_SUB_LOG_PREFIX));
                        } else
                            Log.i(TAG, String.format("%s Main ListView is NULL and CANNOT be updated :(", HYPE_PUB_SUB_LOG_PREFIX));

                    }
                }
                if (b.getString(MESSAGE_HYPE_STARTED) != null) {
                    Log.i(TAG, String.format("%s BroadCast2 Received for HypeStatus", HYPE_PUB_SUB_LOG_PREFIX));
                    reSubscribeToAllChannels();
                }
                if (b.getString(BROADCAST_ALL_MSGS) != null) {
                    Log.i(TAG, String.format("%s BroadCast3 Received for Broadcasting ALL messages to the subscribers", HYPE_PUB_SUB_LOG_PREFIX));
                    broadcastAllMessages();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in Broadcast Receiver as " + e.getMessage());
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, String.format("%s onCreate()", HYPE_PUB_SUB_LOG_PREFIX));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initButtonsFromResourceIDs();
        setButtonListeners();


        /* Set up Adapter for the UI's list view */
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, messagesList);
        mListView.setAdapter(adapter);

        /* Catch the broadcast message */
        getTheBroadcastMessage();
        getHypeStatus();
        getBroadcastAllMessagesRequest();

        /* Initialize Pref. */
        Context context = MainActivity.getContext();
        prefs = context.getSharedPreferences(CHANNEL_MESSAGES, Context.MODE_PRIVATE);


        if (uiData.isToInitializeSdk) {
            initHypeSdk();
            uiData.isToInitializeSdk = false;
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, String.format("%s onStop()", HYPE_PUB_SUB_LOG_PREFIX));
        try {
            unregisterReceiver(mIntentReceiver);
        } catch (Exception e) {
            System.out.println("<<<<exception>>>>");
        }
        super.onStop();
    }


    @Override
    protected void onResume() {
        Log.i(TAG, String.format("%s onResume()", HYPE_PUB_SUB_LOG_PREFIX));
        super.onResume();
        loadMessagesFromPrefs();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, String.format("%s onPause()", HYPE_PUB_SUB_LOG_PREFIX));
        super.onPause();
        storeMessagesToPrefs();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i(TAG, String.format("%s onSaveInstanceState()", HYPE_PUB_SUB_LOG_PREFIX));
        /* Save your work here */
        storeChannelsToPrefs();
    }

    private void initHypeSdk() {
        requestHypeRequiredPermissions(this);
    }

    public void requestHypeRequiredPermissions(Activity activity) {

        // Request AccessCoarseLocation permissions if the Android version of the device
        // requires it. Otherwise it starts the Hype SDK immediately. If the permissions are
        // requested the framework only starts if the permissions are granted (see
        // MainActivity.onRequestPermissionsResult()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    HpsConstants.REQUEST_ACCESS_COARSE_LOCATION_ID);
        } else {
            HypeSdkInterface hypeSdkInterface = HypeSdkInterface.getInstance();
            hypeSdkInterface.requestHypeToStart(getApplicationContext());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case HpsConstants.REQUEST_ACCESS_COARSE_LOCATION_ID:
                // If the permission is not granted the Hype SDK starts but BLE transport
                // will not be active. Regardless of this, this callback must be implemented
                // because the Hype SDK should only start after the permission request being
                // concluded.
                HypeSdkInterface hypeSdkInterface = HypeSdkInterface.getInstance();
                hypeSdkInterface.requestHypeToStart(getApplicationContext());
                break;
        }
    }

    private void setButtonListeners() {
        setListenerSubscribeButton();
        setListenerUnsubscribeButton();
        setListenerPublishButton();
        setListenerCheckOwnIdButton();
        setListenerCheckHypeDevicesButton();
        setListenerCheckOwnSubscriptionsButton();
        setListenerCheckManagedServicesButton();
    }

    private void initButtonsFromResourceIDs() {
        subscribeButton = findViewById(R.id.activity_main_subscribe_button);
        unsubscribeButton = findViewById(R.id.activity_main_unsubscribe_button);
        publishButton = findViewById(R.id.activity_main_publish_button);
        checkOwnIdButton = findViewById(R.id.activity_main_check_own_id_button);
        checkHypeDevicesButton = findViewById(R.id.activity_main_check_hype_devices_button);
        checkOwnSubscriptionsButton = findViewById(R.id.activity_main_check_own_subscriptions_button);
        checkManagedServicesButton = findViewById(R.id.activity_main_check_managed_services_button);
        mListView = findViewById(R.id.newsCollector);
    }


    private void getTheBroadcastMessage() {
        // Create Intent Filter
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("Published_Message");
        registerReceiver(mIntentReceiver, mIntentFilter);
    }

    private void getHypeStatus() {
        // Create Intent Filter
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("Hype_Status");
        registerReceiver(mIntentReceiver, mIntentFilter);
    }

    private void getBroadcastAllMessagesRequest() {
        // Create Intent Filter
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("Broadcast_All_Msgs");
        registerReceiver(mIntentReceiver, mIntentFilter);
    }
//////////////////////////////////////////////////////////////////////////////
// Button Listener Methods
//////////////////////////////////////////////////////////////////////////////


    private void setListenerSubscribeButton() {
        subscribeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }

                displayServicesNamesList("Subscribe",
                        "Select a service to subscribe",
                        uiData.getUnsubscribedServicesAdapter(MainActivity.this),
                        new subscribeServiceAction(),
                        true);
            }
        });
    }

    private void setListenerUnsubscribeButton() {
        unsubscribeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }
                if (isNoServiceSubscribed()) {
                    showNoServicesSubscribedDialog();
                    return;
                }

                displayServicesNamesList("Unsubscribe",
                        "Select a service to unsubscribe",
                        uiData.getSubscribedServicesAdapter(MainActivity.this),
                        new unsubscribeServiceAction(),
                        false);
            }
        });
    }

    private void setListenerPublishButton() {
        publishButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }

                displayServicesNamesList("Publish",
                        "Select a service in which to publish",
                        uiData.getAvailableServicesAdapter(MainActivity.this),
                        new publishServiceAction(),
                        true);
            }
        });
    }

    private void setListenerCheckOwnIdButton() {
        checkOwnIdButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }


                AlertDialogUtils.showInfoDialog(MainActivity.this, "Own Device",
                        HpsGenericUtils.getInstanceAnnouncementStr(network.ownClient.instance) + "\n"
                                + HpsGenericUtils.getIdStringFromClient(network.ownClient) + "\n"
                                + HpsGenericUtils.getKeyStringFromClient(network.ownClient));


            }
        });
    }

    private void setListenerCheckHypeDevicesButton() {
        final Intent intent = new Intent(this, ClientsListActivity.class);

        checkHypeDevicesButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }

                startActivity(intent);
            }
        });
    }

    private void setListenerCheckOwnSubscriptionsButton() {
        final Intent intent = new Intent(this, SubscriptionsListActivity.class);

        checkOwnSubscriptionsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }
                if (isNoServiceSubscribed()) {
                    showNoServicesSubscribedDialog();
                    return;
                }

                startActivity(intent);
            }
        });
    }

    private void setListenerCheckManagedServicesButton() {
        final Intent intent = new Intent(this, ServiceManagersListActivity.class);

        checkManagedServicesButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isHypeSdkReady()) {
                    showHypeNotReadyDialog();
                    return;
                }
                MainActivity.this.
                        startActivity(intent);
            }
        });
    }

//////////////////////////////////////////////////////////////////////////////
// User Action Processing Methods
//////////////////////////////////////////////////////////////////////////////

    private void displayServicesNamesList(String title,
                                          String message,
                                          ListAdapter serviceNamesAdapter,
                                          final IServiceAction serviceAction,
                                          Boolean isNewServiceSelectionAllowed) {
        final ListView listView = new ListView(MainActivity.this);
        listView.setAdapter(serviceNamesAdapter);

        LinearLayout layout = new LinearLayout(MainActivity.this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(listView);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setView(layout);
        builder.setMessage(message);
        builder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

        if (isNewServiceSelectionAllowed) {
            builder.setNeutralButton("New Service",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            processNewServiceSelection(serviceAction);
                            dialog.dismiss();
                        }
                    });
        }

        final Dialog dialog = builder.create();
        dialog.show();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String listItem = (String) listView.getItemAtPosition(position);
                serviceAction.action(listItem);
                dialog.dismiss();
            }
        });
    }

    private interface IServiceAction {
        void action(String userInput);
    }

    private class subscribeServiceAction implements IServiceAction {
        @Override
        public void action(String userInput) {
            String serviceName = processUserServiceNameInput(userInput);
            if (serviceName.length() == 0) {
                return;
            }

            if (hps.ownSubscriptions.containsSubscriptionWithServiceName(serviceName)) {
                AlertDialogUtils.showInfoDialog(MainActivity.this,
                        "INFO", "Service already subscribed");
                return;
            }

            Log.i(TAG, String.format("%s subscribeServiceAction ...()", HYPE_PUB_SUB_LOG_PREFIX));
            boolean wasSubscribed = hps.issueSubscribeReq(serviceName);
            if (wasSubscribed) {
                uiData.addSubscribedService(MainActivity.this, serviceName);
                uiData.removeUnsubscribedService(MainActivity.this, serviceName);
            }
        }
    }


    private void manuallySubscribe(String serviceName) {
        Log.i(TAG, String.format("%s manuallySubscribe()", HYPE_PUB_SUB_LOG_PREFIX));
        if (hps != null) {
            Log.i(TAG, String.format("%s hps object not NULL - continue", HYPE_PUB_SUB_LOG_PREFIX));
            boolean wasSubscribed = hps.issueSubscribeReq(serviceName);
            if (wasSubscribed) {
                Log.i(TAG, String.format("%s wasSubscribed TRUE", HYPE_PUB_SUB_LOG_PREFIX));
                uiData.addSubscribedService(MainActivity.this, serviceName);
                uiData.removeUnsubscribedService(MainActivity.this, serviceName);
                hps.addChannelMessages(serviceName, channelMessenger.get(serviceName));

            }

            // Add related channel message to the Demo's subscription message list
            //TODO
        } else {
            Log.i(TAG, String.format("%s hbs is NULL ()", HYPE_PUB_SUB_LOG_PREFIX));
        }
    }

    private class unsubscribeServiceAction implements IServiceAction {
        @Override
        public void action(String userInput) {
            String serviceName = processUserServiceNameInput(userInput);
            if (serviceName.length() == 0) {
                return;
            }

            boolean wasUnsubscribed = hps.issueUnsubscribeReq(serviceName);
            if (wasUnsubscribed) {
                uiData.addUnsubscribedService(MainActivity.this, serviceName);
                uiData.removeSubscribedService(MainActivity.this, serviceName);
            }
        }
    }

    private class publishServiceAction implements IServiceAction {
        @Override
        public void action(String userInput) {
            final String serviceName = processUserServiceNameInput(userInput);
            if (serviceName.length() == 0) {
                return;
            }

            AlertDialogUtils.ISingleInputDialog publishMsgInput = new AlertDialogUtils.ISingleInputDialog() {

                @Override
                public void onOk(String msg) {
                    msg = msg.trim();
                    if (msg.length() == 0) {
                        AlertDialogUtils.showInfoDialog(MainActivity.this,
                                "WARNING",
                                "A message must be specified");
                        return;
                    }

                    hps.issuePublishReq(serviceName, msg);
                    String pair = serviceName + ";" + msg;
                    addMessageToChannel(msg, serviceName);
                    cachedMessages.add(pair);
                }

                @Override
                public void onCancel() {
                }
            };

            AlertDialogUtils.showSingleInputDialog(MainActivity.this,
                    "Publish",
                    "Insert message to publish in the service: " + serviceName,
                    "message",
                    publishMsgInput);
        }
    }

    private void processNewServiceSelection(final IServiceAction serviceAction) {
        AlertDialogUtils.ISingleInputDialog newServiceInput = new AlertDialogUtils.ISingleInputDialog() {

            @Override
            public void onOk(String input) {
                String serviceName = processUserServiceNameInput(input);
                uiData.addAvailableService(MainActivity.this, serviceName);
                uiData.addUnsubscribedService(MainActivity.this, serviceName);
                serviceAction.action(serviceName);
            }

            @Override
            public void onCancel() {
            }
        };

        AlertDialogUtils.showSingleInputDialog(MainActivity.this,
                "New Service",
                "Specify new service",
                "service",
                newServiceInput);
    }

//////////////////////////////////////////////////////////////////////////////
// Utilities
//////////////////////////////////////////////////////////////////////////////

    private boolean isHypeSdkReady() {
        if (!hypeSdk.hasHypeFailed && !hypeSdk.hasHypeStopped && hypeSdk.hasHypeStarted) {
            return true;
        }
        return false;
    }

    private void showHypeNotReadyDialog() {
        if (hypeSdk.hasHypeFailed) {
            AlertDialogUtils.showInfoDialog(MainActivity.this,
                    "Error", "Hype SDK could not be started.\n" + hypeSdk.hypeFailedMsg);
        } else if (hypeSdk.hasHypeStopped) {
            AlertDialogUtils.showInfoDialog(MainActivity.this,
                    "Error", "Hype SDK stopped.\n" + hypeSdk.hypeStoppedMsg);
        } else if (!hypeSdk.hasHypeStarted) {
            AlertDialogUtils.showInfoDialog(MainActivity.this,
                    "Warning", "Hype SDK is not ready yet.");
        }
    }

    private boolean isNoServiceSubscribed() {
        return uiData.getNumberOfSubscribedServices() == 0;
    }

    private void showNoServicesSubscribedDialog() {
        AlertDialogUtils.showInfoDialog(MainActivity.this,
                "INFO", "No services subscribed");

    }

    static String processUserServiceNameInput(String input) {
        return input.toLowerCase().trim();
    }


    /* Data Storage Operations */
    protected void storeMessagesToPrefs() {
        Log.i(TAG, String.format("%s storeMessagesToPrefs()", HYPE_PUB_SUB_LOG_PREFIX));
        if (prefs == null) {
            return;
        }

        // store list as jsonarray
        JSONArray jsonArray = new JSONArray();
        for (String z : cachedMessages) {
            jsonArray.put(z);
            Log.i(TAG, String.format("%s Add Message from local: " + z, HYPE_PUB_SUB_LOG_PREFIX));
        }
        editor = prefs.edit();
        editor.putString(CHANNEL_MESSAGES, jsonArray.toString());
        editor.commit();
        Log.i(TAG, String.format("%s SAVED into PREF storage.", HYPE_PUB_SUB_LOG_PREFIX));
    }

    private void loadMessagesFromPrefs() {
        Log.i(TAG, String.format("%s loadDataFromPrefs()", HYPE_PUB_SUB_LOG_PREFIX));
        if (prefs == null) {
            Log.i(TAG, String.format("%s PREF  = NULL ", HYPE_PUB_SUB_LOG_PREFIX));
            return;
        } else
            Log.i(TAG, String.format("%s PREF has DATA", HYPE_PUB_SUB_LOG_PREFIX));


        // Read in json array from prefs to fill list
        cachedMessages = new ArrayList<>();
        String jsonArrayString = prefs.getString(CHANNEL_MESSAGES, "");
        Log.i(TAG, String.format("%s jsonArrayString for " + CHANNEL_MESSAGES + " is : " + jsonArrayString, HYPE_PUB_SUB_LOG_PREFIX));
        if (!TextUtils.isEmpty(jsonArrayString)) {
            try {
                JSONArray jsonArray = new JSONArray(jsonArrayString);
                if (jsonArray.length() > 0) {
                    messagesList.clear();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String next = jsonArray.getString(i);
                        Log.i(TAG, String.format("%s next : " + next, HYPE_PUB_SUB_LOG_PREFIX));
                        processRawMessage(next);
                        cachedMessages.add(next);
                        // Just add the message to the viewer list.
                        messagesList.add(next);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /* This method adds the message to the related channel's map list */
    protected void processRawMessage(String message) {
        String serviceName = "";
        String serviceMessage = "";
        String[] info = message.split(";");
        if (info.length == 2) {
            serviceName = info[0];
            serviceMessage = info[1];
        }
        addMessageToChannel(serviceMessage, serviceName);
    }

    protected void addMessageToChannel(String serviceMessage, String serviceName) {
        Log.i(TAG, String.format("%s Adding " + serviceMessage + " to the channel: " + serviceName, HYPE_PUB_SUB_LOG_PREFIX));
        if (!isDuplicateMessage(serviceMessage, serviceName)) {
            if (channelMessenger.containsKey(serviceName))
                channelMessenger.get(serviceName).add(serviceMessage);
            else {

                List<String> newList = new ArrayList<String>();
                newList.add(serviceMessage);
                channelMessenger.put(serviceName, newList);
            }
        }

    }

    protected boolean isDuplicateMessage(String serviceMessage, String serviceName) {
        if (channelMessenger.containsKey(serviceName)) {
            List<String> newList = channelMessenger.get(serviceName);
            for (int a = 0; a < newList.size(); a++) {
                if (newList.get(a).equals(serviceMessage)) {
                    Log.i(TAG, String.format("%s duplicate message for this channel - so ignore it", HYPE_PUB_SUB_LOG_PREFIX));
                    return true;
                }
            }
        }
        return false;
    }

    protected void storeChannelsToPrefs() {

        Log.i(TAG, String.format("%s App's subscription list size:  " + hps.ownSubscriptions.size(), HYPE_PUB_SUB_LOG_PREFIX));
        Log.i(TAG, String.format("%s storeChannelsToPrefs()", HYPE_PUB_SUB_LOG_PREFIX));
        if (prefs == null) {
            return;
        }

        JSONArray jsonArray = new JSONArray();
        for (int a = 0; a < hps.ownSubscriptions.size(); a++) {
            jsonArray.put(hps.ownSubscriptions.get(a).serviceName);
            Log.i(TAG, String.format("%s Add Channel : " + hps.ownSubscriptions.get(a).serviceName, HYPE_PUB_SUB_LOG_PREFIX));
        }

        editor = prefs.edit();
        editor.putString(SUBSCRIBED_CHANNELS, jsonArray.toString());
        editor.commit();
        Log.i(TAG, String.format("%s SAVED CHANNELS into PREF storage.", HYPE_PUB_SUB_LOG_PREFIX));

    }

    protected void broadcastAllMessages() {
        Log.i(TAG, String.format("%s Broadcasting all of the msgs one by one ()", HYPE_PUB_SUB_LOG_PREFIX));
        for (String next : messagesList) {

            String[] info = next.split(";");
            if (info.length == 2) {
                String channel = info[0];
                String msg = info[1];
                Log.i(TAG, String.format("%s broadcasting next msg : " + msg + " to the channel " + channel, HYPE_PUB_SUB_LOG_PREFIX));
                hps.issuePublishReq(channel, msg);
            }
        }
    }

    protected void reSubscribeToAllChannels() {
        Log.i(TAG, String.format("%s reSubscribeToAllChannels()", HYPE_PUB_SUB_LOG_PREFIX));
        if (prefs == null) {
            Log.i(TAG, String.format("%s PREF  = NULL ", HYPE_PUB_SUB_LOG_PREFIX));
            return;
        } else {
            Log.i(TAG, String.format("%s PREF has DATA", HYPE_PUB_SUB_LOG_PREFIX));
        }

        String jsonArrayString = prefs.getString(SUBSCRIBED_CHANNELS, "");
        Log.i(TAG, String.format("%s jsonArrayString of Subscribed Channels : " + jsonArrayString, HYPE_PUB_SUB_LOG_PREFIX));
        if (!TextUtils.isEmpty(jsonArrayString)) {
            try {
                JSONArray jsonArray = new JSONArray(jsonArrayString);
                if (jsonArray.length() > 0) {
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String next = jsonArray.getString(i);
                        Log.i(TAG, String.format("%s next channel: " + next, HYPE_PUB_SUB_LOG_PREFIX));
                        try {
                            //If NOT already subscribed
                            if (!hps.ownSubscriptions.containsSubscriptionWithServiceName(next))
                                manuallySubscribe(next);
                        } catch (Exception e) {
                            Log.e(TAG, String.format("%s Error occured in reSubscribeToAllChannels : " + e.getMessage(), HYPE_PUB_SUB_LOG_PREFIX));
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
