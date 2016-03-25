package com.coinblesk.client.ui.dialogs;

//import android.support.v7.app.AlertDialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.coinblesk.client.R;
import com.coinblesk.client.helpers.UIUtils;
import com.coinblesk.payments.Constants;
import com.coinblesk.payments.WalletService;
import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

/**
 * Created by ckiller
 */

public class SendDialogFragment extends DialogFragment implements View.OnClickListener {
    public static final int REQUEST_CODE = 0x0000c0de; // Only use bottom 16 bits

    public static final String AMOUNT_KEY = "AMOUNT_KEY";
    public static final String ADDRESS_KEY = "ADDRESS_KEY";

    private final static String TAG = SendDialogFragment.class.getName();

    private EditText addressEditText;
    private EditText amountEditText;


    public static DialogFragment newInstance(Coin amount) {
        DialogFragment fragment = new SendDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(AMOUNT_KEY, amount.value);
        fragment.setArguments(arguments);
        return fragment;
    }

    public static DialogFragment newInstance(Address address, Coin amount) {
        DialogFragment fragment = new SendDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(AMOUNT_KEY, amount.value);
        arguments.putString(ADDRESS_KEY, address.toString());
        fragment.setArguments(arguments);
        return fragment;
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_send_dialog, container);
        this.addressEditText = (EditText) view.findViewById(R.id.address_edit_text);
        try {
            Address address = new Address(Constants.PARAMS,this.getArguments().getString(ADDRESS_KEY,""));
            this.addressEditText.setText(address.toString());
        } catch (AddressFormatException e) {

        }

        Coin amount = Coin.valueOf(this.getArguments().getLong(AMOUNT_KEY, 0));
        this.amountEditText = (EditText) view.findViewById(R.id.amount_edit_text);
        this.amountEditText.setText(UIUtils.scaleCoinForDialogs(amount, getContext()));

        view.findViewById(R.id.fragment_send_dialog_cancel).setOnClickListener(this);
        view.findViewById(R.id.fragment_send_dialog_qr_scan).setOnClickListener(this);
        view.findViewById(R.id.fragment_send_dialog_send).setOnClickListener(this);

        return view;

    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(R.string.fragment_send_dialog_title);
        return dialog;
    }

    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.fragment_send_dialog_send:
                sendCoins();
                break;
            case R.id.fragment_send_dialog_cancel:
                getDialog().cancel();
                break;
            case R.id.fragment_send_dialog_qr_scan:
                IntentIntegrator.forSupportFragment(SendDialogFragment.this).initiateScan();
                break;
        }


        }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                final String contents = data.getStringExtra(Intents.Scan.RESULT);
                try {
                    BitcoinURI bitcoinURI = new BitcoinURI(contents);
                    this.addressEditText.setText(bitcoinURI.getAddress().toString());
                } catch (BitcoinURIParseException e) {
                    this.addressEditText.setText(contents);
                }
            }
        }
    }


    /* ------------------- PAYMENTS INTEGRATION STARTS HERE  ------------------- */
    private final BroadcastReceiver walletCoinsSentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dismiss();
        }
    };

    private void sendCoins() {
        try {
//            Coin amount = Coin.valueOf(Long.parseLong(this.amountEditText.getText().toString()));
            Coin amount = Coin.valueOf(this.getArguments().getLong(AMOUNT_KEY, 0));
            walletServiceBinder.sendCoins(new Address(Constants.PARAMS, addressEditText.getText().toString()), amount);
        } catch (AddressFormatException e) {
            e.printStackTrace();
        }
    }

    private WalletService.WalletServiceBinder walletServiceBinder;

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this.getActivity(), WalletService.class);
        this.getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.getActivity().unbindService(serviceConnection);
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(walletCoinsSentReceiver);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            walletServiceBinder = (WalletService.WalletServiceBinder) binder;

            IntentFilter filter = new IntentFilter(Constants.WALLET_COINS_SENT_ACTION);
            filter.addAction(Constants.INSTANT_PAYMENT_SUCCESSFUL_ACTION);
            filter.addAction(Constants.INSTANT_PAYMENT_FAILED_ACTION);
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(walletCoinsSentReceiver, filter);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            walletServiceBinder = null;
        }
    };
    /* -------------------- PAYMENTS INTEGRATION ENDS HERE  -------------------- */
}
