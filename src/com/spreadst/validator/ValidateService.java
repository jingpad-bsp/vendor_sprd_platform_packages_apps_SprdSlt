package com.spreadst.validator;

import java.io.File;
import java.lang.reflect.Method;
//import src.com.sprd.engineermode.String;
import android.app.Activity;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.TelephonyIntents;

import android.os.ServiceManager;
import com.spreadst.validator.R;
import android.os.SystemProperties;

import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;

import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TeleUtils;

import com.android.sprd.telephony.RadioInteractor;

public class ValidateService extends Service {
    private static final String TAG = "Validator_ValidateService";
    private static String PROP_SSDA_MODE = "persist.vendor.radio.modem.config";
    private String OperatorName = null;

    public static enum RadioCapbility {
        NONE, TDD_SVLTE, FDD_CSFB, TDD_CSFB, CSFB, LW
    };

    // ssda mode
    private static String MODE_SVLTE = "svlte";
    private static String MODE_TDD_CSFB = "TL_TD_G,G";
    private static String MODE_FDD_CSFB = "TL_LF_W_G,G";
    private static String MODE_CSFB = "TL_LF_TD_W_G,G";
    private static String MODE_LW = "TL_LF_TD_W_G,W_G";

    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private RadioInteractor mRadioInteractor;

    public static final String ID = "id";

    public static final String TEST_CASE = "case";

    public static final String RE_TEST = "re_test";

    public static final int BUTTON_OK = 0;

    public static final int BUTTON_CANCEL = 1;

    public static final int BUTTON_NEXT = 2;

    public static final int BUTTON_RETRY = 3;

    public static final int BUTTON_FAILED = 4;

    public static final int BUTTON_NO = 5;

    public static final int LAUNCHER_ON_TOP = 8;

    public static final int BOOT_COMPLETED = 9;

    public static final int USER_START_APK = 10;

    public static final int STOP_RECEIVER = 11;

    public static final int APP_NULL = 0;

    public static final int APP_VIDEO = 1;

    public static final int APP_CAM_DC = 2;

    public static final int APP_CAM_DV = 3;

    public static final int APP_BENCHMARK = 4;

    public static final int APP_VOICE_CYCLE = 5;

    public static final int APP_TEL_TD = 7;

    public static final int APP_TEL_GSM = 6;

    public static final int APP_TEL_LTE = 8;

    public static final int APP_BT = 9;

    public static final int APP_WIFI = 10;

    public static final int APP_RESULT = 11;

    public static final int APP_END = 12;

    private static final String COMMAND_SU = "su";

    private static final String COMMAND_MONKEY_RUN = "monkey -f %s 1";

    private static final String SCRIPT_DC = "cam_dc";

    private static final String SCRIPT_DV = "cam_dv";

    private static final String SCRIPT_BENCHMARK = "benchmark";

    private boolean mRunning = false;

    public static final String HOME_LOAD_COMPLETED = "HOME_LOAD_COMPLETED";

    public static final String CURRENT_APP_IS_EXIT = "CURRENT_APP_IS_EXIT";

    public static final int NT_MODE_TD_SCDMA_ONLY = 15;

    public static final int NT_MODE_GSM_ONLY = 13;

    private static final int NT_MODE_WCDMA_ONLY = 14;

    private static final int NT_MODE_LTE_ONLY = 16;

    private static final int RE_SET_MODE = 17;

    private static final int GSM_ONLY = 60;

    private static final int WCDMA_ONLY = 61;

    private static final int TDSCDMA_ONLY = 62;// //td only not supported

    private static final int FDD_LTE_ONLY = 52;

    private static final int TDD_LTE_ONLY = 51; // tdd lte not supported

    private static final int LTE_FDD_TD_LTE = 53; // LTE-FDD/TD-LTE

    private int mSimindex = 0;

    private int mSubID = -1;

    private String atCmd;

    private static final String ENG_AT_NETMODE = "AT^SYSCONFIG=";

    private static final String RE_SET_MODE_AT = "AT+RESET=1";

    private ITelephony mITelephony = null;

    private String mNetworkOperatorName = null;

    private static final String SERVER_NAME = "atchannel";

    private int mSetRadioFeature = -2;

    private IATUtils iATUtils;

    private String serverName;

