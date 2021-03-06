package com.coinblesk.payments.communications.peers.handlers;

import android.util.Log;

import com.coinblesk.payments.WalletService;
import com.coinblesk.payments.communications.peers.PaymentRequestDelegate;
import com.coinblesk.payments.communications.steps.PaymentAuthorizationReceiveStep;
import com.coinblesk.payments.communications.steps.PaymentFinalSignatureOutpointsReceiveStep;
import com.coinblesk.payments.communications.steps.PaymentRefundReceiveStep;
import com.coinblesk.payments.communications.steps.PaymentRequestSendStep;

import org.bitcoinj.uri.BitcoinURI;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 04/03/16.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class InstantPaymentServerHandler extends DERObjectStreamHandler {
    private final static String TAG = InstantPaymentServerHandler.class.getSimpleName();

    private final BitcoinURI paymentUri;
    private final PaymentRequestDelegate paymentRequestDelegate;
    private final WalletService.WalletServiceBinder walletServiceBinder;

    public InstantPaymentServerHandler(InputStream inputStream, OutputStream outputStream, BitcoinURI paymentUri, PaymentRequestDelegate paymentRequestDelegate, WalletService.WalletServiceBinder walletServiceBinder) {
        super(inputStream, outputStream);
        this.paymentUri = paymentUri;
        this.paymentRequestDelegate = paymentRequestDelegate;
        this.walletServiceBinder = walletServiceBinder;
    }


    @Override
    public void run() {
        try {
            long startTime = System.currentTimeMillis();
            final PaymentRequestSendStep paymentRequestSendStep = new PaymentRequestSendStep(this.paymentUri);
            writeDERObject(paymentRequestSendStep.process(readDERObject()));

            final PaymentAuthorizationReceiveStep paymentAuthorizationReceiveStep = new PaymentAuthorizationReceiveStep(this.paymentUri);
            writeDERObject(paymentAuthorizationReceiveStep.process(readDERObject()));

            final PaymentRefundReceiveStep paymentRefundReceiveStep = new PaymentRefundReceiveStep(paymentAuthorizationReceiveStep.getClientPublicKey());
            writeDERObject(paymentRefundReceiveStep.process(readDERObject()));

            final PaymentFinalSignatureOutpointsReceiveStep paymentFinalSignatureOutpointsReceiveStep = new PaymentFinalSignatureOutpointsReceiveStep(paymentAuthorizationReceiveStep.getClientPublicKey(), paymentAuthorizationReceiveStep.getServerSignatures(), this.paymentUri);
            paymentFinalSignatureOutpointsReceiveStep.process(readDERObject());

            walletServiceBinder.commitAndBroadcastTransaction(paymentFinalSignatureOutpointsReceiveStep.getFullSignedTransaction());
            paymentRequestDelegate.onPaymentSuccess();
            Log.d(TAG, "payment was successful in " + (System.currentTimeMillis() - startTime) + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Payment failed due to exception: ", e);
            paymentRequestDelegate.onPaymentError(e.getMessage());
        }
    }


}
