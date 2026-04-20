package com.winlator.cmod;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.contentdialog.SaveEditDialog;
import com.winlator.cmod.saves.Save;
import com.winlator.cmod.saves.SaveManager;
import com.winlator.cmod.contentdialog.SaveSettingsDialog;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.R;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.ImageUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    public static final byte OPEN_IMAGE_REQUEST_CODE = 5;
    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private SharedPreferences sharedPreferences;
    private ContainerManager containerManager;
    private boolean isDarkMode;
    private SaveEditDialog currentSaveEditDialog;
    private SaveEditDialog saveEditDialog;
    private SaveManager saveManager;
    private SaveSettingsDialog saveSettingsDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get shared preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Load the user's preferred theme
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false);

        // Apply the theme based on the preference
        if (isDarkMode) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }


        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        NavigationView navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);

        setSupportActionBar(findViewById(R.id.Toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
        }

        // Determine text color based on dark mode
        int textColor = isDarkMode ? Color.WHITE : Color.BLACK;
        setNavigationViewItemTextColor(navigationView, textColor);

        // Create Winlator folder if not present
        File winlatorDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH);
        if (!winlatorDir.exists())
            winlatorDir.mkdirs();

        containerManager = new ContainerManager(this);
        saveManager = new SaveManager(this);

        Intent intent = getIntent();
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.main_menu_input_controls));
            navigationView.setCheckedItem(R.id.main_menu_input_controls);
        } else {
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId;

            // If no specific menu item is selected, check for shortcuts first
            if (selectedMenuItemId == 0) {
                List<com.winlator.cmod.container.Shortcut> shortcuts = containerManager.loadShortcuts();
                menuItemId = !shortcuts.isEmpty() ? R.id.main_menu_shortcuts : R.id.main_menu_containers;
            } else {
                menuItemId = selectedMenuItemId;
            }

            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);

            if (!requestAppPermissions()) {
                ImageFsInstaller.installIfNeeded(this);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                showAllFilesAccessDialog();
            }
        }
    }

    private void showAllFilesAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.files_access_title)
                .setMessage(R.string.files_access_content)
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    // Method to show SaveEditDialog
    public void showSaveEditDialog(Save saveToEdit) {
        saveEditDialog = new SaveEditDialog(this, saveManager, containerManager, saveToEdit);

        // Check for dark mode and set the background accordingly
        if (isDarkMode) {
            saveEditDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        } else {
            saveEditDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
        }

        saveEditDialog.show();
    }
    private void showSavesFragment() {
        SavesFragment fragment = new SavesFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.FLFragmentContainer, fragment)
                .commit();
    }
    public void onSaveAdded() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.FLFragmentContainer);
        if (currentFragment instanceof SavesFragment) {
            ((SavesFragment) currentFragment).refreshSavesList();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ImageFsInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragments = fragmentManager.getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof BaseFileManagerFragment && fragment.isVisible()) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment) fragment;
                if (fileManagerFragment.onBackPressed()) {
                    return;
                }
            }else if (fragment instanceof ContainersFragment && fragment.isVisible()) {
                finish();
                return;
            }
        }
        if (!editInputControls)
            showFragment(new ContainersFragment(), true);  // Pass `true` to trigger the reverse animation
        else
            super.onBackPressed();
    }

    private boolean requestAppPermissions() {
        boolean hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasManageStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();

        if (hasWritePermission && hasReadPermission && hasManageStoragePermission) {
            return false; // All permissions are granted
        }

        if (!hasWritePermission || !hasReadPermission) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        }

        return true; // Permissions are still being requested
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.FLFragmentContainer);
        if (menuItem.getItemId() == android.R.id.home) {
            if (editInputControls) {
                onBackPressed();
                return true;
            }
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment) currentFragment;
                if (fileManagerFragment.onOptionsMenuClicked()) {
                    return true;
                }
            }

            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }else if (menuItem.getItemId() == R.id.saves_menu_add) {
            // Check if we are editing a save
            Intent intent = getIntent();
            int editSaveId = intent.getIntExtra("edit_save_id", -1);
            Save saveToEdit = editSaveId >= 0 ? saveManager.getSaveById(editSaveId) : null;

            // Create and show SaveEditDialog or SaveSettingsDialog as appropriate
            if (saveToEdit != null) {
                // Ensure previous dialog is dismissed before showing a new one
                if (saveEditDialog != null && saveEditDialog.isShowing()) {
                    saveEditDialog.dismiss();
                }
                showSaveEditDialog(saveToEdit); // Use the correct method to show SaveEditDialog
            } else {
                saveSettingsDialog = new SaveSettingsDialog(this, saveManager, containerManager);

                // Check for dark mode and set the background accordingly
                if (isDarkMode) {
                    saveSettingsDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
                } else {
                    saveSettingsDialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
                }

                saveSettingsDialog.show();
            }
            return true;
        }  else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    public void toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        switch (item.getItemId()) {
            case R.id.main_menu_shortcuts:
                showFragment(new ShortcutsFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_containers:
                showFragment(new ContainersFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_input_controls:
                showFragment(new InputControlsFragment(selectedProfileId), false);  // Forward animation
                break;
            case R.id.main_menu_contents:
                showFragment(new ContentsFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_adrenotools_gpu_drivers:
                showFragment(new AdrenotoolsFragment(), false);
                break;
            case R.id.main_menu_settings:
                showFragment(new SettingsFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_saves:
                showFragment(new SavesFragment(), false);  // Forward animation
                break;
            case R.id.main_menu_system_info:
                showSystemInfoDialog();
                break;
        }
        return true;
    }

    public void showFragment(Fragment fragment, boolean reverse) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (reverse) {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.FLFragmentContainer, fragment)
                    .commitAllowingStateLoss();
        } else {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.FLFragmentContainer, fragment)
                    .commitAllowingStateLoss();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void showSystemInfoDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.system_info_dialog);
        if (isDarkMode) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== "+getString(R.string.general_information)+" ===").append(System.lineSeparator());
        sb.append(getString(R.string.device_manufacturer)+'：'+Build.MANUFACTURER).append(System.lineSeparator());
        sb.append(getString(R.string.device_model)+'：'+Build.MODEL).append(System.lineSeparator());
        sb.append(getString(R.string.device_name)+'：'+Build.DEVICE).append(System.lineSeparator());
        sb.append(getString(R.string.product)+'：'+Build.PRODUCT).append(System.lineSeparator());
        sb.append(getString(R.string.hardware)+'：'+Build.HARDWARE).append(System.lineSeparator());
        sb.append(getString(R.string.supported_abis)+'：'+ String.join(",", Arrays.toString(Build.SUPPORTED_ABIS))).append(System.lineSeparator());
        sb.append(getString(R.string.android_version)+'：'+Build.VERSION.RELEASE+" API（"+Build.VERSION.SDK_INT+"）").append(System.lineSeparator());
        sb.append(getString(R.string.android_security_patch)+'：'+Build.VERSION.SECURITY_PATCH).append(System.lineSeparator());
        sb.append(getString(R.string.build_id)+'：'+Build.ID).append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("=== "+getString(R.string.cpu_info)+" ===").append(System.lineSeparator());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null && !Build.SOC_MODEL.trim().isEmpty()) {
            sb.append(getString(R.string.soc)+" "+Build.SOC_MODEL);
        }
        sb.append(System.lineSeparator()).append(getCpuInfoFromProc()).append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("=== "+getString(R.string.gpu_information)+" ===").append(System.lineSeparator());
        sb.append(getGpuInfo()).append(System.lineSeparator());

        // Memory Info
        sb.append("=== "+getString(R.string.memory_info)+" ===").append(System.lineSeparator());

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        long totalDeviceRam = memInfo.totalMem / (1024 * 1024);

        sb.append(getString(R.string.total_memory)+"： "+totalDeviceRam+" MB");

        TextView tVSystemInfo = dialog.findViewById(R.id.TVSystemInfo);
        tVSystemInfo.setText(sb.toString());

        dialog.show();
    }

    private String getGpuInfo() {
        StringBuilder gpuInfo = new StringBuilder();
        try {
            // Get GPU model via Vulkan API
            String gpuModel = GPUInformation.getRenderer(null, this);
            if (gpuModel != null && !gpuModel.isEmpty() && !gpuModel.equals("Unknown")) {
                gpuInfo.append(getString(R.string.gpu_model)).append("：").append(gpuModel).append(System.lineSeparator());
            }

            // Get Vulkan version via Vulkan API
            String vulkanVersion = GPUInformation.getVulkanVersion(null, this);
            if (vulkanVersion != null && !vulkanVersion.isEmpty() && !vulkanVersion.equals("Unknown")) {
                gpuInfo.append(getString(R.string.vulkan_version)).append("：").append(vulkanVersion).append(System.lineSeparator());
            }

            // Get Vulkan driver version via Vulkan API
            String driverVersion = GPUInformation.getDriverVersion(null, this);
            if (driverVersion != null && !driverVersion.isEmpty() && !driverVersion.equals("Unknown")) {
                gpuInfo.append(getString(R.string.vulkan_driver_version)).append("：").append(driverVersion).append(System.lineSeparator());
            }
        } catch (Exception e) {
            gpuInfo.append("Unable to get GPU info: ").append(e.getMessage());
        }
        return gpuInfo.toString();
    }

    private String getCpuInfoFromProc() {
        StringBuilder cpuInfo = new StringBuilder();
        StringBuilder variant = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            String cpuFeatures = null;
            int processorCount = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("processor")) {
                    processorCount++;
                }else if(line.startsWith("CPU variant")){
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        variant.append("CPU"+(processorCount-1)+"："+ parts[1].trim()).append(System.lineSeparator());
                    }
                } else if (line.startsWith("Features") || line.startsWith("flags")) {
                    if (cpuFeatures == null) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            cpuFeatures = parts[1].trim();
                        }
                    }
                }
            }
            reader.close();

            cpuInfo.append(getString(R.string.cpu_cores)).append("：").append(processorCount).append(System.lineSeparator());
            cpuInfo.append(variant.toString());
            if (cpuFeatures != null && !cpuFeatures.isEmpty()) {
                cpuInfo.append(getString(R.string.cpu_features)).append("：").append(cpuFeatures);
            }
        } catch (IOException e) {
            cpuInfo.append("Unable to read /proc/cpuinfo: ").append(e.getMessage());
        }
        return cpuInfo.toString();
    }

    private void setNavigationViewItemTextColor(NavigationView navigationView, int color) {
        for (int i = 0; i < navigationView.getMenu().size(); i++) {
            MenuItem menuItem = navigationView.getMenu().getItem(i);
            setMenuItemTextColor(menuItem, color);

            // If the menu item has sub-items, iterate through them
            if (menuItem.hasSubMenu()) {
                for (int j = 0; j < menuItem.getSubMenu().size(); j++) {
                    MenuItem subMenuItem = menuItem.getSubMenu().getItem(j);
                    setMenuItemTextColor(subMenuItem, color);
                }
            }
        }
    }

    private void setMenuItemTextColor(MenuItem menuItem, int color) {
        SpannableString spanString = new SpannableString(menuItem.getTitle());
        spanString.setSpan(new ForegroundColorSpan(color), 0, spanString.length(), 0);
        menuItem.setTitle(spanString);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_IMAGE_REQUEST_CODE && resultCode == RESULT_OK) {
            Bitmap bitmap = ImageUtils.getBitmapFromUri(this, data.getData(), 1280);
            if (bitmap == null) return;
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(this);
            ImageUtils.save(bitmap, userWallpaperFile, Bitmap.CompressFormat.PNG, 100);
        }else if (saveEditDialog != null && saveEditDialog.isShowing()) {
            Log.d("WinActivity", "Forwarding result to SaveEditDialog");
            saveEditDialog.onActivityResult(requestCode, resultCode, data);
        }else if (saveSettingsDialog != null && saveSettingsDialog.isShowing()) {
            Log.d("WinActivity", "Forwarding result to SaveSettingsDialog");
            saveSettingsDialog.onActivityResult(requestCode, resultCode, data);
        }
    }
}