    private boolean[] mHasSelectTestCase;

    private static final int WIFI_ACTIVITY = 0;

    private static final int BT_ACTIVITY = 1;

    private static final int PING_ACTIVITY = 2;

    private static final int RESULT_ACTIVITY = 3;

    private boolean mStopReceiver = false;

    private MyVolumeReceiver mVolumeReceiver;

    private int mCurrentApp = APP_NULL;

    private Process mProcess = null;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        Log.d(TAG, "onStartCommand intent:" + intent);
        int id = intent.getIntExtra(ID, -1);
        if (id == BUTTON_OK) {
            mHasSelectTestCase = intent.getBooleanArrayExtra(TEST_CASE);
        }
        boolean reTest = intent.getBooleanExtra(RE_TEST, false);
        if (reTest) {
            mCurrentApp = APP_NULL;
        }
        processRequest(id);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
            builder.detectFileUriExposure();
        }
        myRegisterReceiver();
        mTelephonyManager = TelephonyManager.from(ValidateService.this);
        mRadioInteractor = new RadioInteractor(ValidateService.this);
        OperatorName = getOperator().trim();
    }

    @Override
    public void onDestroy() {
        SystemProperties.set("persist.vendor.radio.engtest.enable", "false");
        super.onDestroy();
        unregisterReceiver(mVolumeReceiver);
    }

    long timeBefore = 0;

    private void processRequest(int id) {
        Log.i(TAG, "processRequest id= " + id);
        switch (id) {
        case BUTTON_CANCEL:
            validateCancel();
            break;
        case BUTTON_FAILED:
        case BUTTON_NO:
            validateComplete();
            break;
        case BUTTON_OK:
        case BUTTON_NEXT:
            try {
                if (mProcess != null) {
                    mProcess.destroy();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mCurrentApp++;
            runApp();
            break;
        case LAUNCHER_ON_TOP:
            // user may press back or home key when in testing
            // so we should kill monkey process and show dialog to user
            // judge 2000s, because UIDialog may exit before the next app be
            // shown
            if (System.currentTimeMillis() - timeBefore > 1000
                    && mCurrentApp != (APP_END - 1) && !mStopReceiver) {
                Log.d(TAG, "processRequest timeBefore > 1000");
                try {
                    if (mProcess != null) {
                        mProcess.destroy();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (mCurrentApp == APP_NULL) {
                    showDialog(UIDialog.DIALOG_BEGIN);
                } else {
                    showDialog(UIDialog.DIALOG_CONFIRM);
                }
            } else
                Log.d(TAG, "processRequest timeBefore <= 1000, so passby");
            break;
        case BUTTON_RETRY:
            runApp();
            break;
        case BOOT_COMPLETED:
            SharedPreferences sltValue = getSharedPreferences(
                    UIDialog.SLT_VALUE, Context.MODE_PRIVATE);
            boolean bootStart = sltValue.getBoolean(
                    UIDialog.BOOT_COMPLETE_START, false);
            if (bootStart) {
                showDialog(UIDialog.DIALOG_BEGIN);
            } else {
                showDialog(UIDialog.DIALOG_FINISH);
            }
            break;
        case USER_START_APK:
            if (mCurrentApp == APP_NULL || mCurrentApp == APP_END) {
                showDialog(UIDialog.DIALOG_BEGIN);
            } else {
                showDialog(UIDialog.DIALOG_CONFIRM);
            }
            break;
        case STOP_RECEIVER:
            stopReceiver();
            SystemProperties.set("persist.vendor.radio.engtest.enable","false");
            break;
        default:
            break;
        }
        timeBefore = System.currentTimeMillis();
    }

    private void runApp() {
        Log.i(TAG, "runApp mCurrentApp =" + mCurrentApp);
        if(mHasSelectTestCase == null) return;
        mRunning = true;
        switch (mCurrentApp) {
        case APP_BENCHMARK:
            if (mHasSelectTestCase[UIDialog.BENCHMARK]) {
                runMonkeyScriptThread(SCRIPT_BENCHMARK);
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_CAM_DC:
            if (mHasSelectTestCase[UIDialog.CAM_DC]) {
                runMonkeyScriptThread(SCRIPT_DC);
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_CAM_DV:
            if (mHasSelectTestCase[UIDialog.CAM_DV]) {
                runMonkeyScriptThread(SCRIPT_DV);
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_END:
            validateComplete();
            break;
        case APP_TEL_GSM:
            // Set the modem network mode

            Log.d(TAG, "case APP_TEL_GSM ");
            if (mHasSelectTestCase[UIDialog.TEL_GSM]) {
                if (!hasSimCard()) {
                    return;
                }
                if (!isSupportLte()) {
                    Log.d(TAG, "case APP_TEL_GSM  !isSupportLte");
                    atCmd = ENG_AT_NETMODE + "13,3,2,4";
                    String atRsp = IATUtils.sendATCmd(atCmd, "atchannel"
                            + mSimindex);
                    Log.d(TAG, "atRsp:" + atRsp);
                    if (atRsp.contains(IATUtils.AT_OK)) {
                        Log.d(TAG,
                                "case APP_TEL_GSM !isSupportLte  switch network success : ");
                        Toast.makeText(
                                this,
                                getResources().getString(
                                        R.string.set_feature_success),
                                Toast.LENGTH_LONG).show();
                        return;
                    } else {
                        Log.d(TAG,
                                "case APP_TEL_GSM !isSupportLte  switch network fail : ");
                        Toast.makeText(
                                this,
                                getResources().getString(
                                        R.string.switch_modem_fail),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // if (!switchTeleNetwork(Phone.NT_MODE_GSM_ONLY)) {
                    // Log.d(TAG,
                    // "!switchTeleNetwork(Phone.NT_MODE_GSM_ONLY):"+(!switchTeleNetwork(Phone.NT_MODE_GSM_ONLY)));
                    // Log.d(TAG,
                    // "case APP_TEL_GSM !isSupportLte  switch network fail : "
                    // + Phone.NT_MODE_GSM_ONLY);
                    // Toast.makeText(this,
                    // getResources().getString(R.string.switch_modem_fail),
                    // Toast.LENGTH_LONG).show();
                    // return;
                    // }
                } else {
                    // mSetRadioFeature = NT_MODE_GSM_ONLY;

                    mSetRadioFeature = GSM_ONLY;
                    Log.d(TAG,
                            "case APP_TEL_GSM isSupportLte  mSetRadioFeature "
                                    + mSetRadioFeature);
                    resetModem(false);
                }
                startActivityCatchException(Properties.getDialorIntent());
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_TEL_TD:
            // Set the modem network mode

            Log.d(TAG, "case APP_TEL_TD ");
            if (mHasSelectTestCase[UIDialog.TEL_TD]) {
                if (!hasSimCard()) {
                    return;
                }
                if (!isSupportLte()) {
                    Log.d(TAG, "case APP_TEL_TD  !isSupportLte");
                    atCmd = ENG_AT_NETMODE + "14,3,2,4";
                    String atRsp = IATUtils.sendATCmd(atCmd, "atchannel"
                            + mSimindex);
                    Log.d(TAG, "atRsp:" + atRsp);
                    if (atRsp.contains(IATUtils.AT_OK)) {
                        Log.d(TAG,
                                "case APP_TEL_TD !isSupportLte  switch network success : ");
                        Toast.makeText(
                                this,
                                getResources().getString(
                                        R.string.set_feature_success),
                                Toast.LENGTH_LONG).show();
                        return;
                    } else {
                        Log.d(TAG,
                                "case APP_TEL_TD !isSupportLte  switch network fail : ");
                        Toast.makeText(
                                this,
                                getResources().getString(
                                        R.string.switch_modem_fail),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                } else {
                    OperatorName = getOperator().trim();
                    resetModemByOperatorName(OperatorName);
                }
                startActivityCatchException(Properties.getDialorIntent());
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_VIDEO:
            if (mHasSelectTestCase[UIDialog.VIDEO_TEST]) {
                startActivityCatchException(Properties.getVedioIntent());
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_VOICE_CYCLE:
            if (mHasSelectTestCase[UIDialog.VOICE_CYCLE]) {
                startActivityCatchException(Properties.getVoiceCircleIntent());
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_WIFI:
            if (mHasSelectTestCase[UIDialog.WIFI]) {
                startTestActivity(WIFI_ACTIVITY);
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_BT:
            if (mHasSelectTestCase[UIDialog.BT]) {
                startTestActivity(BT_ACTIVITY);
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_TEL_LTE:
            if (mHasSelectTestCase[UIDialog.TEL_LTE]) {
                if (!hasSimCard()) {
                    return;
                }
                if (isSupportLte()) {
                    OperatorName = getOperator().trim();
                    mSetRadioFeature = LTE_FDD_TD_LTE;
                    resetModem(true);

                    /*
                     * if (OperatorName.contains("China Unicom")){
                     * mSetRadioFeature = FDD_LTE_ONLY; Log.d(TAG,
                     * "China Unicom, FDD_LTE_ONLY: " +FDD_LTE_ONLY);
                     * mSetRadioFeature = FDD_LTE_ONLY; resetModem(true); }else
                     * if(OperatorName.contains("China Mobile")){ Log.d(TAG,
                     * "China Mobile, TDD_LTE_ONLY: " +TDD_LTE_ONLY);
                     * mSetRadioFeature = TDD_LTE_ONLY; resetModem(true); }else{
                     * Log.d(TAG,"Other OperatorName (not Unicom/Mobile ): " +
                     * OperatorName); }
                     */

                } else {
                    Toast.makeText(this,
                            getResources().getString(R.string.has_no_lte),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                startTestActivity(PING_ACTIVITY);
            } else {
                mCurrentApp++;
                runApp();
            }
            break;
        case APP_RESULT:
            startTestActivity(RESULT_ACTIVITY);
            break;
        default:
            break;
        }
    }

    private void startActivityCatchException(Intent intent){
        try {
            if(intent == null) return;
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }
    private void resetModemByOperatorName(String operatorName){
        Log.d(TAG, "resetModemByOperatorName operatorName=" + operatorName);
        if(operatorName == null) return;
        if (operatorName.contains("China Unicom")) {
            mSetRadioFeature = WCDMA_ONLY;
            Log.d(TAG, " China Unicom, WCDMA_ONLY: " + WCDMA_ONLY);
            resetModem(false);
        } else if (operatorName.contains("China Mobile")) {
            Log.d(TAG, " China Mobile, TDSCDMA_ONLY "
                    + TDSCDMA_ONLY);
            mSetRadioFeature = TDSCDMA_ONLY;
            resetModem(false);
        } else {
            Log.d(TAG, "Other OperatorName (not Unicom/Mobile ): "
                    + operatorName);
        }
    }

    private boolean runMonkeyScript(String scripFile) {
        try {
            Log.d(TAG, "execMonkey enter");
            mProcess = new ProcessBuilder()
                    .command("monkey", "-f",
                            Properties.SDCARD_PATH + scripFile, "1")
                    .redirectErrorStream(true).start();

            int exitValue = mProcess.waitFor();
            Log.d(TAG, "Process.waitFor() return " + exitValue);
            Log.e(TAG, "execMonkey exit");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                mProcess.destroy();
                mProcess = null;
                mRunning = false;
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        return true;
    }

    private void validateComplete() {
        // showDialog(UIDialog.DIALOG_END);
        Toast.makeText(this, getResources().getString(R.string.test_complete),
                Toast.LENGTH_LONG).show();
        stopSelf();
    }

    private void validateCancel() {
        stopSelf();
    }

    private void runMonkeyScriptThread(final String scriptName) {
        File monkeyFile = new File(Properties.SDCARD_PATH + scriptName);
        Log.d(TAG, "monkeyFile = " + monkeyFile.exists());
        if (!monkeyFile.exists()) {
            Toast.makeText(this,
                    getResources().getString(R.string.has_no_monkey_file),
                    Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                runMonkeyScript(scriptName);
            }
        }).start();
    }

    private void showDialog(int id) {
        Log.i(TAG, "showDialog id = " + id);
        Intent i = new Intent();
        i.setClass(this.getApplicationContext(), UIDialog.class);
        i.putExtra(UIDialog.ACTION_ID, id);
        if (id == UIDialog.DIALOG_CONFIRM) {
            i.putExtra(UIDialog.CURRENT_APP, mCurrentApp);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityCatchException(i);
        //this.getApplicationContext().startActivity(i);
    }

    private void initPhone() {
        if (mITelephony != null) {
            return;
        }
        Method method;
        try {

            mITelephony = ITelephony.Stub.asInterface(ServiceManager
                    .getService(Context.TELEPHONY_SERVICE));
            Log.d(TAG, "mITelephony valueL: " + mITelephony);
            Log.d(TAG, "mITelephony creat successfully");

        } catch (Exception e) {
            Log.e(TAG, "can not get ITelephony instance.");
            e.printStackTrace();
            mITelephony = null;
            Log.d(TAG, "mITelephony creat fail");
        }
    }

    private static final Class[] mStartActivity = { WifiTestActivity.class,
            BluetoothTestActivity.class, PingActivity.class,
            TestResultActivity.class };

    private void startTestActivity(int index) {
        Log.d(TAG, "startWifiTestActivity, index = " + index);
        Intent i = new Intent();
        i.setClass(this.getApplicationContext(), mStartActivity[index]);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityCatchException(i);
    }

    private boolean hasSimCard() {
        Log.d(TAG, "hasSimCard founction, phone count = "
                + TelephonyManager.from(this).getPhoneCount());
        for (int i = 0; i < TelephonyManager.from(this).getPhoneCount(); i++) {
            if (TelephonyManager.from(this).getSimState(i) == TelephonyManager.SIM_STATE_READY) {
                // if (TelephonyManager.from(this).hasIccCard(i)) {
                mSimindex = i;
                return true;
            }
        }
        Toast.makeText(this, getResources().getString(R.string.has_no_simcard),
                Toast.LENGTH_LONG).show();
        return false;
    }

    private void resetModem(boolean lte) {
        Log.d(TAG, "resetModem, lte = " + lte);
        RadioCapbility mCurrentCapbility = getRadioCapbility();
        Log.d(TAG, "mCurrentCapbility = " + mCurrentCapbility
                + " mSetRadioFeature=" + mSetRadioFeature);
        SystemProperties.set("persist.vendor.radio.engtest.enable", "true");
        if (mCurrentCapbility == RadioCapbility.TDD_SVLTE) {
            if (setPreferredNetworkType(mSetRadioFeature)) {
                ValidationsendATCmd();
            }
        } else {

            new Thread(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    Log.e("current thread：", ""
                            + Thread.currentThread().getName());
                    setPreferredNetworkType(mSetRadioFeature);
                }

            }).start();

        }
    }

    private void ValidationsendATCmd() {
        serverName = getServerName(mSimindex);
        new Thread(new Runnable() {
            @Override
            public void run() {

                iATUtils.sendATCmd(RE_SET_MODE_AT, serverName);
            }
        }).start();
    }

    private String getServerName(int index) {
        Log.d(TAG, "getServerName, index = " + index);
        if (1 == TelephonyManager.from(this).getPhoneCount()) {
            return SERVER_NAME;
        } else {
            return SERVER_NAME + index;
        }
    }

    private boolean isCmcc() {

        String modeTypestr = SystemProperties.get("ro.vendor.radio.modemtype");
        Log.d(TAG, "modeTypestr = " + modeTypestr);
        // "w" WCDMA;
        // "t" TD;
        // "tl" 3MODE;
        // "lf" 4MODE;
        // "l" 5MODE;

        Log.d(TAG, "get modeTypestr1 = " + modeTypestr);
        String modeType = modeTypestr.trim();
        if (modeType.contains("w") || modeType.contains("lf")) {
            return false;
        } else {
            return true;
        }
    }

    private String getOperator() {
        // mNetworkOperatorName = TeleUtils.updateOperator(tmpMccMnc.trim(),
        // "numeric_to_operator");
        mNetworkOperatorName = mTelephonyManager.getNetworkOperatorName();
        Log.d(TAG, "mNetworkOperatorName: " + mNetworkOperatorName);
        return mNetworkOperatorName;
    }

    private boolean isSupportLte() {
        // String modeTypestr = SystemProperties.get("ro.radio.modemtype");
        String modeTypestr = SystemProperties.get("ro.vendor.radio.modemtype");
        Log.d(TAG, "modeTypestr = " + modeTypestr);
        // "w" WCDMA;
        // "t" TD;
        // "tl" 3MODE;
        // "lf" 4MODE;
        // "l" 5MODE;

        Log.d("TAG", "get modeTypestr2 = " + modeTypestr);
        String modeType = modeTypestr.trim();
        if (modeType.trim().contains("tl") || modeType.trim().contains("lf")
                || modeType.trim().contains("l")) {
            Log.d(TAG, "get modeTypexxxxx = " + modeType);
            return true;
        } else {
            Log.d(TAG, "get modeTypexxxxx111111 = " + modeType);
            return false;
        }
    }

    private void stopReceiver() {
        mStopReceiver = true;
    }

    private void myRegisterReceiver() {
        Log.d(TAG, " myRegisterReceiver");
        mVolumeReceiver = new MyVolumeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(mVolumeReceiver, filter);
    }

    private static class MyVolumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // if the volume change,this receciver get intent
            if (intent.getAction()
                    .equals("android.media.VOLUME_CHANGED_ACTION")) {
                Log.d(TAG, "MyVolumeReceiver get onReceive");
                Intent it = new Intent(context, ValidateService.class);
                it.putExtra(ID, LAUNCHER_ON_TOP);
                Log.d(TAG, "ID: " + ID);
                context.startService(it);
            }
        }
    }

    /* androidn porting new interface begein */
    /**
     * Get the preferred network type. Used for device configuration by some
     * CDMA operators.
     * 
     * @param slotIdx
     *            the id of the subscription to get the preferred network type
     *            for
     * @return the preferred network type, defined in RILConstants.java
     */
    public int getPreferredNetworkType(int slotIdx) {
        if (!SubscriptionManager.isValidPhoneId(slotIdx)) {
            Log.d(TAG, "the slotIdx is not Valid");
            return -1;
        }
        int[] subId = SubscriptionManager.getSubId(slotIdx);
        int networkType = mTelephonyManager.getPreferredNetworkType(subId[0]);
        Log.d(TAG, "the NetworkType of the slot[" + slotIdx + "]: "
                + networkType);
        return networkType;
    }

    /**
     * Get the primary card of network type.
     * 
     * @return the network type of the primary card
     */
    public int getPreferredNetworkType() {
        return getPreferredNetworkType(getPrimaryCard());
    }

    public int getPrimaryCard() {
        mSubscriptionManager = SubscriptionManager.from(this
                .getApplicationContext());
        int phoneId = mSubscriptionManager.getDefaultDataPhoneId();
        Log.d(TAG, "getPrimaryCard: " + phoneId);
        return phoneId;
    }

    /**
     * Set the preferred network type.
     * 
     * @param slotIdx
     *            the id of the subscription to set the preferred network type
     *            for
     * @param networkType
     *            the preferred network type.
     * @return true on success; false on any failure.
     */
    private boolean setPreferredNetworkType(int slotIdx, int networkType) {
        if (!SubscriptionManager.isValidPhoneId(slotIdx)) {
            Log.d(TAG, "the slotIdx is not Valid");
            return false;
        }
        // int[] subId = SubscriptionManager.getSubId(slotIdx);
        Log.d(TAG, "slotIdx: " + slotIdx);
        Log.d(TAG, "networkType: " + networkType);
        mRadioInteractor.setPreferredNetworkType(slotIdx, networkType);
        return true;
    }

    /**
     * Set the network type of the primary card.
     * 
     * @return true on success; false on any failure.
     */
    public boolean setPreferredNetworkType(int networkType) {
        return setPreferredNetworkType(getPrimaryCard(), networkType);
    }

    public static RadioCapbility getRadioCapbility() {
        String ssdaMode = SystemProperties.get(PROP_SSDA_MODE);
        Log.d(TAG, "getRadioCapbility: ssdaMode=" + ssdaMode);
        if (ssdaMode.contains(MODE_TDD_CSFB)) {
            return RadioCapbility.TDD_CSFB;
        } else if (ssdaMode.contains(MODE_FDD_CSFB)) {
            return RadioCapbility.FDD_CSFB;
        } else if (ssdaMode.contains(MODE_CSFB)) {
            return RadioCapbility.CSFB;
        } else if (ssdaMode.contains(MODE_LW)) {
            return RadioCapbility.CSFB;
        }
        return RadioCapbility.NONE;
    }

    /* androidn porting new interface end */

}
