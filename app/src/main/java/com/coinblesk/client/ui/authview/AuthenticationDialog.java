package com.coinblesk.client.ui.authview;


import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.coinblesk.client.R;
import com.coinblesk.client.utils.UIUtils;
import com.coinblesk.client.utils.ClientUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;

/**
 * Created by Andreas Albrecht on 13.04.16.
 */
public class AuthenticationDialog extends DialogFragment {

    private static final String TAG = AuthenticationDialog.class.getName();

    private static final String ARG_ADDRESS = "ADDRESS";
    private static final String ARG_AMOUNT = "AMOUNT";
    private static final String ARG_PAYMENT_REQUEST = "ARG_PAYMENT_REQUEST";
    private static final String ARG_SHOW_ACCEPT = "ARG_SHOW_ACCEPT";

    private AuthenticationDialogListener listener;

    public static AuthenticationDialog newInstance(BitcoinURI paymentRequest, boolean showAccept) {
        return newInstance(paymentRequest.getAddress(), paymentRequest.getAmount(),
                ClientUtils.bitcoinUriToString(paymentRequest), showAccept);
    }

    public static AuthenticationDialog newInstance(Address address, Coin amount,
                                                   String paymentRequestStr, boolean showAccept) {
        AuthenticationDialog frag = new AuthenticationDialog();
        Bundle args = new Bundle();
        args.putString(ARG_ADDRESS, address.toString());
        args.putLong(ARG_AMOUNT, amount.getValue());
        args.putString(ARG_PAYMENT_REQUEST, paymentRequestStr);
        args.putBoolean(ARG_SHOW_ACCEPT, showAccept);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getActivity().setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (listener != null) {
            listener.authViewDestroy();
        }
    }

     @Override
    public void onAttach(Context context) {
        super.onAttach(context);
         if (context instanceof AuthenticationDialogListener) {
             listener = (AuthenticationDialogListener) context;
         } else {
             Log.e(TAG, "onAttach - context does not implement AuthenticationDialogListener");
         }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String address = getArguments().getString(ARG_ADDRESS);
        final long amount = getArguments().getLong(ARG_AMOUNT);
        final String paymentReq = getArguments().getString(ARG_PAYMENT_REQUEST);
        final boolean showAccept = getArguments().getBoolean(ARG_SHOW_ACCEPT);
        final View authView = getActivity().getLayoutInflater().inflate(R.layout.fragment_authview_dialog, null);
        final TextView amountTextView = (TextView) authView.findViewById(R.id.authview_amount_content);
        amountTextView.setText(UIUtils.scaleCoinForDialogs(Coin.valueOf(amount), getContext()));
        final TextView addressTextView = (TextView) authView.findViewById(R.id.authview_address_content);
        addressTextView.setText(address);

        final LinearLayout authviewContainer = (LinearLayout) authView.findViewById(R.id.authview_container);
        authviewContainer.addView(new AuthenticationView(getContext(), paymentReq.getBytes()));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogAccent);
        builder
            .setTitle(R.string.authview_title)
            .setView(authView)
            .setCancelable(true)
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener != null) {
                        listener.authViewNegativeResponse();
                    }
                }
            });
            if (showAccept) {
                builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.authViewPositiveResponse();
                        }
                    }
                });
            }

        return builder.create();
    }

    public interface AuthenticationDialogListener {
        void authViewNegativeResponse();
        void authViewPositiveResponse();
        void authViewDestroy();
    }
}
