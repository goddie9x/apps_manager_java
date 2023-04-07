package com.god.ApplicationManager.Service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import com.god.ApplicationManager.Enum.FreezeServiceNextAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FreezeService extends AccessibilityService {
    private static final String TAG = "FreezeService";
    public static boolean isEnabled;
    private Thread backgroundThread;
    private final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    private Resources settingsResource;
    private static FreezeServiceNextAction nextAction = FreezeServiceNextAction.DO_NOTHING;
    private static final Handler timeoutHandler = new Handler();
    private static long lastActionTimestamp = 0L;
    private String forceStopButtonName;
    public boolean prefUseAccessibilityService;
    public static  void setNextAction(FreezeServiceNextAction next){
        nextAction = next;
    }

    private String getForceStopButtonName(){
        if(forceStopButtonName==null||forceStopButtonName.isEmpty()){
            try {
                // Try to find out what it says on the Force Stop button (different in different languages)
                int resourceId = settingsResource.getIdentifier("force_stop",
                        "string", SETTINGS_PACKAGE_NAME);
                if (resourceId > 0) {
                    forceStopButtonName = settingsResource.getString(resourceId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Settings activity's resources not found");
            }
        }
        return forceStopButtonName;
    }
    public interface OnAppCouldNotBeFrozen{
        void callback(Context context);
    }
    private static OnAppCouldNotBeFrozen doOnAppCouldNotBeFrozen;
    public static void setOnAppCouldNotBeFrozen(OnAppCouldNotBeFrozen eventHandler){
        doOnAppCouldNotBeFrozen = eventHandler;
    }
    public interface HandleGetNodeToClick{
        void callback(AccessibilityNodeInfo rootNode,List<AccessibilityNodeInfo> nodesToClick);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.i(TAG,"onAccessibilityEvent");
        String className  = (String) event.getClassName();
        switch (nextAction) {
            case PRESS_FORCE_STOP:
                if (Objects.equals(className, "com.android.settings.applications.InstalledAppDetailsTop")) {
                    pressForceStopButton(event);
                } else {
                    Log.w(TAG, "awaited InstalledAppDetailsTop to be the next screen but it was ${event.className}");
                    wrongScreenShown();
                }

                break;
            case PRESS_OK:
                String eventClassName = event.getClassName().toString();
                if (eventClassName.endsWith(".app.AlertDialog")
                        || eventClassName.endsWith(".app.COUIAlertDialog")) {
                    pressOkButton(event);
                } else {
                    Log.w(TAG, "awaited AlertDialog to be the next screen but it was ${event.className}");
                    wrongScreenShown();
                }
                break;
            case PRESS_BACK:
                pressBackButton();
                break;
            case DO_NOTHING:
                break;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context baseContext = getBaseContext();
        PackageManager packageManager = baseContext.getPackageManager();
        try {
            settingsResource = packageManager
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(baseContext,"Get app settings failed",Toast.LENGTH_SHORT).show();
            throw new RuntimeException(e);
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isEnabled) {
            isEnabled = true;
            startBackgroundThread();
        }
        return START_STICKY;
    }
    @Override
    public void onInterrupt() {
        isEnabled = false;
    }
    @Override
    public void onServiceConnected() {
        isEnabled = true;

        // From now on, expect that the service works:
        prefUseAccessibilityService = true;
    }
    private void startBackgroundThread() {
        backgroundThread = new Thread(()->{if(isEnabled) {
            try {
                Thread.sleep(120000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }});
        backgroundThread.start();
    }
    @Override
    public void onDestroy() {
        Log.i(TAG, "FreezerService was destroyed.");
        isEnabled = false;
        stopAnyCurrentFreezing();
        if (backgroundThread != null) {
            backgroundThread.interrupt();
        }
    }
    public void pressForceStopButton(AccessibilityEvent event) {
        pressButton(event,"FORCE STOP",FreezeServiceNextAction.PRESS_OK,(node,nodesToClick)->{
            if (nodesToClick.isEmpty())
                nodesToClick = node.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button");

            if (nodesToClick.isEmpty()){
                nodesToClick = node.findAccessibilityNodeInfosByText(getForceStopButtonName());
            }
            if (nodesToClick.isEmpty()) {
                List<AccessibilityNodeInfo> buttons = new ArrayList<>();
                getButtons(node, buttons);
                if (buttons.size() == 2) {
                    AccessibilityNodeInfo rightNode = buttons.get(1);
                    AccessibilityNodeInfo leftNode = buttons.get(0);
                    Log.w(TAG, "right: $rightNode, left: $leftNode");

                    // "force stop" contains two word, "uninstall" does not
                    int amountWordRight = rightNode.getText().toString().trim().split(" ").length;
                    int amountWordLeft = rightNode.getText().toString().trim().split(" ").length;

                    if (amountWordRight == 2 && amountWordLeft == 1)
                        nodesToClick = (List<AccessibilityNodeInfo>) leftNode;
                    else if (amountWordRight == 1 && amountWordLeft == 2)
                        nodesToClick = (List<AccessibilityNodeInfo>)rightNode;
                }
            }
        });
    }
    private void printNodes(AccessibilityNodeInfo parentNode) {
        if(parentNode==null) return;
        if (parentNode.isClickable()) {
            Log.e(TAG, "    $parentNode");
        }
        int amountChild = parentNode.getChildCount();
        for (int i=0;i<amountChild;i++) {
            printNodes(parentNode.getChild(i));
        }
    }

    private boolean clickAll(List<AccessibilityNodeInfo> nodes,
                          AccessibilityNodeInfo  parentNode)  {

        if (nodes.isEmpty()) {
            Log.e(TAG, "Could not find the $buttonName button.");
            Log.e(TAG, "Buttons:");
            printNodes(parentNode);
            stopAnyCurrentFreezing();
            if(doOnAppCouldNotBeFrozen!=null){
                doOnAppCouldNotBeFrozen.callback(this);
            }
            return false;
        } else if (nodes.size() > 1) {
            Log.w(TAG, "Found more than one $buttonName button, clicking them all.");
        }

        List<AccessibilityNodeInfo> clickableNodes = nodes.stream().filter(it-> it.isClickable() && it.isEnabled())
                .collect(Collectors.toList());

        if (clickableNodes.isEmpty()) {
            Log.e(TAG, "The button(s) is/are not clickable, aborting.");
            // Just do not press the button but immediately press Back and act as if the app was successfully frozen:
            // A disabled or not clickable button probably means that the app already is frozen.
            pressBackButton();
            return false;
        }

        for (AccessibilityNodeInfo node:clickableNodes) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        notifyThereIsStillMovement(this);
        return true;
    }
    private void pressBackButton() {
        nextAction = FreezeServiceNextAction.DO_NOTHING;
        performGlobalAction(GLOBAL_ACTION_BACK);
        Log.i(TAG, "We arrived at pressBackButton(), this means that one app was successfully frozen.");
    }

    private static void notifyThereIsStillMovement(Context context) {
        timeoutHandler.removeCallbacksAndMessages(null);

        // After 4 seconds, assume that something went wrong
        timeoutHandler.postDelayed(()->{
            Log.w(TAG, "timeout");
            stopAnyCurrentFreezing();
            if(doOnAppCouldNotBeFrozen!=null){
                doOnAppCouldNotBeFrozen.callback(context);
            }
        }, 4000);

        lastActionTimestamp = System.currentTimeMillis();
    }



    private void getButtons(AccessibilityNodeInfo parentNode,
                                             List<AccessibilityNodeInfo> l) {
        String[]parentNodeNameSplitted = parentNode.getClassName().toString()
                .split("\\.");
        if (parentNode.isClickable() &&  parentNodeNameSplitted[parentNodeNameSplitted.length-1].equals("Button")) {
            l.add(parentNode);
        }
        int amountNode = parentNode.getChildCount();
        for (int i =0;i<amountNode;i++) {
           getButtons(parentNode.getChild(i), l);
        }
    }
    public static void stopAnyCurrentFreezing() {
        if (nextAction != FreezeServiceNextAction.DO_NOTHING)
            Log.i(TAG, "Stopping current freeze process (stopAnyCurrentFreezing()), nextAction was $nextAction");
        nextAction = FreezeServiceNextAction.DO_NOTHING;
        timeoutHandler.removeCallbacksAndMessages(null);
    }
    private void wrongScreenShown() {
        // If the last action was more than 8 seconds ago, something went wrong and we should stop not to destroy anything.
        if (System.currentTimeMillis() - lastActionTimestamp > 8000) {
            Log.e(
                    TAG,
                    "An unexpected screen turned up and the last action was more than 8 seconds ago. Something went wrong. Aborted not to destroy anything"
            );
            stopAnyCurrentFreezing(); // Stop everything, it is to late to do anything :-(
        }
    }

    private void pressOkButton(AccessibilityEvent event) {
        pressButton(event,getString(android.R.string.ok),FreezeServiceNextAction.PRESS_BACK);
    }
    private void pressButton(AccessibilityEvent event,
                             String btnText,
                             FreezeServiceNextAction next){
        AccessibilityNodeInfo node = event.getSource();
        if(node==null){
            Log.e(TAG,"Node target not found");
            return;
        }
        List<AccessibilityNodeInfo> nodesToClick =
                node.findAccessibilityNodeInfosByText(btnText);
        if (clickAll(nodesToClick, node)) nextAction = next;
        node.recycle();
    }
    private void pressButton(AccessibilityEvent event,
                             String btnText,
                             FreezeServiceNextAction next,
                             HandleGetNodeToClick handleGetNodeToClick
    ){
        AccessibilityNodeInfo node = event.getSource();
        if(node==null){
            Log.e(TAG,"Node target not found");
            return;
        }
        List<AccessibilityNodeInfo> nodesToClick =
                node.findAccessibilityNodeInfosByText(btnText);
        if(nodesToClick.isEmpty()){
            handleGetNodeToClick.callback(node,nodesToClick);
        }
        if (clickAll(nodesToClick, node)) nextAction = next;
        node.recycle();
    }
    public static void clickFreezeButtons(Context context) {
        if (nextAction == FreezeServiceNextAction.DO_NOTHING) {
            nextAction = FreezeServiceNextAction.PRESS_FORCE_STOP;
            notifyThereIsStillMovement(context);
        } else {
            throw new IllegalStateException("Attempted to freeze, but was still busy (nextAction was $nextAction)");
        }
    }

    public static boolean isBusy(){return nextAction != FreezeServiceNextAction.DO_NOTHING;}

    public static void finishedFreezing() {
        timeoutHandler.removeCallbacksAndMessages(null);
    }
}
