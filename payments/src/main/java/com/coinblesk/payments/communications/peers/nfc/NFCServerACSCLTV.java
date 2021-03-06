/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.payments.communications.peers.nfc;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.acs.smartcard.Reader;
import com.acs.smartcard.ReaderException;
import com.coinblesk.client.utils.ClientUtils;
import com.coinblesk.payments.WalletService;
import com.coinblesk.der.DERObject;
import com.coinblesk.der.DERParser;
import com.coinblesk.payments.communications.peers.AbstractServer;
import com.coinblesk.payments.communications.steps.cltv.PaymentRequestSendStep;
import com.coinblesk.payments.communications.steps.cltv.PaymentResponseReceiveCompactStep;
import com.coinblesk.payments.communications.steps.cltv.PaymentResponseReceiveStep;
import com.coinblesk.util.Pair;

import static com.coinblesk.payments.communications.peers.nfc.NFCUtils.KEEPALIVE;
import static com.coinblesk.payments.communications.peers.nfc.NFCUtils.AID_ANDROID_ACS;
import static com.coinblesk.payments.communications.peers.nfc.NFCUtils.CLA_INS_P1_P2;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author Andreas Albrecht
 * @author Alessandro De Carli
 */

@TargetApi(Build.VERSION_CODES.KITKAT)
public class NFCServerACSCLTV extends AbstractServer {
    private final static String TAG = NFCServerACSCLTV.class.getName();
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static final int ACS_MAX_FRAGMENT_SIZE = 53;

    private static final int ACS_VENDOR_ID = 1839;
    private static final int ACS_DEVICE_ID_ACR1251U = 8730;
    private static final int ACS_DEVICE_ID_ACR122U = 8704;

    private Reader reader;
    private BroadcastReceiver broadcastReceiver;

    public NFCServerACSCLTV(Context context, WalletService.WalletServiceBinder walletServiceBinder) {
        super(context, walletServiceBinder);

        if (isSupported()) {
            initReader();
        }
    }

    private void initReader() {
        UsbManager manager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        Reader reader = new Reader(manager);
        UsbDevice externalDevice = externalReaderAttached(manager, reader);

        Intent usbIntent = new Intent(ACTION_USB_PERMISSION);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(getContext(), 0, usbIntent, 0);
        manager.requestPermission(externalDevice, permissionIntent);
    }

    @Override
    public boolean isSupported() {
        return hasClass("com.acs.smartcard.Reader") && isExternalReaderAttached(getContext());
    }

    @Override
    public void onStart() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        broadcastReceiver = new USBBroadcastReceiver();
        getContext().registerReceiver(broadcastReceiver, filter);

