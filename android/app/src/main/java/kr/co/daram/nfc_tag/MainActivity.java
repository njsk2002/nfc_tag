package kr.co.daram.nfc_tag;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "kr.co.daram.nfc_tag";
    private NfcAdapter nfcAdapter;

    @Override
    public void configureFlutterEngine(FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if ("startNFCProcess".equals(call.method)) {
                        byte[] imageData = call.argument("imageData");
                        if (imageData == null) {
                            Log.e("NFC", "Image data is null");
                            result.error("INVALID_ARGUMENT", "Image data is null", null);
                            return;
                        }

                        if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
                            showNFCSettingsDialog();
                            result.error("NFC_DISABLED", "NFC is disabled", null);
                            return;
                        }

                        Log.d("NFC", "NFC Reader Mode Enabled");
                        nfcAdapter.enableReaderMode(this, tag -> handleTag(tag, imageData, result),
                                NfcAdapter.FLAG_READER_NFC_A, null);
                    } else {
                        result.notImplemented();
                    }
                });
    }

    private void handleTag(Tag tag, byte[] imageData, MethodChannel.Result result) {
        boolean hasReplied = false; // 중복 호출 방지 플래그

        if (tag == null) {
            Log.e("NFC", "No tag detected");
            result.error("NFC_ERROR", "No NFC tag detected", null);
            return;
        }

        Log.d("NFC", "Tag detected: " + tag.toString());
        Log.d("NFC", "Available technologies: " + Arrays.toString(tag.getTechList()));

        try {
            waveshare.feng.nfctag.activity.a nfcLibrary = new waveshare.feng.nfctag.activity.a();
            NfcA nfcA = NfcA.get(tag);

            // 모든 활성 기술 닫기
            closeOtherTechnologies(tag);

            // NFC 연결 시도
            try {
                Log.d("NFC", "Attempting to connect to NFC tag...");
                nfcA.connect();
                Log.d("NFC", "NFC tag connected successfully");
            } catch (IOException e) {
                Log.e("NFC", "Failed to connect to NFC tag", e);
                if (!hasReplied) {
                    result.error("NFC_ERROR", "Failed to connect to NFC tag: " + e.getMessage(), null);
                    hasReplied = true;
                }
                return;
            }

            // NFC 초기화
            int initResponse = nfcLibrary.a(nfcA);
            if (initResponse != 1) {
                Log.e("NFC", "NFC initialization failed with code: " + initResponse);
                if (!hasReplied) {
                    result.error("NFC_ERROR", "NFC initialization failed", null);
                    hasReplied = true;
                }
                return;
            }
            Log.d("NFC", "NFC initialized successfully");

            // 이미지 데이터 전송
            Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(imageData));
            if (bitmap == null) {
                Log.e("NFC", "Failed to decode image data");
                if (!hasReplied) {
                    result.error("BITMAP_ERROR", "Failed to decode image", null);
                    hasReplied = true;
                }
                return;
            }

            Log.d("NFC", "Sending image data to NFC tag...");
            int sendResponse = nfcLibrary.a(2, bitmap);
            if (sendResponse == 1) {
                Log.d("NFC", "Image data sent successfully");
                if (!hasReplied) {
                    result.success(true);
                    hasReplied = true;
                }
            } else {
                Log.e("NFC", "Failed to send image data, response code: " + sendResponse);
                if (!hasReplied) {
                    result.error("NFC_ERROR", "Failed to send image data, response code: " + sendResponse, null);
                    hasReplied = true;
                }
            }
        } catch (Exception e) {
            Log.e("NFC", "Error during NFC operation", e);
            if (!hasReplied) {
                result.error("NFC_ERROR", "Error: " + e.getMessage(), null);
                hasReplied = true;
            }
        } finally {
            // NFC 태그 연결 해제
            try {
                NfcA nfcA = NfcA.get(tag);
                if (nfcA != null && nfcA.isConnected()) {
                    nfcA.close();
                    Log.d("NFC", "NFC tag connection closed");
                }
            } catch (IOException e) {
                Log.e("NFC", "Failed to close NFC tag connection", e);
            }
        }
    }

    private void closeOtherTechnologies(Tag tag) {
        String[] techList = tag.getTechList();
        for (String tech : techList) {
            try {
                switch (tech) {
                    case "android.nfc.tech.Ndef":
                        android.nfc.tech.Ndef.get(tag).close();
                        break;
                    case "android.nfc.tech.NdefFormatable":
                        android.nfc.tech.NdefFormatable.get(tag).close();
                        break;
                    case "android.nfc.tech.MifareClassic":
                        android.nfc.tech.MifareClassic.get(tag).close();
                        break;
                    case "android.nfc.tech.MifareUltralight":
                        android.nfc.tech.MifareUltralight.get(tag).close();
                        break;
                    case "android.nfc.tech.NfcA":
                        android.nfc.tech.NfcA.get(tag).close();
                        break;
                }
            } catch (Exception e) {
                Log.e("NFC", "Failed to close technology: " + tech, e);
            }
        }
    }

    private void showNFCSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("NFC Disabled")
                .setMessage("NFC is disabled. Please enable NFC in settings to continue.")
                .setPositiveButton("Open Settings", (DialogInterface dialog, int which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
