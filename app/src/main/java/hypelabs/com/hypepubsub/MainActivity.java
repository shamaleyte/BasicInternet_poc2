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
import java.util.List;

public class MainActivity extends AppCompatActivity
{
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
private List<String> cachedMessages = new ArrayList<>();
String[] messages = new String[] {"BasicInternet Messages"};

private List<String> subscribedChannels = new ArrayList<>();
private List<String> messagesList = new ArrayList<>(Arrays.asList(messages));


ArrayAdapter<String> adapter;
private ListView mListView;


private static final String TAG =  HypePubSub.class.getName();
private static final String HYPE_PUB_SUB_LOG_PREFIX = HpsConstants.LOG_PREFIX + "<BasicInternet> ";
private static final String CHANNEL_MESSAGES = "messages";
private static final String SUBSCRIBED_CHANNELS = "channels";
private static final String MESSAGE_HYPE_STARTED = "hypeStarted";

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
        Log.d(TAG, String.format("%s BroadcastReceiver()", HYPE_PUB_SUB_LOG_PREFIX));
        Bundle b = intent.getExtras();
        try {
            if (b.getString("message") != null) {
                Log.d(TAG, String.format("%s BroadCast Received for MESSAGE", HYPE_PUB_SUB_LOG_PREFIX));
                String res = b.getString("message");
                cachedMessages.add(res);
                messagesList.add(res);
                if (adapter != null)
                    adapter.notifyDataSetChanged();
            }
            if(b.getString(MESSAGE_HYPE_STARTED) !=null){
                Log.d(TAG, String.format("%s BroadCast Received for HypeStatus", HYPE_PUB_SUB_LOG_PREFIX));
                reSubscribeToAllChannels();
            }
        }
        catch (Exception e){
            Log.e(TAG, "Error in Broadcast Receiver as " + e.getMessage());
        }
    }
};


@Override
protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, String.format("%s onCreate()", HYPE_PUB_SUB_LOG_PREFIX));
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initButtonsFromResourceIDs();
    setButtonListeners();


    /* Set up Adapter for the UI's list view */
    adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, messagesList);
    mListView.setAdapter(adapter);

    /* Catch the broadcast message */
    getTheBroadcastMessage();
    //getHypeStatus();

    /* Initialize Pref. */
    Context context = MainActivity.getContext();
    prefs = context.getSharedPreferences(CHANNEL_MESSAGES, Context.MODE_PRIVATE);

    if(uiData.isToInitializeSdk) {
        initHypeSdk();
        uiData.isToInitializeSdk = false;
    }
}

@Override
protected void onStop(){
    Log.d(TAG, String.format("%s onStop()", HYPE_PUB_SUB_LOG_PREFIX));
    try {
        unregisterReceiver(mIntentReceiver);
    }catch (Exception e){
        System.out.println("<<<<exception>>>>");
    }
    super.onStop();
}


@Override
protected void onResume() {
    Log.d(TAG, String.format("%s onResume()", HYPE_PUB_SUB_LOG_PREFIX));
    super.onResume();
    loadDataFromPrefs(CHANNEL_MESSAGES);
    //loadDataCustom();
}

@Override
protected void onPause() {
    Log.d(TAG, String.format("%s onPause()", HYPE_PUB_SUB_LOG_PREFIX));
    super.onPause();
    storeMessagesToPrefs();
}