        try {
            Pair<ACSTransceiver, Reader> pair = createReaderAndTransceiver(getContext());
            reader = pair.element1();
            ACSTransceiver transceiver = pair.element0();
            NFCServerACSCallback callback = new NFCServerACSCallbackImpl();
            reader.setOnStateChangeListener(new ReaderStateChangeListener(transceiver, callback));
        } catch (IOException e) {
            Log.e(TAG, "Exception ", e);
        }
    }

    @Override
    public void onStop() {
        setPaymentRequestUri(null);
        // TODO: close reader? unregister receiver?
/*        try {
            if (!this.isRunning()) {
                Log.d(TAG, "Already turned off ACS");
            }
            Log.d(TAG, "Turn off ACS");
            if (reader != null && reader.isOpened()) {
                reader.close();
                reader = null;
                Log.d(TAG, "Reader closed");
            }
            getContext().unregisterReceiver(broadcastReceiver);
            this.setRunning(false);
        } catch (Exception e) {
        }*/
    }

    public DERObject transceiveDER(ACSTransceiver acsTransceiver, DERObject input,
                                   boolean needsSelectAidApdu)
            throws Exception {
        byte[] derPayload = input.serializeToDER();
        byte[] derResponse = new byte[0];
        int fragmentByte = 0;

        Log.d(TAG, "transceiveDER - start transceive, needsSelectAidApdu=" + needsSelectAidApdu +
                ", payload=" + derPayload.length + " bytes");
        while (fragmentByte < derPayload.length) {
            byte[] fragment = new byte[0];
            if (needsSelectAidApdu) {
                acsTransceiver.write(createSelectAidApdu(AID_ANDROID_ACS));
                needsSelectAidApdu = false;
            }

            int end = Math.min(derPayload.length, fragmentByte + ACS_MAX_FRAGMENT_SIZE);
            byte[] data = Arrays.copyOfRange(derPayload, fragmentByte, end);
            fragment = ClientUtils.concatBytes(fragment, data);

            Log.d(TAG, "transceiveDER - about to send fragment size: " + fragment.length);
            derResponse = acsTransceiver.write(fragment);
            Log.d(TAG, "transceiveDER - my client received payload: " + Arrays.toString(derResponse));
            fragmentByte += fragment.length;
        }

        while (Arrays.equals(derResponse, KEEPALIVE)) {
            derResponse = acsTransceiver.write(KEEPALIVE);
        }

        int responseLength = DERParser.extractPayloadEndIndex(derResponse);
        Log.d(TAG, "transceiveDER - expected response lenght: " + responseLength);
        Log.d(TAG, "transceiveDER - actual response lenght: " + derResponse.length);

        while (derResponse.length < responseLength) {
            derResponse = ClientUtils.concatBytes(derResponse, acsTransceiver.write(KEEPALIVE));
            Log.d(TAG, "transceiveDER - had to ask for next bytes: " + derResponse.length);
        }
        Log.d(TAG, "transceiveDER - end transceive");
        return DERParser.parseDER(derResponse);
    }

    private DERObject transceiveDER(ACSTransceiver acsTransceiver, DERObject input) throws Exception {
        return transceiveDER(acsTransceiver, input, false);
    }

    private byte[] createSelectAidApdu(byte[] aid) {
        byte[] result = new byte[6 + aid.length];
        System.arraycopy(CLA_INS_P1_P2, 0, result, 0, CLA_INS_P1_P2.length);
        result[4] = (byte) aid.length;
        System.arraycopy(aid, 0, result, 5, aid.length);
        result[result.length - 1] = 0;
        return result;
    }

    private static Pair<ACSTransceiver, Reader> createReaderAndTransceiver(final Context context) throws IOException {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Reader reader = new Reader(manager);
        UsbDevice externalDevice = externalReaderAttached(manager, reader);
        if (externalDevice == null) {
            throw new IOException("External device is not set");
        }

        ACSTransceiver transceiver;
        try {
            transceiver = createAcsTransceiver(reader, externalDevice);
            Log.d(TAG, "ask user for permission");
            reader.open(externalDevice);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "could not access device, no permissions given?", e);
            throw new IOException(e);
        }

        return new Pair<ACSTransceiver, Reader>(transceiver, reader);
    }

    @NonNull
    private static ACSTransceiver createAcsTransceiver(Reader reader, UsbDevice externalDevice) throws IOException {
        final int pid = externalDevice.getProductId();
        final int vid = externalDevice.getVendorId();

        Log.d(TAG, "pid=" + pid + ", vid=" + vid);

        final int maxLen;
        final boolean isAcr122u;
        if (pid == ACS_DEVICE_ID_ACR122U && vid == ACS_VENDOR_ID) {
            /*
             * 64 is the maximum due to a sequence bug in the ACR122u
			 * http://musclecard.996296
			 * .n3.nabble.com/ACR122U-response-frames-contain-wrong
			 * -sequence-numbers-td5002.html If larger than 64, then I get a
			 * com.acs.smartcard.CommunicationErrorException: The sequence number (4) is
			 * invalid.
			 *
			 * The same problem arises sometimes even with the length of 54.
			 */
            maxLen = ACS_MAX_FRAGMENT_SIZE;
            isAcr122u = true;
        } else if (pid == ACS_DEVICE_ID_ACR1251U && vid == ACS_VENDOR_ID) {
            /**
             * The ACR1251U can handle larger message,
             * it may be possible to go for the same amount as the android devices (245).
             * use same as above for the moment.
             */
            maxLen = ACS_MAX_FRAGMENT_SIZE;
            isAcr122u = false;
        } else {
            throw new IOException("unknow device with pid:vid " + pid + ":" + vid);
        }

        return new ACSTransceiver(reader, maxLen, isAcr122u);
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isExternalReaderAttached(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Reader reader = new Reader(manager);
        return externalReaderAttached(manager, reader) != null;
    }

    private static UsbDevice externalReaderAttached(UsbManager manager, Reader reader) {
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (reader.isSupported(device)) {
                return device;
            }
        }
        return null;
    }

    private static class ReaderStateChangeListener implements Reader.OnStateChangeListener {
        private final ACSTransceiver transceiver;
        private final NFCServerACSCallback callback;

        public ReaderStateChangeListener(ACSTransceiver transceiver, NFCServerACSCallback callback) {
            this.transceiver = transceiver;
            this.callback = callback;
        }

        public void onStateChange(int slotNum, int prevState, int currState) {
            Log.d(TAG, "statechange from: " + prevState + " to: " + currState);

            if (currState == Reader.CARD_PRESENT) {
                try {
                    transceiver.initCard(slotNum);
                        /*if(!disabledBuzzer) {
                            transceiver.disableBuzzer();
                            disabledBuzzer = true;
                        }*/
                    callback.tagDiscovered(transceiver);
                } catch (ReaderException e) {
                    Log.e(TAG, "Could not connnect reader (ReaderException): ", e);
                    callback.tagFailed();
                }
            } else if (currState == Reader.CARD_ABSENT) {
                callback.tagLost();
            }
        }
    }

    private class NFCServerACSCallbackImpl implements NFCServerACSCallback {
        private static final String TAG = "NFCServerACSCallback";
        @Override
        public void tagDiscovered(ACSTransceiver transceiver) {
            try {
                if (getPaymentRequestUri() == null) {
                    return;
                }
                PaymentRequestSendStep paymentRequestSend = new PaymentRequestSendStep(getPaymentRequestUri());
                DERObject paymentRequest = paymentRequestSend.process(DERObject.NULLOBJECT);
                DERObject paymentResponse = transceiveDER(transceiver, paymentRequest, true);

                PaymentResponseReceiveStep receiveResponse = new PaymentResponseReceiveCompactStep(
                        getPaymentRequestUri(), getWalletServiceBinder());
                DERObject serverSignatures = receiveResponse.process(paymentResponse);
                transceiveDER(transceiver, serverSignatures);

                getPaymentRequestDelegate().onPaymentSuccess();
                transceiver.write(DERObject.NULLOBJECT.serializeToDER());
            } catch (Exception e) {
                Log.e(TAG, "tagDiscovered - Exception: ", e);
            }
        }

        @Override
        public void tagFailed() {
            Log.d(TAG, "tagFailed");
        }

        @Override
        public void tagLost() {
            Log.d(TAG, "tagLost");
        }
    }

    private class USBBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive - got action: " + action);

            switch (action) {
                case ACTION_USB_PERMISSION:
                    handleUsbPermission(intent);
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    handleDeviceDetached(intent);
                    break;
                default:
                    Log.w(TAG, "onReceive - do not known how to handle action: " + action);
            }
        }

        private void handleUsbPermission(Intent intent) {
            Log.d(TAG, "handleUsbPermission - try to create reader");
            synchronized (this) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (!granted) {
                    Log.w(TAG, "USB Permissions not granted. Cannot create reader.");
                    return;
                }

                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device == null) {
                    Log.w(TAG, "USB device is null. Cannot create reader.");
                    return;
                }

                try {
                    reader.open(device);
                    Log.d(TAG, "Reader opened");
                } catch (Exception e) {
                    Log.e(TAG, "Could not open reader: ", e);
                }
            }
        }

        private void handleDeviceDetached(Intent intent) {
            synchronized (this) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && reader != null && device.equals(reader.getDevice())) {
                    reader.close();
                    Log.d(TAG, "handleDeviceDetached - reader detached");
                }
            }
        }
    }
}