package com.god.ApplicationManager.Interface;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public interface HandleGetNodeToClick {
    void callback(AccessibilityNodeInfo rootNode, List<AccessibilityNodeInfo> nodesToClick);
}
