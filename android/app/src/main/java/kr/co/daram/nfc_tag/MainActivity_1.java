//package kr.co.daram.nfc_tag;
//
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Color;
//import android.nfc.NfcAdapter;
//import android.nfc.Tag;
//import android.nfc.tech.NfcA;
//import android.util.Log;
//import androidx.appcompat.app.AlertDialog;
//import io.flutter.embedding.android.FlutterActivity;
//import io.flutter.embedding.engine.FlutterEngine;
//import io.flutter.plugin.common.MethodChannel;
//import java.io.ByteArrayInputStream;
//import java.util.Arrays;
//
//public class MainActivity extends FlutterActivity {
//    private static final String CHANNEL = "kr.co.daram.nfc_tag";
//    private NfcAdapter nfcAdapter;
//
//    @Override
//    public void configureFlutterEngine(FlutterEngine flutterEngine) {
//        super.configureFlutterEngine(flutterEngine);
//        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
//
//        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
//                .setMethodCallHandler((call, result) -> {
//                    if ("startNFCProcess".equals(call.method)) {
//                        byte[] imageData = call.argument("imageData");
//                        int displaySize = call.argument("displaySize");
//
//                        if (imageData == null) {
//                            Log.e("NFC", "Image data is null");
//                            result.error("INVALID_ARGUMENT", "Image data is null", null);
//                            return;
//                        }
//
//                        if (displaySize < 0 || displaySize > 9) {
//                            Log.e("NFC", "Invalid display size");
//                            result.error("INVALID_ARGUMENT", "Invalid display size", null);
//                            return;
//                        }
//
//                        if (nfcAdapter != null && !nfcAdapter.isEnabled()) {
//                            showNFCSettingsDialog();
//                            result.error("NFC_DISABLED", "NFC is disabled", null);
//                            return;
//                        }
//
//                        Log.d("NFC", "NFC Reader Mode Enabled");
//                        nfcAdapter.enableReaderMode(this, tag -> handleTag(tag, imageData, displaySize, result),
//                                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
//                    } else {
//                        result.notImplemented();
//                    }
//                });
//    }
//
//    private void handleTag(Tag tag, byte[] imageData, int displaySize, MethodChannel.Result result) {
//        if (tag == null) {
//            Log.e("NFC", "No tag detected");
//            result.error("NFC_ERROR", "No NFC tag detected", null);
//            return;
//        }
//
//        Log.d("NFC", "Tag detected: " + tag.toString());
//        Log.d("NFC", "Available technologies: " + Arrays.toString(tag.getTechList()));
//
//        NfcA nfcA = null;
//        try {
//            waveshare.feng.nfctag.activity.a nfcLibrary = new waveshare.feng.nfctag.activity.a();
//            nfcA = NfcA.get(tag);
//
//            if (nfcA == null) {
//                Log.e("NFC", "NfcA object is null. Tag may not support NfcA.");
//                result.error("NFC_ERROR", "Tag does not support NfcA", null);
//                return;
//            }
//
//            closeOtherTechnologies(tag);
//
//            Log.d("NFC", "Initializing and connecting to NFC tag using nfcLibrary...");
//            int initResponse = nfcLibrary.a(nfcA);
//
//            if (initResponse != 1) {
//                Log.e("NFC", "NFC initialization failed with code: " + initResponse);
//                result.error("NFC_ERROR", "NFC initialization failed", null);
//                return;
//            }
//
//            Log.d("NFC", "NFC initialized and connected successfully using nfcLibrary.");
//
//            try {
//                Thread.sleep(2000); // 1초 딜레이 추가
//            } catch (InterruptedException e) {
//                Log.e("NFC", "Thread sleep interrupted", e);
//            }
//
//            Bitmap bitmap = convertToEInkCompatible(BitmapFactory.decodeStream(new ByteArrayInputStream(imageData)));
//            if (bitmap == null) {
//                Log.e("NFC", "Failed to decode or convert image data");
//                result.error("BITMAP_ERROR", "Failed to decode or convert image", null);
//                return;
//            }
//
//            Log.d("NFC", "Starting data transmission...");
//            int sendResponse = nfcLibrary.a(displaySize, bitmap);
//
//            if (sendResponse == 1) {
//                Log.d("NFC", "Data transmission completed successfully.");
//                monitorProgress(nfcLibrary, tag, result); // `tag` 전달
//            } else {
//                Log.e("NFC", "Data transmission failed with response: " + sendResponse);
//                result.error("TRANSMISSION_ERROR", "Data transmission failed", null);
//                closeOtherTechnologies(tag); // 실패 시 기술 닫기
//                disableNFCReaderMode();
//            }
//        } catch (Exception e) {
//            Log.e("NFC", "Error during NFC operation", e);
//            result.error("NFC_ERROR", "Error: " + e.getMessage(), null);
//        } finally {
//            try {
//                if (nfcA != null && nfcA.isConnected()) {
//                    nfcA.close();
//                    Log.d("NFC", "NFC tag connection closed");
//                }
//            } catch (Exception e) {
//                Log.e("NFC", "Failed to close NFC tag connection", e);
//            }
//        }
//    }
//
//    private void closeOtherTechnologies(Tag tag) {
//        String[] techList = tag.getTechList();
//        for (String tech : techList) {
//            try {
//                switch (tech) {
//                    case "android.nfc.tech.Ndef":
//                        android.nfc.tech.Ndef.get(tag).close();
//                        break;
//                    case "android.nfc.tech.NdefFormatable":
//                        android.nfc.tech.NdefFormatable.get(tag).close();
//                        break;
//                    case "android.nfc.tech.MifareClassic":
//                        android.nfc.tech.MifareClassic.get(tag).close();
//                        break;
//                    case "android.nfc.tech.MifareUltralight":
//                        android.nfc.tech.MifareUltralight.get(tag).close();
//                        break;
//                    case "android.nfc.tech.NfcA":
//                        android.nfc.tech.NfcA.get(tag).close();
//                        break;
//                }
//            } catch (Exception e) {
//                Log.e("NFC", "Failed to close technology: " + tech, e);
//            }
//        }
//    }
//
//    private void showNFCSettingsDialog() {
//        new AlertDialog.Builder(this)
//                .setTitle("NFC Disabled")
//                .setMessage("NFC is disabled. Please enable NFC in settings to continue.")
//                .setPositiveButton("Open Settings", (DialogInterface dialog, int which) -> {
//                    Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
//                    startActivity(intent);
//                })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }
//
//    private void monitorProgress(waveshare.feng.nfctag.activity.a nfcLibrary, Tag tag, MethodChannel.Result result) {
//        new Thread(() -> {
//            try {
//                boolean isCompleted = false;
//
//                while (!isCompleted) {
//                    int progress = nfcLibrary.a();
//                    Log.d("NFC", "Progress: " + progress);
//
//                    if (progress == 100) {
//                        Log.d("NFC", "Data transmission completed.");
//                        result.success(true);
//                        isCompleted = true;
//                        closeOtherTechnologies(tag); // 완료 후 기술 닫기
//                        disableNFCReaderMode();
//                    } else if (progress < 0) {
//                        Log.e("NFC", "Error occurred during transmission monitoring.");
//                        result.error("TRANSMISSION_ERROR", "Error occurred while monitoring progress", null);
//                        isCompleted = true;
//                    }
//
//                    Thread.sleep(500);
//                }
//            } catch (Exception e) {
//                Log.e("NFC", "Error during progress monitoring", e);
//                result.error("MONITORING_ERROR", "Error during progress monitoring: " + e.getMessage(), null);
//            } finally {
//                disableNFCReaderMode();
//            }
//        }).start();
//    }
//
//    private void disableNFCReaderMode() {
//        if (nfcAdapter != null) {
//            nfcAdapter.disableReaderMode(this);
//            Log.d("NFC", "NFC Reader Mode Disabled");
//        }
//    }
//
//    private Bitmap convertToEInkCompatible(Bitmap original) {
//        Bitmap converted = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
//        for (int y = 0; y < original.getHeight(); y++) {
//            for (int x = 0; x < original.getWidth(); x++) {
//                int color = original.getPixel(x, y);
//                int gray = (int) (0.299 * ((color >> 16) & 0xff) + 0.587 * ((color >> 8) & 0xff) + 0.114 * (color & 0xff));
//                converted.setPixel(x, y, gray > 128 ? Color.WHITE : Color.BLACK);
//            }
//        }
//        return converted;
//    }
//}
