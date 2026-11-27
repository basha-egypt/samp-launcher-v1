package com.xyron.game.launcher.fragments;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xyron.game.BuildConfig;
import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.UpdateActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.GameDataVerifier;
import com.xyron.game.launcher.util.SampQueryApi;
import org.ini4j.Wini;
import java.io.File;
import com.xyron.game.launcher.util.ServerConfigManager;

public class HomeFragment extends Fragment {
    private static final int NEXT_STEP_PLAY = 0;
    private static final int NEXT_STEP_UPDATE_DATA = 1;
    private static final int NEXT_STEP_ADD_SERVER = 2;
    private static final int NEXT_STEP_CHOOSE_SERVER = 3;

    private View playButton;
    private TextView selectServerButton;
    private TextView selectedServerName;
    private TextView selectedServerAddress;
    private TextView selectedServerHint;
    private TextView readinessTitle;
    private TextView readinessBody;
    private TextView nextStepButton;
    private int nextStepAction = NEXT_STEP_PLAY;

    private View statusDot;
    private TextView statusText;
    private TextView playersText;
    private TextView pingText;
    private TextView nameValue;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private volatile boolean liveQueryRunning = false;
    private final Runnable liveTicker = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            requestLiveInfo();
            liveHandler.postDelayed(this, 5000L);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        playButton = root.findViewById(R.id.play_button);
        selectServerButton = root.findViewById(R.id.select_server_button);
        selectedServerName = root.findViewById(R.id.selected_server_name);
        selectedServerAddress = root.findViewById(R.id.selected_server_address);
        selectedServerHint = root.findViewById(R.id.selected_server_hint);
        readinessTitle = root.findViewById(R.id.home_readiness_title);
        readinessBody = root.findViewById(R.id.home_readiness_body);
        nextStepButton = root.findViewById(R.id.home_next_step_button);
        statusDot = root.findViewById(R.id.home_status_dot);
        statusText = root.findViewById(R.id.home_status_text);
        playersText = root.findViewById(R.id.home_players_text);
        pingText = root.findViewById(R.id.home_ping_text);
        nameValue = root.findViewById(R.id.home_name_value);

