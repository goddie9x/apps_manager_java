package com.god.ApplicationManager.Service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.god.ApplicationManager.Enum.BackgroundServiceNextAction;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Unit;

public class FreezeService extends AccessibilityService {
    private final String TAG = "BackgroundService";
    private final String SETTINGS_PACKAGE_NAME = "BackgroundService";
    private String forceStopButtonName;
    private Resources settingsResource;
    private Context baseContext;
    private PackageManager packageManager;
    private BackgroundServiceNextAction nextAction;
    private Handler timeoutHandler = new Handler();
    private long lastActionTimestamp = 0L;
    private interface OnOnAppCouldNotBeFrozen{
        Unit callback(Context context);
    }
    public OnOnAppCouldNotBeFrozen doOnAppCouldNotBeFrozen;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //We are only interested in WINDOW_STATE_CHANGED events
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String className  = (String) event.getClassName();
        switch (nextAction) {
            case PRESS_FORCE_STOP:
                if (className == "com.android.settings.applications.InstalledAppDetailsTop") {
                    pressForceStopButton(event);
                } else {
                    Log.w(TAG, "awaited InstalledAppDetailsTop to be the next screen but it was ${event.className}");
                    wrongScreenShown();
                }

                break;
            case PRESS_OK:
                break;
            case PRESS_BACK:
                break;
            case DO_NOTHING:
                break;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        baseContext = getBaseContext();
        packageManager = baseContext.getPackageManager();
        try {
            settingsResource =packageManager
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(baseContext,"Get app settings failed",Toast.LENGTH_SHORT).show();
            throw new RuntimeException(e);
        }
    }
    @Override
    public void onInterrupt() {

    }
    private String getButtonName(String name){
        String buttonName;
        // Try to find out what it says on the Force Stop button (different in different languages)

        int resourceId = settingsResource.getIdentifier(name, "string",
                SETTINGS_PACKAGE_NAME);
        if (resourceId > 0) {
            buttonName= settingsResource.getString(resourceId);
        } else {
            Log.e(TAG, "Label for the force stop button in settings could not be found");
            buttonName= null;
        }
        return buttonName;
    }
    private String getForceStopButtonName(){
        if(forceStopButtonName==null){
            forceStopButtonName=getButtonName("force_stop");
        }
        return forceStopButtonName;
    }
    private void pressForceStopButton(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        List<AccessibilityNodeInfo> nodesToClick = node.findAccessibilityNodeInfosByText("FORCE STOP");

        if (nodesToClick.isEmpty())
            nodesToClick = node.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button");

        if (nodesToClick.isEmpty()){
            nodesToClick = node.findAccessibilityNodeInfosByText(forceStopButtonName);
        }
        if (nodesToClick.isEmpty()) {
            List<AccessibilityNodeInfo> buttons = new ArrayList<AccessibilityNodeInfo>();
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

        boolean success = clickAll(nodesToClick, "force stop", node);
        if (success) nextAction = BackgroundServiceNextAction.PRESS_OK;
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

    private boolean clickAll(List<AccessibilityNodeInfo> nodes, String buttonName,
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
        nextAction = BackgroundServiceNextAction.DO_NOTHING;
        performGlobalAction(GLOBAL_ACTION_BACK);
        Log.i(TAG, "We arrived at pressBackButton(), this means that one app was successfully frozen.");
    }

    private void notifyThereIsStillMovement(Context context) {
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
        String[]parentNodeNameSplited = parentNode.getClassName().toString()
                .split(".");
        if (parentNode.isClickable() &&  parentNodeNameSplited[parentNodeNameSplited.length-1] == "Button") {
            l.add(parentNode);
        }
        int amountNode = parentNode.getChildCount();
        for (int i =0;i<amountNode;i++) {
           getButtons(parentNode.getChild(i), l);
        }
    }
    public static void stopAnyCurrentFreezing() {
        if (nextAction != BackgroundServiceNextAction.DO_NOTHING)
            Log.i(TAG, "Stopping current freeze process (stopAnyCurrentFreezing()), nextAction was $nextAction");
        nextAction = BackgroundServiceNextAction.DO_NOTHING;
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
        // else do nothing and simply wait for the next screen to show up.
    }

}