@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Log.d(TAG, String.format("%s onSaveInstanceState()", HYPE_PUB_SUB_LOG_PREFIX));
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

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        activity.requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                HpsConstants.REQUEST_ACCESS_COARSE_LOCATION_ID);
    }
    else {
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



private void getTheBroadcastMessage(){
    // Create Intent Filter
    mIntentFilter = new IntentFilter();
    mIntentFilter.addAction("Published_Message");
    registerReceiver(mIntentReceiver, mIntentFilter);
}
    private void getHypeStatus(){
        // Create Intent Filter
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("Hype_Status");
        registerReceiver(mIntentReceiver, mIntentFilter);
    }
//////////////////////////////////////////////////////////////////////////////
// Button Listener Methods
//////////////////////////////////////////////////////////////////////////////



private void setListenerSubscribeButton() {
    subscribeButton.setOnClickListener(new View.OnClickListener() {

        @Override
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
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
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
                showHypeNotReadyDialog();
                return;
            }
            if( isNoServiceSubscribed()) {
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
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
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
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
                showHypeNotReadyDialog();
                return;
            }


            AlertDialogUtils.showInfoDialog(MainActivity.this,"Own Device",
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
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
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
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
                showHypeNotReadyDialog();
                return;
            }
            if( isNoServiceSubscribed()) {
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
        public void onClick(View arg0)
        {
            if( !isHypeSdkReady()) {
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
            public void onClick(DialogInterface dialog, int which) { }
        });

    if(isNewServiceSelectionAllowed) {
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

        Log.d(TAG, String.format("%s subscribeServiceAction ...()", HYPE_PUB_SUB_LOG_PREFIX));
        boolean wasSubscribed = hps.issueSubscribeReq(serviceName);
        if (wasSubscribed) {
            subscribedChannels.add(serviceName);
            uiData.addSubscribedService(MainActivity.this, serviceName);
            uiData.removeUnsubscribedService(MainActivity.this, serviceName);
        }
    }
}
private void manuallySubscribe(String serviceName){
    Log.d(TAG, String.format("%s manuallySubscribe()", HYPE_PUB_SUB_LOG_PREFIX));
    if(hps != null) {
        Log.d(TAG, String.format("%s hps object not NULL - continue", HYPE_PUB_SUB_LOG_PREFIX));
        boolean wasSubscribed = hps.issueSubscribeReq(serviceName);
        if (wasSubscribed) {
            subscribedChannels.add(serviceName);
            uiData.addSubscribedService(MainActivity.this, serviceName);
            uiData.removeUnsubscribedService(MainActivity.this, serviceName);
        }
    }
    else{
        Log.d(TAG, String.format("%s hbs is NULL ()", HYPE_PUB_SUB_LOG_PREFIX));
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
            }

            @Override
            public void onCancel() {}
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
        public void onCancel() {}
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
    if(!hypeSdk.hasHypeFailed  && !hypeSdk.hasHypeStopped && hypeSdk.hasHypeStarted) {
        return true;
    }
    return false;
}

private void showHypeNotReadyDialog() {
    if(hypeSdk.hasHypeFailed) {
        AlertDialogUtils.showInfoDialog(MainActivity.this,
                "Error", "Hype SDK could not be started.\n" + hypeSdk.hypeFailedMsg);
    }
    else if(hypeSdk.hasHypeStopped) {
        AlertDialogUtils.showInfoDialog(MainActivity.this,
                "Error", "Hype SDK stopped.\n" + hypeSdk.hypeStoppedMsg);
    }
    else if( !hypeSdk.hasHypeStarted) {
        AlertDialogUtils.showInfoDialog(MainActivity.this,
                "Warning", "Hype SDK is not ready yet.");
    }
}

private boolean isNoServiceSubscribed()
{
    return uiData.getNumberOfSubscribedServices() == 0;
}

private void showNoServicesSubscribedDialog() {
    AlertDialogUtils.showInfoDialog(MainActivity.this,
            "INFO", "No services subscribed");

}

static String processUserServiceNameInput(String input)
{
    return input.toLowerCase().trim();
}


/* Data Storage Operations */

protected void storeMessagesToPrefs() {
    Log.d(TAG, String.format("%s storeMessagesToPrefs()", HYPE_PUB_SUB_LOG_PREFIX));
    if (prefs == null) {
        return;
    }
    SharedPreferences.Editor editor = prefs.edit();

    // store list as jsonarray
    JSONArray jsonArray = new JSONArray();
    for (String z : cachedMessages) {
        jsonArray.put(z);
        Log.d(TAG, String.format("%s Add Message: " + z, HYPE_PUB_SUB_LOG_PREFIX));
    }
    editor.putString(CHANNEL_MESSAGES, jsonArray.toString());
    editor.commit();
    Log.d(TAG, String.format("%s SAVED into PREF storage.", HYPE_PUB_SUB_LOG_PREFIX));
}
    private void loadDataCustom() {
        messagesList.add("osman");
        if (adapter != null) adapter.notifyDataSetChanged();
    }

private void loadDataFromPrefs(String dataset) {
    Log.d(TAG, String.format("%s loadDataFromPrefs()", HYPE_PUB_SUB_LOG_PREFIX));
    if (prefs == null) {
        Log.d(TAG, String.format("%s PREF  = NULL ", HYPE_PUB_SUB_LOG_PREFIX));
        return;
    }
    else{
        Log.d(TAG, String.format("%s PREF has DATA", HYPE_PUB_SUB_LOG_PREFIX));
    }


    // read in json array from prefs to fill list
    cachedMessages = new ArrayList<>();
    String jsonArrayString = prefs.getString(dataset, "");
    Log.d(TAG, String.format("%s jsonArrayString : " + jsonArrayString, HYPE_PUB_SUB_LOG_PREFIX));
    if (!TextUtils.isEmpty(jsonArrayString)) {
        try {
            JSONArray jsonArray = new JSONArray(jsonArrayString);
            if (jsonArray.length() > 0) {
                messagesList.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    String next= jsonArray.getString(i);
                    Log.d(TAG, String.format("%s next : " + next, HYPE_PUB_SUB_LOG_PREFIX));
                    cachedMessages.add(next);
                    messagesList.add(next);
                    if (adapter != null) adapter.notifyDataSetChanged();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
    protected void storeChannelsToPrefs(){
        Log.d(TAG, String.format("%s storeChannelsToPrefs()", HYPE_PUB_SUB_LOG_PREFIX));
        if (prefs == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();

        // store list as jsonarray
        JSONArray jsonArray = new JSONArray();
        for (String z : subscribedChannels) {
            jsonArray.put(z);
            Log.d(TAG, String.format("%s Add Channel : " + z, HYPE_PUB_SUB_LOG_PREFIX));
        }
        editor.putString(SUBSCRIBED_CHANNELS, jsonArray.toString());
        editor.commit();
        Log.d(TAG, String.format("%s SAVED into PREF storage.", HYPE_PUB_SUB_LOG_PREFIX));

    }
protected void reSubscribeToAllChannels(){
    Log.d(TAG, String.format("%s reSubscribeToAllChannels()", HYPE_PUB_SUB_LOG_PREFIX));
    if (prefs == null) {
        Log.d(TAG, String.format("%s PREF  = NULL ", HYPE_PUB_SUB_LOG_PREFIX));
        return;
    }
    else{
        Log.d(TAG, String.format("%s PREF has DATA", HYPE_PUB_SUB_LOG_PREFIX));
    }
    // read in json array from prefs to fill list
    subscribedChannels = new ArrayList<>();
    String jsonArrayString = prefs.getString(SUBSCRIBED_CHANNELS, "");
    Log.d(TAG, String.format("%s jsonArrayString of Subscribed Channels : " + jsonArrayString, HYPE_PUB_SUB_LOG_PREFIX));
    if (!TextUtils.isEmpty(jsonArrayString)) {
        try {
            JSONArray jsonArray = new JSONArray(jsonArrayString);
            if (jsonArray.length() > 0) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    String next= jsonArray.getString(i);
                    Log.d(TAG, String.format("%s next channel: " + next, HYPE_PUB_SUB_LOG_PREFIX));
                    subscribedChannels.add(next);
                    try {
                        manuallySubscribe(next);
                    }
                    catch (Exception e){
                        Log.e(TAG, String.format("%s Error occured in resubscribe method : " + e.getMessage(), HYPE_PUB_SUB_LOG_PREFIX));
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
}