        View nameRow = root.findViewById(R.id.home_name_row);
        if (nameRow != null) {
            nameRow.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openTab(MainActivity.TAB_SETTINGS);
                }
            });
        }

        if (playButton != null) {
            playButton.setOnTouchListener(new ButtonAnimator(requireContext(), playButton));
            playButton.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).startGta();
                }
            });
        }

        if (selectServerButton != null) {
            selectServerButton.setOnTouchListener(new ButtonAnimator(requireContext(), selectServerButton));
            selectServerButton.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openTab(
                            ServerConfigManager.hasConfiguredServers(requireContext())
                                    ? MainActivity.TAB_SERVERS
                                    : MainActivity.TAB_SETTINGS
                    );
                }
            });
        }

        configureTabShortcut(root, R.id.home_quick_host, MainActivity.TAB_HOST);
        configureTabShortcut(root, R.id.home_quick_files, MainActivity.TAB_HOST_FILES);
        configureTabShortcut(root, R.id.home_quick_settings, MainActivity.TAB_SETTINGS);

        if (nextStepButton != null) {
            nextStepButton.setOnTouchListener(new ButtonAnimator(requireContext(), nextStepButton));
            nextStepButton.setOnClickListener(v -> performNextStep());
        }

        updateSelectedServer();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSelectedServer();
        updateNameValue();
        liveHandler.removeCallbacks(liveTicker);
        liveHandler.post(liveTicker);
    }

    @Override
    public void onPause() {
        super.onPause();
        liveHandler.removeCallbacks(liveTicker);
    }

    private boolean isAdmin() {
        return getActivity() instanceof MainActivity && ((MainActivity) getActivity()).isAdminMode();
    }

    private void updateNameValue() {
        if (nameValue == null || getContext() == null || getContext().getExternalFilesDir(null) == null) {
            return;
        }
        String nick = "";
        try {
            File f = new File(getContext().getExternalFilesDir(null), "SAMP/settings.ini");
            if (f.exists()) {
                Object v = new Wini(f).get("client", "name");
                nick = v == null ? "" : v.toString().trim();
            }
        } catch (Exception ignored) {
        }
        boolean empty = nick.isEmpty();
        nameValue.setText(empty ? "Steve_Max" : nick);
        nameValue.setTextColor(getResources().getColor(empty
                ? R.color.launcher_text_muted : R.color.launcher_text_primary));
    }

    private void requestLiveInfo() {
        Context context = getContext();
        if (context == null || liveQueryRunning) {
            return;
        }
        final ServerConfigManager.ServerOption server = ServerConfigManager.getSelectedServer(context);
        if (server == null || !server.isValid()) {
            return;
        }
        liveQueryRunning = true;
        new Thread(() -> {
            boolean online = false;
            int players = 0, max = 0, ping = 0;
            SampQueryApi api = new SampQueryApi(server.host, server.port, 600);
            try {
                long p = api.measurePingMs(1);
                if (p >= 0) {
                    online = true;
                    ping = (int) p;
                    String[] info = api.getInfo();
                    if (info != null) {
                        players = parseInt(info[1]);
                        max = parseInt(info[2]);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                api.close();
            }
            final boolean fOnline = online;
            final int fPlayers = players, fMax = max, fPing = ping;
            liveQueryRunning = false;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> applyLiveInfo(fOnline, fPlayers, fMax, fPing));
            }
        }, "arab-island-live").start();
    }

    private void applyLiveInfo(boolean online, int players, int max, int ping) {
        if (!isAdded() || statusText == null) {
            return;
        }
        statusDot.setBackgroundResource(online
                ? R.drawable.server_status_dot_online : R.drawable.server_status_dot_offline);
        statusText.setText(online ? "متصل" : "غير متاح");
        playersText.setText(online ? players + " / " + max : "-- / --");
        pingText.setText(online && ping > 0 ? ping + " ms" : "-- ms");
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }

    private void configureTabShortcut(View root, int viewId, int tabIndex) {
        View shortcut = root.findViewById(viewId);
        if (shortcut == null) {
            return;
        }

        if (getActivity() instanceof MainActivity
                && !((MainActivity) getActivity()).isTabAllowed(tabIndex)) {
            shortcut.setVisibility(View.GONE);
            return;
        }

        shortcut.setOnTouchListener(new ButtonAnimator(requireContext(), shortcut));
        shortcut.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openTab(tabIndex);
            }
        });
    }

    private void updateSelectedServer() {
        Context context = getContext();
        if (context == null || selectedServerName == null || selectedServerAddress == null || selectedServerHint == null) {
            return;
        }

        boolean admin = isAdmin();
        boolean hasGameData = isLauncherRole() || GameDataVerifier.hasRequiredGameData(context);
        boolean hasServers = ServerConfigManager.hasConfiguredServers(context);

        // Server-switch button and raw address are admin-only.
        if (selectServerButton != null) {
            selectServerButton.setVisibility(admin ? View.VISIBLE : View.GONE);
        }
        selectedServerAddress.setVisibility(admin ? View.VISIBLE : View.GONE);

        if (!hasServers) {
            selectedServerName.setText("لا يوجد سيرفر");
            selectedServerAddress.setText("");
            selectedServerHint.setText("لا يوجد سيرفر متاح حالياً.");
            updateReadiness(hasGameData, false, false);
            return;
        }

        ServerConfigManager.ServerOption selectedServer = ServerConfigManager.getSelectedServer(context);
        if (selectedServer == null || !selectedServer.isValid()) {
            selectedServerName.setText("لا يوجد سيرفر");
            selectedServerAddress.setText("");
            selectedServerHint.setText("اختر سيرفراً للمتابعة.");
            updateReadiness(hasGameData, true, false);
            return;
        }

        if (selectServerButton != null) {
            selectServerButton.setText("تغيير السيرفر");
        }
        selectedServerName.setText(selectedServer.name);
        selectedServerAddress.setText(admin ? selectedServer.getAddress() : "");
        selectedServerHint.setText("السيرفر جاهز، اضغط العب الآن للدخول إلى المدينة.");
        updateReadiness(hasGameData, true, true);
    }

    private void updateReadiness(boolean hasGameData, boolean hasServers, boolean hasSelectedServer) {
        if (readinessTitle == null || readinessBody == null || nextStepButton == null) {
            return;
        }

        nextStepButton.setVisibility(View.VISIBLE);

        if (!hasGameData) {
            nextStepAction = NEXT_STEP_UPDATE_DATA;
            readinessTitle.setText("ملفات اللعبة ناقصة");
            readinessBody.setText("حمّل ملفات اللعبة أولاً");
            nextStepButton.setText("تحميل الملفات");
            return;
        }

        if (!hasServers) {
            nextStepAction = NEXT_STEP_ADD_SERVER;
            readinessTitle.setText("لا يوجد سيرفر");
            readinessBody.setText("تواصل مع الإدارة");
            nextStepButton.setText("الإعدادات");
            return;
        }

        if (!hasSelectedServer) {
            nextStepAction = NEXT_STEP_CHOOSE_SERVER;
            readinessTitle.setText("اختر السيرفر");
            readinessBody.setText("لم يتم تحديد سيرفر");
            nextStepButton.setText("اختيار السيرفر");
            return;
        }

        nextStepAction = NEXT_STEP_PLAY;
        readinessTitle.setText("اللانشر جاهز");
        readinessBody.setText("يمكنك الدخول الآن");
        nextStepButton.setVisibility(View.GONE);
    }

    private void performNextStep() {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }

        MainActivity activity = (MainActivity) getActivity();
        if (nextStepAction == NEXT_STEP_UPDATE_DATA) {
            Intent intent = new Intent(requireContext(), UpdateActivity.class);
            intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
            startActivity(intent);
            return;
        }

        if (nextStepAction == NEXT_STEP_ADD_SERVER) {
            activity.openTab(MainActivity.TAB_SETTINGS);
            return;
        }

        if (nextStepAction == NEXT_STEP_CHOOSE_SERVER) {
            activity.openTab(MainActivity.TAB_SERVERS);
            return;
        }

        activity.startGta();
    }

    private boolean isLauncherRole() {
        return "launcher".equals(BuildConfig.XYRON_APK_ROLE);
    }
}
