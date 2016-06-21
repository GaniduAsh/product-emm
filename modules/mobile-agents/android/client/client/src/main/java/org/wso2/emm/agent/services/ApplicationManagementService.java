/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.emm.agent.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import org.wso2.emm.agent.AndroidAgentException;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.api.ApplicationManager;
import org.wso2.emm.agent.beans.ServerConfig;
import org.wso2.emm.agent.proxy.interfaces.APIResultCallBack;
import org.wso2.emm.agent.utils.CommonUtils;
import org.wso2.emm.agent.utils.Constants;
import org.wso2.emm.agent.utils.Preference;
import java.util.Map;

/**
 * This is the service class which exposes all the application management operations
 * to the App catalog application. App catalog can bind to this service and execute permitted operations by
 * sending necessary parameters.
 */
public class ApplicationManagementService extends IntentService implements APIResultCallBack {

    private static final String TAG = ApplicationManagementService.class.getName();
    private static final String INTENT_KEY_PAYLOAD = "payload";
    private static final String INTENT_KEY_STATUS = "status";
    private static final String INTENT_KEY_SERVER = "server";
    private static final String INTENT_KEY_CODE = "code";
    private static final String INTENT_KEY_APP_URI = "appUri";
    private static final String INTENT_KEY_APP_NAME = "appName";
    private String operationCode = null;
    private String appUri = null;
    private String appName = null;
    private Context context;
    private ServerConfig utils;
    public ApplicationManagementService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        context = this.getApplicationContext();
        utils = new ServerConfig();
        if (extras != null) {
            operationCode = extras.getString(INTENT_KEY_CODE);
            if (extras.containsKey(INTENT_KEY_APP_URI)) {
                appUri = extras.getString(INTENT_KEY_APP_URI);
            }

            if (extras.containsKey(INTENT_KEY_APP_NAME)) {
                appName = extras.getString(INTENT_KEY_APP_NAME);
            }
        }

        Log.d(TAG, "App catalog has sent a command.");
        if ((operationCode != null)) {
            Log.d(TAG, "The operation code is: " + operationCode);

            Log.i(TAG, "Will now executing the command ..." + operationCode);
            boolean isRegistered = Preference.getBoolean(this.getApplicationContext(), Constants.PreferenceFlag.REGISTERED);
            if (isRegistered && Constants.CATALOG_APP_PACKAGE_NAME.equals(intent.getPackage())) {
                doTask(operationCode);
            } else {
                sendBroadcast(Constants.Status.AUTHENTICATION_FAILED, null);
            }
        }
    }

    /**
     * Executes device management operations on the device.
     *
     * @param code - Operation object.
     */
    public void doTask(String code) {
        ApplicationManager applicationManager = new ApplicationManager(context);
        switch (code) {
            case Constants.Operation.GET_APPLICATION_LIST:
                getAppListFromServer();
                break;
            case Constants.Operation.INSTALL_APPLICATION:
                if (appUri != null) {
                    applicationManager.installApp(appUri, null);
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.toast_app_installation_failed),
                                   Toast.LENGTH_LONG).show();
                }
                break;
            case Constants.Operation.UNINSTALL_APPLICATION:
                if (appUri != null) {
                    applicationManager.uninstallApplication(appUri, null);
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.toast_app_removal_failed),
                                   Toast.LENGTH_LONG).show();
                }
                break;
            case Constants.Operation.WEBCLIP:
                if (appUri != null && appName != null) {
                    try {
                        applicationManager.manageWebAppBookmark(appUri, appName, context.getResources().
                                getString(R.string.operation_install));
                    } catch (AndroidAgentException e) {
                        Log.e(TAG, "WebClip creation failed." + e);
                    }
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.toast_app_installation_failed),
                                   Toast.LENGTH_LONG).show();
                }
                break;
            case Constants.Operation.UNINSTALL_WEBCLIP:
                if (appUri != null && appName != null) {
                    try {
                        applicationManager.manageWebAppBookmark(appUri, appName, context.getResources().
                                getString(R.string.operation_uninstall));
                    } catch (AndroidAgentException e) {
                        Log.e(TAG, "WebClip creation failed." + e);
                    }
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.toast_app_installation_failed),
                                   Toast.LENGTH_LONG).show();
                }
                break;
            case Constants.Operation.GET_APP_DOWNLOAD_PROGRESS:
                sendBroadcast(Constants.Status.SUCCESSFUL, Preference.getString(context, context.getResources().
                        getString(R.string.app_download_progress)));
                break;
            default:
                Log.e(TAG, "Invalid operation code received");
                break;
        }
    }

    /**
     * Retriever application list from the server.
     */
    private void getAppListFromServer() {
        Context context = this.getApplicationContext();
        String ipSaved = Preference.getString(context, Constants.PreferenceFlag.IP);

        if (ipSaved != null && !ipSaved.isEmpty()) {
            utils.setServerIP(ipSaved);
            CommonUtils.callSecuredAPI(context, utils.getAPIServerURL(context) + Constants.APP_LIST_ENDPOINT,
                                       org.wso2.emm.agent.proxy.utils.Constants.HTTP_METHODS.GET, null,
                                       ApplicationManagementService.this, Constants.APP_LIST_REQUEST_CODE
            );
        } else {
            Log.e(TAG, "There is no valid IP to contact the server");
        }
    }

    private void sendBroadcast(String status, String payload) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(Constants.ACTION_RESPONSE);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtra(INTENT_KEY_STATUS, status);
        broadcastIntent.putExtra(INTENT_KEY_PAYLOAD, payload);
        broadcastIntent.putExtra(INTENT_KEY_SERVER, utils.getAPIServerURL(context));
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onReceiveAPIResult(Map<String, String> result, int requestCode) {
        String responseStatus;
        String response;
        if (requestCode == Constants.APP_LIST_REQUEST_CODE) {
            if (result != null) {
                responseStatus = result.get(Constants.STATUS_KEY);
                if (Constants.Status.SUCCESSFUL.equals(responseStatus)) {
                    response = result.get(Constants.RESPONSE);
                    if (response != null && !response.isEmpty()) {
                        sendBroadcast(Constants.Status.SUCCESSFUL, result.get(Constants.RESPONSE));
                    } else {
                        sendBroadcast(Constants.Status.INTERNAL_SERVER_ERROR, null);
                    }
                } else {
                    sendBroadcast(Constants.Status.INTERNAL_SERVER_ERROR, null);
                }
            } else {
                sendBroadcast(Constants.Status.INTERNAL_SERVER_ERROR, null);
            }
        }
    }
}
