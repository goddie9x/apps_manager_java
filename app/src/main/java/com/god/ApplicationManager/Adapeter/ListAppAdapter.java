package com.god.ApplicationManager.Adapeter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.god.ApplicationManager.Entity.AppInfo;
import com.god.ApplicationManager.Enum.MenuContextType;
import com.god.ApplicationManager.R;

import java.util.ArrayList;
import java.util.List;

public class ListAppAdapter extends RecyclerView.Adapter<ListAppAdapter.AppItemHolder>
        implements View.OnCreateContextMenuListener {
    private List<AppInfo> listApp;
    private AppInfo selectedAppInfo;
    private List<AppInfo> listSelectedApp = new ArrayList<>();
    private boolean isEnableSelect = false;
    private MenuContextType crrMenuContext=MenuContextType.MAIN_MENU;

    public void setMenuContextType(MenuContextType menuContextType) {
        crrMenuContext = menuContextType;
    }

    public void setListAppSelected(List<AppInfo> listAppSelected) {
        this.listSelectedApp = listSelectedApp;
        notifyDataSetChanged();
    }

    public interface TouchItemEvent {
        void onTouch(LinearLayout view, AppInfo appInfo);
    }

    private TouchItemEvent onTouchEvent;

    public void setOnTouchEvent(TouchItemEvent onTouchEvent) {
        this.onTouchEvent = onTouchEvent;
    }

    public interface ChangeAmountSelectionEvent {
        void callback(List<AppInfo> listSelectedApp);
    }

    private ChangeAmountSelectionEvent onChangeAmountSelectionEvent;

    public void setOnChangeAmountSelectionEvent(ChangeAmountSelectionEvent callback) {
        this.onChangeAmountSelectionEvent = callback;
    }

    public ListAppAdapter(List<AppInfo> listApp) {
        this.listApp = listApp;
    }

    @NonNull
    @Override
    public AppItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.activity_app_info_item,
                parent,
                false
        );
        AppItemHolder holder = new AppItemHolder(itemView);
        itemView.setTag(holder);
        return holder;
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onBindViewHolder(@NonNull ListAppAdapter.AppItemHolder holder, int position) {
        AppInfo crrAppInfo = listApp.get(position);
        holder.appNameLabel.setText(crrAppInfo.appName);
        holder.appPackageLabel.setText(crrAppInfo.packageName);
        holder.appIcon.setImageDrawable(crrAppInfo.appIcon);
        if (!crrAppInfo.isSystemApp) {
            holder.systemLabel.setText(R.string.user);
            holder.systemLabel.setTextColor(Color.GREEN);
        }
        if (crrAppInfo.isRunning) {
            holder.runningLabel.setText(R.string.running);
            holder.runningLabel.setTextColor(Color.GREEN);
        }
        holder.setSelectedApp(listSelectedApp.contains(crrAppInfo));
        holder.itemView.setOnCreateContextMenuListener(this);
        holder.itemView.setOnClickListener(v -> {
            if (isEnableSelect) {
                holder.setSelectedApp(!holder.isSelected);
                if (holder.isSelected) {
                    if (!listSelectedApp.contains(crrAppInfo)) {
                        listSelectedApp.add(crrAppInfo);
                    }
                } else {
                    if (listSelectedApp.contains(crrAppInfo)) {
                        listSelectedApp.remove(crrAppInfo);
                    }
                }
                if (onChangeAmountSelectionEvent != null) {
                    onChangeAmountSelectionEvent.callback(listSelectedApp);
                }
            } else {
                if (onTouchEvent != null) {
                    onTouchEvent.onTouch(holder.listServiceLayout, crrAppInfo);
                }
                holder.setShowServices(!holder.isShowServices);
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = new MenuInflater(v.getContext());
        inflater.inflate(R.menu.menu_context, menu);
        AppItemHolder holder = (AppItemHolder) v.getTag();
        boolean isOpenMainMenu = crrMenuContext == MenuContextType.MAIN_MENU;
        int amountMenuItem = menu.size();
        for (int i = 0; i < amountMenuItem; i++) {
            MenuItem item = menu.getItem(i);
            int menuId = item.getItemId();
            if (menuId == R.id.remove_from_list) {
                item.setVisible(!isOpenMainMenu);
            } else {
                item.setVisible(isOpenMainMenu);
                if(menuId==R.id.open_in_setting){
                    item.setVisible(!isEnableSelect);
                }
            }
            if(menuId ==R.id.open_in_setting){
                item.setVisible(!isEnableSelect);
            }
        }
        if (holder != null) {
            int indexItemSelected = holder.getBindingAdapterPosition();
            selectedAppInfo = (indexItemSelected < listApp.size()) ? listApp.get(indexItemSelected) : null;
        }
    }

    @Override
    public int getItemCount() {
        return listApp.size();
    }

    public static class AppItemHolder extends RecyclerView.ViewHolder {
        private final ImageView appIcon;
        private final TextView appNameLabel;
        private final TextView appPackageLabel;
        private final TextView systemLabel;
        private final LinearLayout listServiceLayout;
        private final LinearLayout currentItem;
        private final ImageView iconSelected;
        private final TextView runningLabel;
        private boolean isShowServices = false;
        private boolean isSelected = false;

        private void setShowServices(boolean isShowSevices) {
            this.isShowServices = isShowSevices;
            listServiceLayout.setVisibility(this.isShowServices ? View.VISIBLE : View.GONE);
        }

        public AppItemHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            systemLabel = itemView.findViewById(R.id.system_label);
            appNameLabel = itemView.findViewById(R.id.app_name);
            appPackageLabel = itemView.findViewById(R.id.app_package);
            iconSelected = itemView.findViewById(R.id.selected_icon);
            appNameLabel.setSelected(true);
            appNameLabel.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            appNameLabel.setMarqueeRepeatLimit(-1);
            appPackageLabel.setSelected(true);
            appPackageLabel.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            appPackageLabel.setMarqueeRepeatLimit(-1);
            listServiceLayout = itemView.findViewById(R.id.list_service);
            runningLabel = itemView.findViewById(R.id.running_label);
            currentItem = (LinearLayout) itemView;
        }

        public void setSelectedApp(boolean isSelected) {
            this.isSelected = isSelected;
            if (isSelected) {
                iconSelected.setVisibility(View.VISIBLE);
                currentItem.setBackgroundResource(androidx.cardview.R.color.cardview_dark_background);
            } else {
                iconSelected.setVisibility(View.GONE);
                currentItem.setBackgroundResource(androidx.cardview.R.color.cardview_shadow_start_color);
            }
        }
    }

    public AppInfo getSelectedAppInfo() {
        return selectedAppInfo;
    }

    public void clearSelectedAppInfo() {
        selectedAppInfo = null;
    }

    public void setListApp(List<AppInfo> listApp) {
        this.listApp = listApp;
        notifyDataSetChanged();
    }

    public void toggleEnableSelect() {
        isEnableSelect = !isEnableSelect;
        if (!isEnableSelect) {
            clearSelect();
        }
    }

    public void selectAll() {
        listSelectedApp.clear();
        listSelectedApp.addAll(listApp);
        if (onChangeAmountSelectionEvent != null) {
            onChangeAmountSelectionEvent.callback(listSelectedApp);
        }
        notifyDataSetChanged();
    }

    public void clearSelect() {
        listSelectedApp.clear();
        if (onChangeAmountSelectionEvent != null) {
            onChangeAmountSelectionEvent.callback(listSelectedApp);
        }
        notifyDataSetChanged();
    }

    public boolean getSelectionState() {
        return isEnableSelect;
    }

    public List<AppInfo> getListSelectedApp() {
        return listSelectedApp;
    }

    public List<AppInfo> getListApp() {
        return listApp;
    }
}
